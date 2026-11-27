# ADR 0004: Despliegue en Azure con AKS

- **Estado:** aceptada
- **Fecha:** 2026-11-26
- **Alcance:** `triage-service`, `clinical-assistant-service`, `infra/`, `.github/workflows/`

## Contexto

El enunciado pide manifests de Kubernetes (Deployment, Service, probes) con Azure como objetivo, AKS o Azure
Container Apps a elección, y una URL pública con las APIs. La plataforma necesita PostgreSQL, Kafka y Redis además de
los dos servicios. El costo tiene que ser bajo: es una evaluación, no producción.

## Decisión

### AKS, no Container Apps

Los manifests que pide el enunciado son de Kubernetes, y en AKS se aplican tal cual. Container Apps no usa esos
manifests. El control plane va en el tier gratuito, con un solo nodo `Standard_B4ms`.

### Tamaño del clúster

Un nodo de 4 vCPU deja unos 3,8 vCPU asignables, y los pods de AKS (Istio administrado, CSI, DNS, cert-manager) ya
reservan cerca de 1,9. Los requests de CPU de la plataforma son lo que cada pod usa en reposo, sin límite de CPU, para
que use la CPU libre del nodo al arrancar o con tráfico:

| Workload | Réplicas | CPU pedida | Memoria pedida |
|---|---|---|---|
| `clinical-assistant-service` | 2 | 250m | 1Gi |
| `triage-service` | 2 | 100m | 512Mi |
| Kafka | 1 | 100m | 1Gi |
| Redis | 1 | 50m | 128Mi |

