// Azure resources of VidaCruz, in one resource group: AKS (Gateway API with the application routing add-on and
// managed Istio), the container registry, PostgreSQL Flexible Server, the public IPs of the cluster and the identity
// that GitHub Actions uses to deploy (OIDC, no stored keys). Kafka and Redis run inside the cluster (infra/k8s).
// Deployed by infra/azure/provision.ps1.

@description('Region of every resource.')
param location string = resourceGroup().location

@description('Prefix of the resource names (lowercase letters and digits).')
@minLength(3)
@maxLength(12)
param namePrefix string = 'vidacruz'

@description('Size of the only node of the cluster.')
param nodeVmSize string = 'Standard_B4ms'

@description('Nodes of the cluster.')
@minValue(1)
@maxValue(3)
param nodeCount int = 1

@description('Administrator of PostgreSQL; the triage connects with it (DB_USERNAME).')
param postgresAdminLogin string = 'triage_admin'

@description('Password of the PostgreSQL administrator (DB_PASSWORD of the triage).')
@secure()
@minLength(16)
param postgresAdminPassword string

@description('GitHub repository allowed to deploy, as owner/name.')
param githubRepository string

@description('GitHub environment of the deployment workflow; only its jobs get a token for Azure.')
param githubEnvironment string = 'azure'

var suffix = take(uniqueString(resourceGroup().id), 6)
var registryName = '${namePrefix}${suffix}'
var clusterName = '${namePrefix}-aks'
var dnsLabel = '${namePrefix}-${suffix}'
var postgresName = '${namePrefix}-${suffix}-pg'

// Built-in roles
var acrPullRole = '7f951dda-4ed3-4680-a7ca-43fe172d538d'
var acrPushRole = '8311e382-0749-4cb8-b61a-304f252e45ec'
var readerRole = 'acdd72a7-3385-48ef-bd42-f606fba81ae7'
var aksClusterAdminRole = '0ab0b1a8-8aac-4efd-b8c2-3ee1fb270be8'
var networkContributorRole = '4d97b98b-1d4f-4787-a291-c67834d212e7'

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: registryName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
  }
}

// Fixed outbound IP of the cluster: the only address the PostgreSQL firewall lets in
resource egressIp 'Microsoft.Network/publicIPAddresses@2024-05-01' = {
  name: '${namePrefix}-aks-egress'
  location: location
  sku: {
    name: 'Standard'
  }
  properties: {
    publicIPAllocationMethod: 'Static'
  }
}

// Public IP of the Gateway, with its Azure DNS name: the host of the certificate and of the APIs
resource ingressIp 'Microsoft.Network/publicIPAddresses@2024-05-01' = {
  name: '${namePrefix}-ingress'
  location: location
  sku: {
    name: 'Standard'
  }
  properties: {
    publicIPAllocationMethod: 'Static'
    dnsSettings: {
      domainNameLabel: dnsLabel
    }
  }
}

// Identity of the control plane, created first: it must already manage the outbound IP when the cluster is created
resource clusterIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${clusterName}-identity'
  location: location
}

resource clusterUsesEgressIp 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(egressIp.id, clusterIdentity.id, networkContributorRole)
  scope: egressIp
  properties: {
    principalId: clusterIdentity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', networkContributorRole)
  }
}

// The load balancer of the Gateway takes its IP from this resource group (azure-pip-name in infra/k8s/edge)
resource clusterUsesIngressIp 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(ingressIp.id, clusterIdentity.id, networkContributorRole)
  scope: ingressIp
  properties: {
    principalId: clusterIdentity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', networkContributorRole)
  }
}

