#Requires -Version 7
<#
.SYNOPSIS
    Creates (or updates) the Azure resources of VidaCruz and prepares the cluster. Run it signed in with `az login`
    as Owner of the subscription (it assigns roles); it needs kubectl and kubelogin (az aks install-cli). It creates
    billable resources. Running it again is safe: every step converges to the same state.

.DESCRIPTION
    1. Deploys main.bicep: resource group, AKS with Microsoft Entra ID and no local accounts, registry, PostgreSQL and
       the identity of the deployment workflow, trusted by GitHub with the exact subject of its OIDC token.
    2. Makes the person who runs it administrator of the cluster (Azure RBAC) and gives the workflow only what it
       needs: its kubeconfig and write access to the namespace vidacruz.
    3. Prepares the cluster, which the workflow is not allowed to touch: cert-manager (checked by SHA-256), the
       Let's Encrypt issuer, the Gateway, the namespaces and the public routes.

.EXAMPLE
    $env:POSTGRES_PASSWORD = '<password of 16+ characters>'
    ./infra/azure/provision.ps1 -ResourceGroup vidacruz-rg -Location eastus2 -GithubRepository owner/name
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $ResourceGroup,
    [string] $Location = 'eastus2',
    [Parameter(Mandatory)] [string] $GithubRepository,
    # Subject of the OIDC token of the deployment job. Empty: computed from the ids of the repository, in the format
    # of the repositories created since July 2026 (repo:OWNER@OWNER-ID/REPO@REPO-ID:environment:azure)
    [string] $GithubSubject,
    [string] $NodeVmSize = 'Standard_B4ms',
    [ValidateRange(1, 3)] [int] $NodeCount = 1
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'common.ps1')

$certManagerVersion = 'v1.18.2'
# SHA-256 of cert-manager.yaml of that release: a manifest that does not match is never applied
$certManagerSha256 = '0546b64bc806237885ccc80e345bce1585eb2ec2f476a7af593f030abdb3a16f'
$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '../..')
$githubEnvironment = 'azure'
# Built-in roles of AKS
$clusterAdminRole = '0ab0b1a8-8aac-4efd-b8c2-3ee1fb270be8'
$namespaceWriterRole = 'a7ffa36f-339b-4b5c-8bdf-e2c188b2c0eb'

# GitHub puts the immutable ids of the owner and the repository in the subject; the public API gives them (GH_TOKEN,
# when set, lets it read a private repository)
function Get-GithubSubject([string] $Repository) {
    $headers = @{ 'User-Agent' = 'vidacruz-provision'; Accept = 'application/vnd.github+json' }
    if ($env:GH_TOKEN) { $headers.Authorization = "Bearer $env:GH_TOKEN" }
    $repo = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repository" -Headers $headers
    return "repo:$($repo.owner.login)@$($repo.owner.id)/$($repo.name)@$($repo.id):environment:$githubEnvironment"
}

function Grant-Role([string] $PrincipalId, [string] $Role, [string] $Scope) {
    $existing = Invoke-Native az role assignment list --assignee $PrincipalId --role $Role --scope $Scope `
        --query '[].id' --output tsv
    if (-not $existing) {
        Invoke-Native az role assignment create --assignee-object-id $PrincipalId --assignee-principal-type `
            ServicePrincipal --role $Role --scope $Scope --output none
    }
}

function Revoke-Role([string] $PrincipalId, [string] $Role, [string] $Scope) {
    $existing = Invoke-Native az role assignment list --assignee $PrincipalId --role $Role --scope $Scope `
        --query '[].id' --output tsv
    if ($existing) { Invoke-Native az role assignment delete --ids @($existing) }
}

# A new role assignment can take a few minutes to reach the cluster
function Wait-ClusterAdmin {
    $deadline = (Get-Date).AddMinutes(10)
    while ((& kubectl --kubeconfig $script:kubeconfig auth can-i create clusterroles 2>$null) -ne 'yes') {
        if ((Get-Date) -gt $deadline) { throw 'The cluster does not grant the administrator role yet; run again.' }
        Start-Sleep -Seconds 15
    }
}

function Install-CertManager {
    $manifest = Join-Path ([IO.Path]::GetTempPath()) "cert-manager-$certManagerVersion.yaml"
    Invoke-WebRequest -Uri "https://github.com/cert-manager/cert-manager/releases/download/$certManagerVersion/cert-manager.yaml" `
        -OutFile $manifest
    $actual = (Get-FileHash -Path $manifest -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $certManagerSha256) {
        Remove-Item -Path $manifest -Force
        throw "cert-manager.yaml $certManagerVersion has SHA-256 $actual instead of ${certManagerSha256}: not applied."
    }
    Invoke-Kubectl apply --filename $manifest
    $controllerArguments = (Invoke-Kubectl --namespace cert-manager get deployment cert-manager `
        --output 'jsonpath={.spec.template.spec.containers[0].args}') -join ''
    if (-not $controllerArguments.Contains('--enable-gateway-api')) {
        Invoke-Kubectl --namespace cert-manager patch deployment cert-manager --type json `
            --patch '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--enable-gateway-api"}]'
    }
    foreach ($deployment in @('cert-manager', 'cert-manager-webhook', 'cert-manager-cainjector')) {
        Invoke-Kubectl --namespace cert-manager rollout status "deployment/$deployment" --timeout=300s
    }
}

