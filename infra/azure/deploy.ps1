#Requires -Version 7
<#
.SYNOPSIS
    Builds both images, pushes them to the registry and deploys the platform and both services to AKS. Used by the
    workflow .github/workflows/deploy.yml and by hand after `az login`.

.DESCRIPTION
    Reads the outputs of infra/azure/main.bicep, so provision.ps1 must have run first. The secrets come only from
    the environment and are never printed: TRIAGE_API_KEY, ASSISTANT_API_KEY, POSTGRES_PASSWORD, REDIS_PASSWORD and
    ANTHROPIC_API_KEY (optional). kubectl always runs with the kubeconfig of this cluster, in a file of its own: the
    kubeconfig of the user is never read or changed.

.EXAMPLE
    ./infra/azure/deploy.ps1 -ResourceGroup vidacruz-rg -ImageTag v1
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $ResourceGroup,
    [string] $ImageTag = (Get-Date -Format 'yyyyMMddHHmmss'),
    # Deploys images already pushed with this tag
    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$certManagerVersion = 'v1.18.2'
$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '../..')
$namespace = 'vidacruz'
$services = @('triage-service', 'clinical-assistant-service')

function Invoke-Native {
    param([Parameter(Mandatory)] [string] $Command, [Parameter(ValueFromRemainingArguments)] $Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command $($Arguments[0]) failed with exit code $LASTEXITCODE" }
}

function Invoke-Kubectl {
    Invoke-Native kubectl --kubeconfig $script:kubeconfig @args
}

# Applies the YAML given as text (kubectl reads it from its standard input)
function Send-Manifest([string] $Yaml) {
    $Yaml | & kubectl --kubeconfig $script:kubeconfig apply --filename -
    if ($LASTEXITCODE -ne 0) { throw "kubectl apply failed with exit code $LASTEXITCODE" }
}

function Get-RequiredSecret([string] $Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Set $Name in the environment." }
    return $value
}

# Renders a kustomization and replaces the __TOKENS__ with the values of this deployment
function Get-RenderedManifests([string] $Directory, [hashtable] $Tokens) {
    $rendered = (Invoke-Kubectl kustomize $Directory) -join "`n"
    foreach ($token in $Tokens.Keys) {
        $rendered = $rendered.Replace("__${token}__", $Tokens[$token])
    }
    $left = [regex]::Matches($rendered, '__[A-Z_]+__') | ForEach-Object Value | Sort-Object -Unique
    if ($left) { throw "Tokens without a value in ${Directory}: $($left -join ', ')" }
    return $rendered
}

function Set-Secret([string] $Name, [hashtable] $Values) {
    $file = New-TemporaryFile
    try {
        $Values.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" } | Set-Content -Path $file -Encoding utf8NoBOM
        $manifest = (Invoke-Kubectl create secret generic $Name --namespace $namespace `
            --from-env-file=$file --dry-run=client --output yaml) -join "`n"
        Send-Manifest $manifest | Out-Null
    } finally {
        Remove-Item -Path $file -Force
    }
}

$secrets = @{
    TRIAGE_API_KEY    = Get-RequiredSecret 'TRIAGE_API_KEY'
    ASSISTANT_API_KEY = Get-RequiredSecret 'ASSISTANT_API_KEY'
    POSTGRES_PASSWORD = Get-RequiredSecret 'POSTGRES_PASSWORD'
    REDIS_PASSWORD    = Get-RequiredSecret 'REDIS_PASSWORD'
    ANTHROPIC_API_KEY = [string] $env:ANTHROPIC_API_KEY
}

$outputs = (Invoke-Native az deployment group show --resource-group $ResourceGroup --name vidacruz `
    --query properties.outputs --output json) -join "`n" | ConvertFrom-Json
$registry = $outputs.registryLoginServer.value

if (-not $SkipBuild) {
    Invoke-Native az acr login --name $outputs.registryName.value
    foreach ($service in $services) {
        $image = "$registry/${service}:$ImageTag"
        $context = Join-Path $repositoryRoot $service
        Invoke-Native docker build --file (Join-Path $context 'deployment/Dockerfile') --tag $image $context
        Invoke-Native docker push $image
    }
}

$script:kubeconfig = Join-Path ([IO.Path]::GetTempPath()) "vidacruz-$($outputs.clusterName.value).kubeconfig"
Invoke-Native az aks get-credentials --resource-group $ResourceGroup --name $outputs.clusterName.value --admin `
    --file $script:kubeconfig --overwrite-existing

# The Gateway API managed by AKS must be installed before cert-manager starts, or it never watches the Gateways
Invoke-Kubectl wait --for condition=established --timeout=300s crd/gateways.gateway.networking.k8s.io `
    crd/httproutes.gateway.networking.k8s.io
Invoke-Kubectl apply --filename "https://github.com/cert-manager/cert-manager/releases/download/$certManagerVersion/cert-manager.yaml"
$controllerArguments = (Invoke-Kubectl --namespace cert-manager get deployment cert-manager `
    --output 'jsonpath={.spec.template.spec.containers[0].args}') -join ''
if (-not $controllerArguments.Contains('--enable-gateway-api')) {
    Invoke-Kubectl --namespace cert-manager patch deployment cert-manager --type json `
        --patch '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--enable-gateway-api"}]'
}
foreach ($deployment in @('cert-manager', 'cert-manager-webhook', 'cert-manager-cainjector')) {
    Invoke-Kubectl --namespace cert-manager rollout status "deployment/$deployment" --timeout=300s
}

$tokens = @{
    REGISTRY        = $registry
    IMAGE_TAG       = $ImageTag
    DB_HOST         = $outputs.postgresHost.value
    PUBLIC_HOST     = $outputs.publicHost.value
    INGRESS_IP_NAME = $outputs.ingressIpName.value
    RESOURCE_GROUP  = $outputs.resourceGroupName.value
}
# Let's Encrypt issuer, then the Gateway on its public IP
Send-Manifest (Get-RenderedManifests (Join-Path $repositoryRoot 'infra/k8s/cluster') $tokens)
Send-Manifest (Get-RenderedManifests (Join-Path $repositoryRoot 'infra/k8s/edge') $tokens)

# The namespace first, so the secrets exist before the pods that read them
Invoke-Kubectl apply --filename (Join-Path $repositoryRoot 'infra/k8s/namespace.yaml')
Set-Secret 'redis-auth' @{ REDIS_PASSWORD = $secrets.REDIS_PASSWORD }
Set-Secret 'triage-service-secrets' @{ API_KEY = $secrets.TRIAGE_API_KEY; DB_PASSWORD = $secrets.POSTGRES_PASSWORD }
Set-Secret 'clinical-assistant-service-secrets' @{
    ASSISTANT_API_KEY = $secrets.ASSISTANT_API_KEY
    ANTHROPIC_API_KEY = $secrets.ANTHROPIC_API_KEY
}

Send-Manifest (Get-RenderedManifests (Join-Path $repositoryRoot 'infra/k8s') $tokens)
foreach ($workload in @('statefulset/kafka', 'statefulset/redis', 'deployment/triage-service',
        'deployment/clinical-assistant-service')) {
    Invoke-Kubectl --namespace $namespace rollout status $workload --timeout=600s
}
# The Gateway has its address and Let's Encrypt issued the certificate of the HTTPS listener
Invoke-Kubectl --namespace vidacruz-edge wait --for condition=Programmed gateway/vidacruz --timeout=300s
Invoke-Kubectl --namespace vidacruz-edge wait --for condition=Ready certificate/vidacruz-tls --timeout=600s

$publicHost = $outputs.publicHost.value
Write-Host "Deployed $ImageTag."
Write-Host "  Triage:     https://$publicHost/triage/pacientes   (Swagger: https://$publicHost/docs/triage/swagger-ui.html)"
Write-Host "  Assistant:  https://$publicHost/asistente/consulta (Swagger: https://$publicHost/docs/asistente/swagger-ui.html)"