resource cluster 'Microsoft.ContainerService/managedClusters@2026-03-01' = {
  name: clusterName
  location: location
  dependsOn: [
    clusterUsesEgressIp
    clusterUsesIngressIp
  ]
  sku: {
    name: 'Base'
    tier: 'Free'
  }
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${clusterIdentity.id}': {}
    }
  }
  properties: {
    dnsPrefix: clusterName
    agentPoolProfiles: [
      {
        name: 'system'
        mode: 'System'
        count: nodeCount
        vmSize: nodeVmSize
        osType: 'Linux'
        osSKU: 'AzureLinux'
        osDiskSizeGB: 64
      }
    ]
    networkProfile: {
      networkPlugin: 'azure'
      networkPluginMode: 'overlay'
      loadBalancerSku: 'standard'
      loadBalancerProfile: {
        outboundIPs: {
          publicIPs: [
            {
              id: egressIp.id
            }
          ]
        }
      }
    }
    // Gateway API managed by AKS, implemented by application routing with managed Istio (GatewayClass
    // approuting-istio). The NGINX of the add-on is not created: its support ends in November 2026.
    ingressProfile: {
      gatewayAPI: {
        installation: 'Standard'
      }
      webAppRouting: {
        enabled: true
        nginx: {
          defaultIngressControllerType: 'None'
        }
        gatewayAPIImplementations: {
          appRoutingIstio: {
            mode: 'Enabled'
          }
        }
      }
    }
    autoUpgradeProfile: {
      upgradeChannel: 'patch'
    }
  }
}

// The nodes pull the images from the registry with the identity of the kubelet
resource kubeletPullsImages 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, cluster.id, acrPullRole)
  scope: registry
  properties: {
    principalId: cluster.properties.identityProfile.kubeletidentity.objectId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRole)
  }
}

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: postgresName
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '16'
    administratorLogin: postgresAdminLogin
    administratorLoginPassword: postgresAdminPassword
    storage: {
      storageSizeGB: 32
      autoGrow: 'Enabled'
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
  }
}

resource triageDatabase 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgres
  name: 'triage'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

resource postgresAllowsCluster 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgres
  name: 'aks-egress'
  properties: {
    startIpAddress: egressIp.properties.ipAddress
    endIpAddress: egressIp.properties.ipAddress
  }
}

// Identity of the deployment workflow: GitHub exchanges its OIDC token for an Azure one, only for jobs of the
// repository that run in the GitHub environment of the deployment
resource deployer 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${namePrefix}-github-deploy'
  location: location
}

resource deployerTrustsGithub 'Microsoft.ManagedIdentity/userAssignedIdentities/federatedIdentityCredentials@2023-01-31' = {
  parent: deployer
  name: 'github-${githubEnvironment}'
  properties: {
    issuer: 'https://token.actions.githubusercontent.com'
    subject: 'repo:${githubRepository}:environment:${githubEnvironment}'
    audiences: [
      'api://AzureADTokenExchange'
    ]
  }
}

// Reads the outputs of this deployment and the resources of the group
resource deployerReads 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(resourceGroup().id, deployer.id, readerRole)
  properties: {
    principalId: deployer.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', readerRole)
  }
}

// Pushes the images it builds
resource deployerPushesImages 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, deployer.id, acrPushRole)
  scope: registry
  properties: {
    principalId: deployer.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPushRole)
  }
}

// Gets the credentials of the cluster to apply the manifests
resource deployerManagesCluster 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(cluster.id, deployer.id, aksClusterAdminRole)
  scope: cluster
  properties: {
    principalId: deployer.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', aksClusterAdminRole)
  }
}

output registryName string = registry.name
output registryLoginServer string = registry.properties.loginServer
output clusterName string = cluster.name
output postgresHost string = postgres.properties.fullyQualifiedDomainName
output postgresAdminLogin string = postgresAdminLogin
output ingressIpName string = ingressIp.name
output publicHost string = ingressIp.properties.dnsSettings.fqdn
output resourceGroupName string = resourceGroup().name
output deployerClientId string = deployer.properties.clientId
output tenantId string = subscription().tenantId
output subscriptionId string = subscription().subscriptionId
