// Azure resources of VidaCruz, in one resource group: AKS (Gateway API with the application routing add-on and
// managed Istio; Microsoft Entra ID sign-in with Azure RBAC and no local accounts), the container registry, PostgreSQL Flexible Server, the public IPs of the cluster and the identity
// that GitHub Actions uses to deploy (OIDC, no stored keys). Kafka and Redis run inside the cluster (infra/k8s).
// Deployed by infra/azure/provision.ps1.

@description('Region of every resource.')
param location string = resourceGroup().location

@description('Prefix of the resource names (lowercase letters and digits).')
@minLength(3)
@maxLength(12)
param namePrefix string = 'vidacruz'

@description('Size of the nodes of the cluster. With 4 vCPU one node runs the platform and two replicas of each service.')
param nodeVmSize string = 'Standard_B4ms'

@description('Nodes of the cluster (a node more needs vCPU quota in the region).')
@minValue(1)
@maxValue(3)
param nodeCount int = 1

@description('Administrator of PostgreSQL; the triage connects with it (DB_USERNAME).')
param postgresAdminLogin string = 'triage_admin'

@description('Password of the PostgreSQL administrator (DB_PASSWORD of the triage).')
@secure()
@minLength(16)
param postgresAdminPassword string

@description('Exact subject of the OIDC token of the deployment job. Repositories created since July 2026 use the immutable format repo:OWNER@OWNER-ID/REPO@REPO-ID:environment:azure; provision.ps1 computes it.')
@minLength(1)
param githubSubject string

@description('GitHub environment of the deployment workflow; only its jobs get a token for Azure.')
param githubEnvironment string = 'azure'

@description('Object id of the person who administers the cluster (provision.ps1 passes the one signed in).')
param clusterAdminPrincipalId string

var suffix = take(uniqueString(resourceGroup().id), 6)
var registryName = '${namePrefix}${suffix}'
var clusterName = '${namePrefix}-aks'
var dnsLabel = '${namePrefix}-${suffix}'
var postgresName = '${namePrefix}-${suffix}-pg'

// Built-in roles
var acrPullRole = '7f951dda-4ed3-4680-a7ca-43fe172d538d'
var acrPushRole = '8311e382-0749-4cb8-b61a-304f252e45ec'
var readerRole = 'acdd72a7-3385-48ef-bd42-f606fba81ae7'
var aksClusterUserRole = '4abbcc35-e782-43d8-92c5-2d3f1bd2253f'
var aksRbacClusterAdminRole = 'b1ff04bb-8a4e-4dc4-8eb5-8693973ce19b'
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
    // Sign-in with Microsoft Entra ID and authorization with Azure RBAC; no local account (admin credentials) exists
    aadProfile: {
      managed: true
      enableAzureRBAC: true
    }
    disableLocalAccounts: true
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
    subject: githubSubject
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

// Gets the Entra ID kubeconfig of the cluster; what it may do inside is the role "Azure Kubernetes Service RBAC
// Writer" on the namespace vidacruz only, assigned by provision.ps1 (a namespace is not a resource of Bicep)
resource deployerGetsKubeconfig 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(cluster.id, deployer.id, aksClusterUserRole)
  scope: cluster
  properties: {
    principalId: deployer.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', aksClusterUserRole)
  }
}

// The administrator prepares the cluster (cert-manager, the Gateway, the namespaces and the public routes)
resource administratorManagesCluster 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(cluster.id, clusterAdminPrincipalId, aksRbacClusterAdminRole)
  scope: cluster
  properties: {
    principalId: clusterAdminPrincipalId
    principalType: 'User'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', aksRbacClusterAdminRole)
  }
}

output registryName string = registry.name
output registryLoginServer string = registry.properties.loginServer
output clusterName string = cluster.name
output clusterId string = cluster.id
output postgresHost string = postgres.properties.fullyQualifiedDomainName
output postgresAdminLogin string = postgresAdminLogin
output ingressIpName string = ingressIp.name
output publicHost string = ingressIp.properties.dnsSettings.fqdn
output resourceGroupName string = resourceGroup().name
output deployerClientId string = deployer.properties.clientId
output deployerPrincipalId string = deployer.properties.principalId
output tenantId string = subscription().tenantId
output subscriptionId string = subscription().subscriptionId