En total, 850m (antes 1850m, que dejaba el nodo al 96 % y a Kafka sin lugar: issue #6). `provision.ps1` expone
`-NodeCount` (1 a 3) y `-NodeVmSize`; un nodo más necesita cuota de vCPU en la región, que Azure for Students no da.

### Dónde corre cada dependencia

| Dependencia | Dónde | Por qué |
|---|---|---|
| PostgreSQL | Azure Database for PostgreSQL Flexible Server (Burstable B1ms) | Guarda la auditoría clínica: backups de 7 días y TLS administrados. El firewall solo deja entrar la IP de salida fija del clúster |
| Kafka | StatefulSet de un broker KRaft en el clúster, con disco administrado | Mismo protocolo y misma imagen que el compose. Event Hubs exige SASL_SSL y el tier Standard |
| Redis | StatefulSet en el clúster, con `requirepass` y disco | Es una proyección que el asistente reconstruye sola desde el topic si se pierde. No justifica un servicio administrado |

Variables nuevas: `DB_SSL_MODE` (`require` en Azure, `disable` en el compose) y `REDIS_PASSWORD`.

### Entrada pública: Gateway API, no NGINX

Microsoft da parches críticos al NGINX del add-on *application routing* solo hasta noviembre de 2026, y ingress-nginx
dejó de mantenerse en marzo de 2026. La entrada usa Gateway API administrada por AKS con la implementación de
*application routing* sobre Istio administrado (GatewayClass `approuting-istio`).

- El Gateway vive en `vidacruz-edge`, con Pod Security `baseline`, porque sus pods de Envoy los crea Istio. Los
  servicios viven en `vidacruz` con Pod Security `restricted`.
- La IP pública de entrada la crea el Bicep, con su nombre DNS de Azure (`<prefijo>-<sufijo>.<región>.cloudapp.azure.com`).
  El Gateway la toma con la anotación `azure-pip-name`.
- HTTPS con cert-manager y Let's Encrypt (HTTP-01 por el listener HTTP del Gateway). Todo lo demás en HTTP se
  redirige a HTTPS.
- Solo se publican `/triage/**`, `/asistente/**` y Swagger en `/docs/triage` y `/docs/asistente`. `/health` y
  `/prometheus` quedan dentro del clúster.
- Swagger se publica a propósito, sin API key: es el contrato de la API, no tiene datos de pacientes y es lo que usa
  quien evalúa. Se apaga con `API_DOCS_ENABLED=false`. Las rutas atienden `/docs/<servicio>/swagger-ui.html` con una
  regla exacta, porque un `PathPrefix` de Gateway API compara segmentos completos y `/swagger-ui` no cubre
  `/swagger-ui.html`.
- `/prometheus` pide la API key aunque no salga del clúster: dice cuántos pacientes llegan y qué tan graves. El
  Prometheus local la manda en el header `X-API-Key` (`http_headers` de su configuración).

### Secretos

Ningún secreto está en el repositorio. Los Secrets de Kubernetes (`triage-service-secrets`,
`clinical-assistant-service-secrets`, `redis-auth`) los crea `infra/azure/deploy.ps1` a partir de variables de
entorno, que en GitHub Actions vienen de los secretos del environment `azure`.

### Acceso al clúster: Microsoft Entra ID, sin cuentas locales

El clúster solo acepta tokens de Microsoft Entra ID (`aadProfile` con Azure RBAC y `disableLocalAccounts: true`): no
existen las credenciales de administrador (`az aks get-credentials --admin`). `kubectl` entra con `kubelogin`, que
toma el token de la sesión de la CLI de Azure.

| Quién | Rol | Qué puede hacer |
|---|---|---|
| Quien corre `provision.ps1` | Azure Kubernetes Service RBAC Cluster Admin | Preparar el clúster: cert-manager, el ClusterIssuer, el Gateway, los namespaces y las rutas públicas |
| El workflow de despliegue | Cluster User Role en el AKS y Azure Kubernetes Service RBAC Writer **solo en el namespace `vidacruz`** | Crear los Secrets y aplicar Kafka, Redis y los dos servicios |

Por eso el despliegue se parte en dos:

- `provision.ps1` (a mano, una vez y cada vez que cambie lo de afuera del namespace) aplica `infra/k8s/cluster`,
  `infra/k8s/edge`, `infra/k8s/namespace.yaml` e `infra/k8s/routes`. Qué se publica es una decisión del administrador,
  no del pipeline.
- `deploy.ps1` (el workflow) aplica `infra/k8s`, que solo tiene los workloads del namespace.

cert-manager se instala desde el `cert-manager.yaml` de su release `v1.18.2` y **solo si su SHA-256 coincide** con el
fijado en `provision.ps1`; si no coincide, no se aplica. Actualizar cert-manager es cambiar la versión y el hash juntos.

### CI y despliegue

- Un workflow de CI por servicio en cada push y pull request: `./gradlew build` con Testcontainers y la imagen de
  Docker.
- El despliegue se lanza a mano (`workflow_dispatch`). GitHub inicia sesión en Azure con OIDC usando una identidad
  administrada que confía solo en el environment `azure` del repositorio. La identidad tiene Reader en el grupo,
  AcrPush en el registro y, en el AKS, lo de la tabla de arriba; no hay contraseñas de Azure guardadas en GitHub.
- El subject del token de GitHub tiene que coincidir **exacto** con el de la credencial federada. Los repositorios
  creados desde julio de 2026 usan un formato inmutable con los ids del dueño y del repositorio:
  `repo:OWNER@OWNER-ID/REPO@REPO-ID:environment:azure` (en este repositorio,
  `repo:rgranados1995@63021833/vidacruz-triage-platform@1472061967:environment:azure`). `provision.ps1` lo calcula
  con la API de GitHub y acepta `-GithubSubject` para un repositorio con el formato anterior
  (`repo:OWNER/REPO:environment:azure`). Un subject distinto falla en `azure/login` con `AADSTS700213` (issue #5).
- El workflow entra al clúster antes de construir las imágenes: el token OIDC de GitHub dura minutos y el token de
  Entra ID del clúster, tomado al principio, queda en caché el resto del job.
- `kubectl` usa siempre un kubeconfig propio del clúster, en un archivo aparte, sin tocar el del usuario.

## Consecuencias

- Costo aproximado de 140 a 150 USD al mes. El AKS y el PostgreSQL se pueden detener cuando no se usan.
- Un solo nodo y un solo broker: una caída del nodo detiene la plataforma hasta que AKS lo reemplace. Es aceptable
  para la evaluación; en producción serían 3 nodos en zonas y Kafka administrado.
- Un clúster creado con una versión anterior de este ADR (cuentas locales y Cluster Admin para el workflow) se
  actualiza corriendo `provision.ps1` otra vez: activa Entra ID, quita las cuentas locales y le retira al workflow el
  rol de administrador.
- Grafana y Prometheus no se despliegan en Azure: con un nodo de 4 vCPU, la observabilidad se ve en local
  (`docker-compose.demo.yml`). Al desplegar, las métricas siguen en `/prometheus` de cada pod, con la API key.
- Evoluciones documentadas: Event Hubs o un Kafka con 3 brokers; Key Vault con el driver CSI y Workload Identity para
  los secretos; red privada para PostgreSQL; NetworkPolicies (requieren Cilium al crear el clúster); exportación de
  trazas a Azure Monitor; métricas en Azure Monitor managed Prometheus con Azure Managed Grafana, reusando los
  tableros de `observability/grafana` (en local ya corren con Prometheus y Grafana en el compose).