if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD) -or $env:POSTGRES_PASSWORD.Length -lt 16) {
    throw 'Set POSTGRES_PASSWORD (16 or more characters) in the environment before running this script.'
}
if ([string]::IsNullOrWhiteSpace($GithubSubject)) { $GithubSubject = Get-GithubSubject $GithubRepository }
$administrator = (Invoke-Native az ad signed-in-user show --query id --output tsv) -join ''

Invoke-Native az group create --name $ResourceGroup --location $Location --output none
Invoke-Native az deployment group create `
    --resource-group $ResourceGroup `
    --name vidacruz `
    --template-file (Join-Path $PSScriptRoot 'main.bicep') `
    --parameters location=$Location githubSubject=$GithubSubject nodeVmSize=$NodeVmSize nodeCount=$NodeCount `
        clusterAdminPrincipalId=$administrator postgresAdminPassword=$env:POSTGRES_PASSWORD `
    --output none
$outputs = Get-DeploymentOutputs $ResourceGroup
$clusterId = $outputs.clusterId.value
$deployer = $outputs.deployerPrincipalId.value

# The workflow writes only inside the namespace vidacruz. Versions before Entra ID gave it the admin credentials of
# the cluster: without local accounts they no longer work, and the role goes away.
Grant-Role $deployer $namespaceWriterRole "$clusterId/namespaces/vidacruz"
Revoke-Role $deployer $clusterAdminRole $clusterId

Connect-Cluster $ResourceGroup $outputs.clusterName.value
Wait-ClusterAdmin
# The Gateway API managed by AKS must be installed before cert-manager starts, or it never watches the Gateways
Invoke-Kubectl wait --for condition=established --timeout=300s crd/gateways.gateway.networking.k8s.io `
    crd/httproutes.gateway.networking.k8s.io
Install-CertManager

$tokens = @{
    PUBLIC_HOST     = $outputs.publicHost.value
    INGRESS_IP_NAME = $outputs.ingressIpName.value
    RESOURCE_GROUP  = $outputs.resourceGroupName.value
}
Send-Manifest (Get-RenderedManifests (Join-Path $repositoryRoot 'infra/k8s/cluster') $tokens)
Send-Manifest (Get-RenderedManifests (Join-Path $repositoryRoot 'infra/k8s/edge') $tokens)
Invoke-Kubectl apply --filename (Join-Path $repositoryRoot 'infra/k8s/namespace.yaml')
Send-Manifest (Get-RenderedManifests (Join-Path $repositoryRoot 'infra/k8s/routes') $tokens)
# The Gateway has its address and Let's Encrypt issued the certificate of the HTTPS listener
Invoke-Kubectl --namespace vidacruz-edge wait --for condition=Programmed gateway/vidacruz --timeout=300s
Invoke-Kubectl --namespace vidacruz-edge wait --for condition=Ready certificate/vidacruz-tls --timeout=600s

Write-Host "Provisioned. Public host of the APIs: https://$($outputs.publicHost.value)"
Write-Host "Subject trusted for the deployment workflow: $GithubSubject"
Write-Host ''
Write-Host 'Variables of the GitHub environment "azure" (Settings > Environments > azure > Variables):'
Write-Host "  AZURE_CLIENT_ID       = $($outputs.deployerClientId.value)"
Write-Host "  AZURE_TENANT_ID       = $($outputs.tenantId.value)"
Write-Host "  AZURE_SUBSCRIPTION_ID = $($outputs.subscriptionId.value)"
Write-Host "  AZURE_RESOURCE_GROUP  = $ResourceGroup"
Write-Host 'Secrets of the same environment: TRIAGE_API_KEY, ASSISTANT_API_KEY, POSTGRES_PASSWORD, REDIS_PASSWORD and'
Write-Host 'ANTHROPIC_API_KEY (optional: without it the assistant runs in degraded mode).'
