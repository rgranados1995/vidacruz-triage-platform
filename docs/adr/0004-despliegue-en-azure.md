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

### Secretos

Ningún secreto está en el repositorio. Los Secrets de Kubernetes (`triage-service-secrets`,
`clinical-assistant-service-secrets`, `redis-auth`) los crea `infra/azure/deploy.ps1` a partir de variables de
entorno, que en GitHub Actions vienen de los secretos del environment `azure`.

### CI y despliegue

- Un workflow de CI por servicio en cada push y pull request: `./gradlew build` con Testcontainers y la imagen de
  Docker.
- El despliegue se lanza a mano (`workflow_dispatch`). GitHub inicia sesión en Azure con OIDC usando una identidad
  administrada que confía solo en el environment `azure` del repositorio. La identidad tiene Reader en el grupo,
  AcrPush en el registro y Cluster Admin en el AKS; no hay contraseñas de Azure guardadas en GitHub.
- `kubectl` usa siempre un kubeconfig propio del clúster, en un archivo aparte, sin tocar el del usuario.

## Consecuencias

- Costo aproximado de 140 a 150 USD al mes. El AKS y el PostgreSQL se pueden detener cuando no se usan.
- Un solo nodo y un solo broker: una caída del nodo detiene la plataforma hasta que AKS lo reemplace. Es aceptable
  para la evaluación; en producción serían 3 nodos en zonas y Kafka administrado.
- Evoluciones documentadas: Event Hubs o un Kafka con 3 brokers; Key Vault con el driver CSI y Workload Identity para
  los secretos; red privada para PostgreSQL; NetworkPolicies (requieren Cilium al crear el clúster); exportación de
  trazas a Azure Monitor.
