# Functions shared by provision.ps1 and deploy.ps1 (dot-sourced). kubectl always runs with the kubeconfig of this
# cluster, in a file of its own: the kubeconfig of the user is never read or changed.

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

function Get-DeploymentOutputs([string] $ResourceGroup) {
    return (Invoke-Native az deployment group show --resource-group $ResourceGroup --name vidacruz `
        --query properties.outputs --output json) -join "`n" | ConvertFrom-Json
}

# The cluster has no local accounts: the kubeconfig signs in with Microsoft Entra ID, and kubelogin takes the token
# of the current Azure CLI session (a person with az login, or the workflow with its OIDC identity)
function Connect-Cluster([string] $ResourceGroup, [string] $ClusterName) {
    foreach ($tool in @('kubectl', 'kubelogin')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            throw "$tool is not installed. Install it with: az aks install-cli"
        }
    }
    $script:kubeconfig = Join-Path ([IO.Path]::GetTempPath()) "vidacruz-$ClusterName.kubeconfig"
    Invoke-Native az aks get-credentials --resource-group $ResourceGroup --name $ClusterName `
        --file $script:kubeconfig --overwrite-existing
    Invoke-Native kubelogin convert-kubeconfig --login azurecli --kubeconfig $script:kubeconfig
}
