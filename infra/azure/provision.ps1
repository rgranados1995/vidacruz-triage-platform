#Requires -Version 7
<#
.SYNOPSIS
    Creates (or updates) the Azure resources of VidaCruz: resource group, AKS, registry, PostgreSQL and the identity
    of the deployment workflow. Run it once, signed in with `az login` as Owner of the subscription (it assigns
    roles). It creates billable resources.

.EXAMPLE
    $env:POSTGRES_PASSWORD = '<password of 16+ characters>'
    ./infra/azure/provision.ps1 -ResourceGroup vidacruz-rg -Location eastus2 -GithubRepository owner/name
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $ResourceGroup,
    [string] $Location = 'eastus2',
    [Parameter(Mandatory)] [string] $GithubRepository,
    [string] $NodeVmSize = 'Standard_B4ms'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-Az {
    & az @args
    if ($LASTEXITCODE -ne 0) { throw "az $($args[0]) failed with exit code $LASTEXITCODE" }
}

if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD) -or $env:POSTGRES_PASSWORD.Length -lt 16) {
    throw 'Set POSTGRES_PASSWORD (16 or more characters) in the environment before running this script.'
}

Invoke-Az group create --name $ResourceGroup --location $Location --output none
Invoke-Az deployment group create `
    --resource-group $ResourceGroup `
    --name vidacruz `
    --template-file (Join-Path $PSScriptRoot 'main.bicep') `
    --parameters location=$Location githubRepository=$GithubRepository nodeVmSize=$NodeVmSize `
        postgresAdminPassword=$env:POSTGRES_PASSWORD `
    --output none

$outputs = (Invoke-Az deployment group show --resource-group $ResourceGroup --name vidacruz `
    --query properties.outputs --output json) -join "`n" | ConvertFrom-Json

Write-Host "Provisioned. Public host of the APIs: https://$($outputs.publicHost.value)"
Write-Host ''
Write-Host 'Variables of the GitHub environment "azure" (Settings > Environments > azure > Variables):'
Write-Host "  AZURE_CLIENT_ID       = $($outputs.deployerClientId.value)"
Write-Host "  AZURE_TENANT_ID       = $($outputs.tenantId.value)"
Write-Host "  AZURE_SUBSCRIPTION_ID = $($outputs.subscriptionId.value)"
Write-Host "  AZURE_RESOURCE_GROUP  = $ResourceGroup"
Write-Host 'Secrets of the same environment: TRIAGE_API_KEY, ASSISTANT_API_KEY, POSTGRES_PASSWORD, REDIS_PASSWORD and'
Write-Host 'ANTHROPIC_API_KEY (optional: without it the assistant runs in degraded mode).'
