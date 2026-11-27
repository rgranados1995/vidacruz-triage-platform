#Requires -Version 7
<#
.SYNOPSIS
    Builds both images, pushes them to the registry and deploys the platform and both services to the namespace
    vidacruz of AKS. Used by the workflow .github/workflows/deploy.yml and by hand after `az login`.

.DESCRIPTION
    Reads the outputs of infra/azure/main.bicep, so provision.ps1 must have run first: it prepares what is outside
    the namespace (cert-manager, the Gateway, the public routes), which this script is not allowed to touch. It signs
    in to the cluster with Microsoft Entra ID (kubelogin, no admin credentials) and only writes in vidacruz. The
    secrets come only from the environment and are never printed: TRIAGE_API_KEY, ASSISTANT_API_KEY,
    POSTGRES_PASSWORD, REDIS_PASSWORD and ANTHROPIC_API_KEY (optional).

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
. (Join-Path $PSScriptRoot 'common.ps1')

$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '../..')
$namespace = 'vidacruz'
$services = @('triage-service', 'clinical-assistant-service')

function Get-RequiredSecret([string] $Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Set $Name in the environment." }
    return $value
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

$outputs = Get-DeploymentOutputs $ResourceGroup
$registry = $outputs.registryLoginServer.value

# Signs in to the cluster before the build: the OIDC token of the workflow lasts minutes, and the Entra ID token of
# the cluster taken now is cached for the rest of the run
Connect-Cluster $ResourceGroup $outputs.clusterName.value
Invoke-Kubectl --namespace $namespace get configmaps --output name | Out-Null

if (-not $SkipBuild) {
    Invoke-Native az acr login --name $outputs.registryName.value
    foreach ($service in $services) {
        $image = "$registry/${service}:$ImageTag"
        $context = Join-Path $repositoryRoot $service
        Invoke-Native docker build --file (Join-Path $context 'deployment/Dockerfile') --tag $image $context
        Invoke-Native docker push $image
    }
}

$tokens = @{
    REGISTRY  = $registry
    IMAGE_TAG = $ImageTag
    DB_HOST   = $outputs.postgresHost.value
}
# The secrets first, so they exist before the pods that read them
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

$publicHost = $outputs.publicHost.value
Write-Host "Deployed $ImageTag."
Write-Host "  Triage:     https://$publicHost/triage/pacientes   (Swagger: https://$publicHost/docs/triage/swagger-ui.html)"
Write-Host "  Assistant:  https://$publicHost/asistente/consulta (Swagger: https://$publicHost/docs/asistente/swagger-ui.html)"
