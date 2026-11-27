# VidaCruz · Triaje digital con asistente clínico

[![triage-service](https://github.com/rgranados1995/vidacruz-triage-platform/actions/workflows/triage-service.yml/badge.svg)](https://github.com/rgranados1995/vidacruz-triage-platform/actions/workflows/triage-service.yml)
[![clinical-assistant-service](https://github.com/rgranados1995/vidacruz-triage-platform/actions/workflows/clinical-assistant-service.yml/badge.svg)](https://github.com/rgranados1995/vidacruz-triage-platform/actions/workflows/clinical-assistant-service.yml)

Backend de dos microservicios en **Java 21 reactivo** (Spring Boot 3 + WebFlux) para las salas de urgencias de
VidaCruz:

- **[`triage-service`](triage-service/README.md)** registra a los pacientes, los **clasifica solo** en 5 niveles por
  sus signos vitales, vigila su tiempo de espera (**SLA**) y **escala y alerta** al equipo médico cuando se supera, con
  auditoría clínica completa.
- **[`clinical-assistant-service`](clinical-assistant-service/README.md)** responde preguntas de los médicos sobre
  los protocolos clínicos con **IA (RAG con Claude), siempre citando la fuente y sin inventar**, y muestra en tiempo
  real cuántos pacientes activos hay por nivel, armado desde los eventos de Kafka.

---

## Índice

1. [Levantar todo con un comando](#1-levantar-todo-con-un-comando)
2. [Arquitectura](#2-arquitectura)
3. [Tecnologías](#3-tecnologías)
4. [Decisiones clave](#4-decisiones-clave)
5. [Supuestos ante las ambigüedades del enunciado](#5-supuestos-ante-las-ambigüedades-del-enunciado)
6. [Cómo probarlo](#6-cómo-probarlo)
7. [Calidad y pruebas automáticas](#7-calidad-y-pruebas-automáticas)
8. [Observabilidad](#8-observabilidad)
9. [Despliegue: Docker, Kubernetes y Azure](#9-despliegue-docker-kubernetes-y-azure)
10. [Estructura del repositorio](#10-estructura-del-repositorio)
11. [Límites conocidos y evolución](#11-límites-conocidos-y-evolución)

---

## 1. Levantar todo con un comando

Solo necesitas **Docker Desktop** (con 8 GB de memoria) y **Git**. No hace falta instalar Java, PostgreSQL ni Kafka.

```powershell
git clone https://github.com/rgranados1995/vidacruz-triage-platform.git
cd vidacruz-triage-platform
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

- El archivo `docker-compose.demo.yml` es opcional: baja el SLA de los pacientes CRITICO a **1 minuto**, para ver el
  escalamiento y la alerta sin esperar 5. Sin él (`docker compose up -d --build`) corren los SLA reales del enunciado.
- La primera vez tarda entre 10 y 15 minutos (descarga imágenes, dependencias y el modelo de búsqueda); después, menos
  de un minuto.
- **Opcional:** para que el asistente responda con IA de verdad, copia `.env.example` a `.env` y pon tu
  `ANTHROPIC_API_KEY`. Sin la llave todo funciona y el asistente responde 503 en **modo degradado** cuando necesita a
  la IA.

Se levantan 8 contenedores:

| Servicio | Dirección | Para qué |
|---|---|---|
| `triage-service` | <http://localhost:8081> · Swagger en `/swagger-ui.html` | API del triaje |
| `clinical-assistant-service` | <http://localhost:8082> · Swagger en `/swagger-ui.html` | API del asistente |
| `grafana` | <http://localhost:3000> | Tableros de métricas (entras sin usuario) |
| `jaeger` | <http://localhost:16686> | Trazas de punta a punta |
| `prometheus` | <http://localhost:9090> | Guarda las métricas |
| `postgres`, `kafka`, `redis` | 5432, 29092, 6379 | Infraestructura |

Comprueba que estén vivos: <http://localhost:8081/health/readiness> y <http://localhost:8082/health/readiness> deben
decir `{"status":"UP"}`.

## 2. Arquitectura

[![Arquitectura de la plataforma](docs/diagramas/arquitectura.svg)](docs/diagramas/arquitectura.svg)

Cómo viaja un registro, de punta a punta:

[![Registro, evento y escalamiento de punta a punta](docs/diagramas/registro-y-escalamiento.svg)](docs/diagramas/registro-y-escalamiento.svg)

Los diagramas están hechos con Archify. Cada uno tiene una **versión interactiva** (zoom, búsqueda, tema oscuro,
exportar) en [`docs/diagramas`](docs/diagramas/README.md); ábrela desde tu copia del repositorio en el navegador.
El de las tres compuertas del RAG está en la sección 4 y el de Azure, en la sección 9.

**Por qué así:**

- **Dos servicios, comunicados solo por Kafka.** El asistente nunca llama al triaje, y el triaje nunca depende del
  asistente: si el asistente o la IA se caen, el triaje sigue registrando, clasificando y escalando.
- **Arquitectura limpia** en cada servicio (scaffold de Bancolombia 3.28.0): el dominio (reglas RN-01 a RN-05 y casos
  de uso) no conoce Spring, la base de datos, Kafka ni la IA; los adaptadores se conectan por puertos (interfaces).
- **Reactivo de punta a punta, sin bloquear el event loop:** WebFlux, R2DBC, reactor-kafka, Lettuce y el cliente
  asíncrono de Anthropic. Lo que es CPU o bloqueante por naturaleza (los embeddings, crear el consumidor de Kafka) va a
  `boundedElastic`. **BlockHound** corre en todas las pruebas y hace fallar cualquier llamada bloqueante en un hilo
  reactivo.

## 3. Tecnologías

| Área | Tecnología |
|---|---|
| Lenguaje y framework | Java 21, Spring Boot 3.5.7, Spring WebFlux (RouterFunction), Project Reactor |
| Triaje | R2DBC PostgreSQL 16, Flyway, `TransactionalOperator`, LISTEN/NOTIFY, Server-Sent Events |
| Mensajería | Kafka 3.9 (KRaft), reactor-kafka 1.3.25, CloudEvents 1.0 en JSON, outbox transaccional |
| Asistente | Spring AI 1.1.8 (`TransformersEmbeddingModel` ONNX + `SimpleVectorStore`), SDK oficial de Anthropic para Java 3.8.0 (`claude-opus-5-5`), Redis 7.4 con scripts Lua |
| Resiliencia | Resilience4j 2.4.0: retry con backoff, circuit breaker y time limiter (operadores reactivos) |
| Seguridad | Spring Security reactivo con API key por servicio (`X-API-Key`), errores RFC 9457, headers de seguridad |
| Observabilidad | Actuator (health, liveness, readiness), Micrometer + Prometheus, OpenTelemetry (Jaeger), Grafana 12, logs JSON (ECS) con `traceId` |
| Documentación de API | springdoc-openapi (Swagger UI) |
| Pruebas | JUnit 5, Testcontainers 1.21.4 (PostgreSQL, Kafka, Redis reales), WireMock (IA simulada), BlockHound, ArchUnit, JaCoCo, Pitest |
| Entrega | Docker (multi-stage, usuario no root), Kubernetes (Kustomize), Azure (Bicep: AKS, ACR, PostgreSQL Flexible), GitHub Actions |

## 4. Decisiones clave

| Decisión | Por qué | Dónde |
|---|---|---|
| **Outbox transaccional** para publicar en Kafka | El cambio del paciente, su auditoría y su evento se guardan en una sola transacción: nunca hay un evento sin cambio ni un cambio sin evento, aunque Kafka esté caído. Un relay publica después, y varias réplicas no publican el mismo evento (`FOR UPDATE SKIP LOCKED` con lease) | `triage-service` |
| **Eventos CloudEvents 1.0 en JSON**, clave = id del paciente | Formato estándar y legible. La clave mantiene el orden de los eventos de cada paciente en su partición. Llevan nivel, estado y `ocurridoEn`, pero **no el nombre** del paciente | `triage-contract` |
| **Escalamiento idempotente en SQL** (RN-03) | Un solo `UPDATE patient SET escalated = TRUE WHERE id = :id AND status = 'REGISTERED' AND NOT escalated`: aunque corran varias réplicas a la vez, un paciente se escala una sola vez, y nunca en consulta ni en alta | `PatientSql` |
| **Auditoría que no se puede borrar** (RN-04) | No existe endpoint para borrarla, y además triggers de PostgreSQL rechazan `UPDATE`, `DELETE` y `TRUNCATE` sobre la tabla | Migración V1 |
| **Reporte en una sola consulta SQL** (RF-05) | `generate_series`, `ROW_NUMBER() OVER (PARTITION BY ...)`, agregados con `FILTER`, `PERCENTILE_CONT(0.95)` y `GROUPING SETS`; nada se calcula en memoria | `OperationReportSql` |
| **Alertas RF-06: persistidas, con estado y por SSE** | Una alerta no se puede perder: se crea en la transacción del escalamiento, tiene estado (pendiente, reconocida, resuelta) y se resuelve sola al atender al paciente. El stream SSE sirve a cualquier cliente HTTP, y entre réplicas se reparte con LISTEN/NOTIFY | [ADR 0001](docs/adr/0001-alertas-de-escalamiento.md) |
| **RAG con tres compuertas** (RN-05) | 1) búsqueda semántica con umbral: si nada se parece, se responde el aviso **sin llamar a la IA**; 2) Claude solo ve esos fragmentos y responde en JSON con esquema obligatorio; 3) se descartan las respuestas que citan fragmentos que no se le dieron. Las fuentes salen del corpus, nunca del texto de la IA | [ADR 0002](docs/adr/0002-rag-del-asistente.md) |
| **Vector store en memoria y embeddings locales** | Tres protocolos no justifican una base vectorial; el modelo multilingüe corre dentro de la JVM, sin segunda llave ni red, así que el aviso de "sin protocolo" funciona aunque no haya internet | [ADR 0002](docs/adr/0002-rag-del-asistente.md) |
| **Modo degradado** | Retry solo de fallas pasajeras, circuit breaker y límite de 15 s por intento. Sin IA, el asistente responde 503 con un mensaje claro y `Retry-After`, sigue `UP` y el triaje no se entera | [ADR 0003](docs/adr/0003-modo-degradado-del-asistente.md) |
| **Proyección del estado de urgencias en Redis** | Idempotente por estado y fecha del evento: un duplicado no cuenta dos veces y un evento atrasado no deshace uno nuevo. Si Redis pierde los datos, el asistente la reconstruye leyendo el topic desde el principio | `clinical-assistant-service` |
| **Autenticación con API key** (`X-API-Key`), una por servicio | El enunciado permite JWT o API key. Son APIs entre sistemas internos y una API key es simple de operar y de probar; una llave por servicio limita el daño si una se filtra. Las llaves llegan por variables de entorno (en Kubernetes, por Secrets) | Ambos servicios |
| **Errores RFC 9457** con código propio (`TRG-xxx`, `AST-xxx`) | Formato estándar; los datos inválidos devuelven **todos** los campos con error a la vez, con mensajes en español | Ambos servicios |
| **Despliegue en AKS** con Gateway API, PostgreSQL administrado y Kafka y Redis en el clúster | Los manifests de Kubernetes se aplican tal cual; HTTPS con Let's Encrypt; costo bajo para una evaluación | [ADR 0004](docs/adr/0004-despliegue-en-azure.md) |

Cómo el asistente evita inventar respuestas (RN-05):

[![RAG con tres compuertas](docs/diagramas/rag-tres-compuertas.svg)](docs/diagramas/rag-tres-compuertas.svg)

## 5. Supuestos ante las ambigüedades del enunciado

- **SLA:** se mide desde el registro hasta la **primera** entrada a consulta. Solo se escalan pacientes en
  `REGISTRADO`; el escalamiento no cambia el estado, marca el flag `escalado`.
- **Clasificación:** si se cumplen condiciones de varios niveles, gana el más grave. NO_URGENTE_2 aplica cuando la
  queja principal **completa** es una de las administrativas, sin importar mayúsculas, tildes ni espacios de más.
- **Validaciones:** además de los rangos del enunciado (nombre de 3 a 100 caracteres, diastólica menor que la
  sistólica), la queja principal admite hasta 500 caracteres para proteger la base de datos.
- **Transiciones:** se permite el alta directa (`REGISTRADO → ALTA`). Nunca se vuelve a `REGISTRADO`.
- **Reporte:** las horas se agrupan en UTC; las fechas `desde` y `hasta` deben traer zona (`Z` o `-05:00`) y el rango
  máximo es de 31 días.
- **Estado de urgencias:** "activo" es todo paciente que no está en `ALTA` (registrado o en consulta).
- **Alertas (RF-06):** no llevan nombre ni queja del paciente, porque se ven en pantallas compartidas.
- **Credenciales locales:** el `docker-compose.yml` trae llaves y contraseñas **solo de desarrollo local** para cumplir
  el "un solo comando"; se cambian con `.env`. En Kubernetes todas llegan por Secrets. La llave de la IA nunca tiene
  valor por defecto.

## 6. Cómo probarlo

| Forma | Dónde | Para quién |
|---|---|---|
| **Postman** | Importa [`docs/postman/VidaCruz.postman_collection.json`](docs/postman/VidaCruz.postman_collection.json): 31 peticiones en orden, con las llaves puestas, los ids guardados solos y una verificación en cada respuesta | La forma más rápida de recorrer todo |
| **Swagger** | <http://localhost:8081/swagger-ui.html> y <http://localhost:8082/swagger-ui.html> (botón **Authorize** con la API key) | Probar a mano desde el navegador |
| **curl paso a paso** | [README del triaje](triage-service/README.md) y [README del asistente](clinical-assistant-service/README.md): firmas, datos de prueba E1 a E6, casos de uso y respuestas reales | Entender cada caso |

Las API keys locales son `local-dev-api-key-0001` (triaje) y `local-dev-assistant-key-0001` (asistente).

## 7. Calidad y pruebas automáticas

```powershell
cd triage-service
./gradlew build
```

Requiere Java 21 y Docker abierto (las pruebas levantan PostgreSQL, Kafka y Redis reales). Lo mismo en
`clinical-assistant-service`, donde la IA se simula con WireMock y no hace falta llave.

| | Triaje | Asistente |
|---|---|---|
| Pruebas | 373 | 193 |
| Cobertura mínima exigida por módulo (JaCoCo) | 90 % | 90 % |
| Pruebas de mutación (Pitest) | 262 mutantes eliminados, 0 sobrevivientes | 239 eliminados, 0 sobrevivientes |

Cubren lo que pide el enunciado: la clasificación de E1 a E6, las transiciones válidas e inválidas, la idempotencia
del escalamiento, las citas del RAG y el caso "sin coincidencia, sin alucinación". También cubren:
- concurrencia entre réplicas;
- la IA caída (reintento, circuit breaker y 503);
- eventos duplicados, atrasados o dañados;
- la reconstrucción desde Kafka;
- que el corpus sea el del enunciado palabra por palabra.

Además, **BlockHound** hace fallar cualquier llamada bloqueante en un hilo reactivo, y **ArchUnit** las violaciones
de la arquitectura limpia. El CI de GitHub Actions corre el build completo de cada servicio en cada push.

## 8. Observabilidad

- **Grafana** (<http://localhost:3000>) trae tres tableros listos:
  - **Urgencias:** pacientes por nivel, escalamientos y alertas.
  - **Asistente clínico:** respuestas por resultado, latencia de la IA, circuit breaker y lag de Kafka.
  - **Servicios:** tráfico, errores, latencia p95 por ruta, memoria y CPU.
- **Métricas de negocio** pedidas por el enunciado, en `/prometheus` de cada servicio:
  - `triage_patients_classified_total{level}`;
  - `triage_patients_escalated_total{level}`;
  - `assistant_llm_latency_seconds{outcome}`, un histograma por intento.
- **Trazas distribuidas** (OpenTelemetry, bonus del enunciado): una traza en **Jaeger** (<http://localhost:16686>) va
  desde la solicitud HTTP del triaje hasta el procesamiento del evento en el asistente. El contexto viaja por el outbox
  y por el header `traceparent` de Kafka.
- **Logs** en JSON con el `traceId`, sin datos personales de los pacientes ni el texto de las preguntas.
- **Probes:** `/health/liveness` y `/health/readiness`. La base de datos saca al triaje del balanceador sin
  reiniciarlo; el modo degradado del asistente no lo saca.

## 9. Despliegue: Docker, Kubernetes y Azure

- **Docker:** cada servicio tiene un `deployment/Dockerfile` multi-stage que compila con su propio Gradle y corre con
  un JRE y un usuario no root.
- **Kubernetes:** manifests con Kustomize en `*/deployment/k8s` (Deployment de 2 réplicas, probes, recursos, Pod
  Security `restricted`, PodDisruptionBudget, ConfigMap y la forma de los Secrets) y la plataforma en `infra/k8s`:
  - Kafka y Redis;
  - las rutas de Gateway API;
  - el emisor de certificados.
- **Azure:** `infra/azure/main.bicep` crea:
  - AKS;
  - el registro de imágenes;
  - PostgreSQL Flexible con TLS;
  - la IP pública con nombre DNS;
  - la identidad con la que GitHub Actions despliega por OIDC, sin contraseñas guardadas.

  `provision.ps1` crea los recursos y `deploy.ps1` (o el workflow `deploy`) construye, sube y despliega.
- **URL en Azure:** _se completa al desplegar_ (`https://<nombre>.<región>.cloudapp.azure.com/triage/...` y
  `/asistente/...`).

[![Despliegue en Azure](docs/diagramas/despliegue-azure.svg)](docs/diagramas/despliegue-azure.svg)

Detalle y costos en el [ADR 0004](docs/adr/0004-despliegue-en-azure.md).

## 10. Estructura del repositorio

```text
├── triage-service/                # Microservicio de triaje (arquitectura limpia)
│   ├── domain/                    #   model (reglas, puertos) y usecase
│   ├── infrastructure/            #   reactive-web, scheduler, r2dbc-postgresql, kafka-producer, métricas
│   ├── applications/app-service/  #   arranque y configuración
│   ├── deployment/                #   Dockerfile y manifests de Kubernetes
│   └── docs/pruebas/              #   datos de prueba (pacientes E1 a E6, estados, alertas)
├── clinical-assistant-service/    # Microservicio del asistente (misma estructura)
│   └── docs/pruebas/              #   preguntas de prueba
├── docs/
│   ├── adr/                       # Decisiones de arquitectura (4 ADR)
│   ├── diagramas/                 # Diagramas interactivos (Archify) y sus fuentes
│   └── postman/                   # Colección de Postman
├── infra/
│   ├── k8s/                       # Plataforma en Kubernetes (Kafka, Redis, Gateway, rutas)
│   └── azure/                     # Bicep y scripts de aprovisionamiento y despliegue
├── observability/                 # Configuración de Prometheus y tableros de Grafana
├── .github/workflows/             # CI de cada servicio y despliegue a Azure
├── docker-compose.yml             # Todo el entorno local con un comando
└── docker-compose.demo.yml        # SLA de 1 minuto para la demo
```

## 11. Límites conocidos y evolución

- **Un broker de Kafka y un nodo de AKS:** suficiente para la evaluación; en producción serían 3 brokers (o Event
  Hubs) y 3 nodos en zonas.
- **Mensajes dañados de Kafka:** se registran y se saltan para no bloquear la partición; la evolución es una cola de
  mensajes fallidos (DLQ).
- **Fidelidad frase por frase del RAG:** las compuertas garantizan que la respuesta **cite** el corpus, no que cada
  frase sea fiel al fragmento citado; eso queda en manos del modelo. La evolución es una evaluación periódica con
  preguntas reales.
- **Reconstrucción del estado de urgencias:** si Redis se vacía con el servicio en marcha, se reconstruye al siguiente
  arranque.
- **Secretos en Azure:** hoy son Secrets de Kubernetes creados por el despliegue; la evolución es Key Vault con
  Workload Identity.
