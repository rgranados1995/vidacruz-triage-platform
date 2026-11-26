# clinical-assistant-service — Asistente Clínico de VidaCruz

Hace dos cosas:

1. **Responde preguntas de los médicos sobre los protocolos clínicos** usando IA (Claude), pero **solo con lo que
   dicen los protocolos oficiales** y siempre citando la fuente. Si ningún protocolo responde la pregunta, lo dice y
   no inventa nada.
2. **Muestra cuántos pacientes activos hay en urgencias por nivel**, en tiempo real. Lo arma escuchando los eventos
   del triaje en Kafka y lo guarda en Redis: nunca llama al servicio de triaje.

Si la IA no está disponible, el asistente no se cae: responde **503 con un mensaje claro** (modo degradado), y la
parte de urgencias sigue funcionando.

> Este servicio trabaja junto con [`triage-service`](../triage-service/README.md). Los dos se levantan juntos con
> **un solo comando** desde la raíz del repositorio.

---

## Índice

1. [Lo que necesitas instalar](#1-lo-que-necesitas-instalar)
2. [Descargar el proyecto y (opcional) poner la llave de la IA](#2-descargar-el-proyecto-y-opcional-poner-la-llave-de-la-ia)
3. [Levantar todo con un comando](#3-levantar-todo-con-un-comando)
4. [Comprobar que está vivo](#4-comprobar-que-está-vivo)
5. [Dos formas de probar: navegador o consola](#5-dos-formas-de-probar-navegador-o-consola)
6. [Firmas de la API](#6-firmas-de-la-api)
7. [Datos de prueba (preguntas)](#7-datos-de-prueba-preguntas)
8. [Casos de uso paso a paso](#8-casos-de-uso-paso-a-paso)
9. [Cómo evita inventar respuestas](#9-cómo-evita-inventar-respuestas)
10. [Códigos de error](#10-códigos-de-error)
11. [Ver por dentro: métricas y trazas](#11-ver-por-dentro-métricas-y-trazas)
12. [Problemas comunes](#12-problemas-comunes)
13. [Apagar y limpiar](#13-apagar-y-limpiar)
14. [Pruebas automáticas y estructura del código](#14-pruebas-automáticas-y-estructura-del-código)

---

## 1. Lo que necesitas instalar

| Programa | Para qué | Obligatorio |
|---|---|---|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Levanta todo: Kafka, Redis, base de datos y los dos servicios | **Sí** |
| [Git](https://git-scm.com/downloads) | Descargar el proyecto | **Sí** |
| Un navegador | Probar con Swagger (botones, sin escribir comandos) | Recomendado |
| Una llave de la API de Anthropic | Que la IA redacte respuestas de verdad | No (sin ella funciona en modo degradado) |
| Java 21 | Solo si quieres correr las pruebas automáticas | No |

En Docker Desktop: **Settings → Resources → Memory**, deja al menos **8 GB**.

## 2. Descargar el proyecto y (opcional) poner la llave de la IA

En una terminal (en Windows, **PowerShell**):

```powershell
git clone https://github.com/rgranados1995/vidacruz-triage-platform.git
cd vidacruz-triage-platform
```

**Todos los comandos de este README se escriben desde esa carpeta** (la raíz del repositorio).

**Para respuestas con IA:** copia el archivo de ejemplo, ábrelo y pon tu llave en la línea `ANTHROPIC_API_KEY=`. Las
demás líneas puedes dejarlas como están o borrarlas (tienen valores por defecto para local).

```powershell
Copy-Item .env.example .env
notepad .env
```

El archivo `.env` nunca se sube al repositorio. **Sin llave** todo funciona igual: las preguntas que necesitan a la
IA responden 503 (modo degradado) y las que no tienen protocolo siguen respondiendo el aviso.

## 3. Levantar todo con un comando

```powershell
docker compose up -d --build
```

La **primera vez tarda entre 10 y 15 minutos**: descarga imágenes, dependencias y el modelo de búsqueda de
protocolos (unos 120 MB, fijado por versión y verificado). Las siguientes veces tarda menos de un minuto.

Si además quieres ver escalamientos rápidos en el triaje (SLA de 1 minuto), usa el modo demo:

```powershell
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

Si pones o cambias la llave en `.env` con todo ya levantado, vuelve a crear el asistente para que la lea:

```powershell
docker compose up -d clinical-assistant-service
```

## 4. Comprobar que está vivo

```powershell
docker compose ps
```

Todos deben decir `running` (o `healthy`). En el navegador:

- <http://localhost:8082/health/readiness> → `{"status":"UP"}`

El asistente tarda un poco más que el triaje en arrancar (carga el modelo de búsqueda y los protocolos). Para saber si
quedó con IA o en modo degradado:

```powershell
docker compose logs clinical-assistant-service | Select-String "ANTHROPIC_API_KEY is not set"
```

Si aparece esa línea, está en **modo degradado** (sin llave). Si no aparece nada, tiene llave.

## 5. Dos formas de probar: navegador o consola

### La llave del asistente (API key)

Todas las rutas `/asistente/**` piden el header **`X-API-Key`**. En local es:

```text
local-dev-assistant-key-0001
```

(Es distinta a la del triaje. Puedes cambiarla con `ASSISTANT_API_KEY` en `.env`. Sin ella la respuesta es 401.)

### Opción A — Navegador con Swagger (la más fácil)

1. Abre <http://localhost:8082/swagger-ui.html>
2. Pulsa **Authorize**, pega `local-dev-assistant-key-0001`, pulsa **Authorize** y **Close**.
3. Abre `POST /asistente/consulta`, pulsa **Try it out**, escribe una pregunta de la
   [sección 7](#7-datos-de-prueba-preguntas) y pulsa **Execute**.

### Opción C — Postman (todo listo para importar)

En Postman: **Import** → elige el archivo [`docs/postman/VidaCruz.postman_collection.json`](../docs/postman/VidaCruz.postman_collection.json) de la raíz del repositorio. Trae las pruebas de los dos servicios en orden (carpetas 0 a 3), con las llaves ya puestas, los `id` guardados solos entre una petición y otra y una verificación en cada respuesta. Puedes ir petición por petición con **Send** o correr todo con **Run collection** (desmarca la carpeta 9, que es el stream en vivo y no termina nunca). La carpeta 2 necesita el modo demo y espera sola el minuto del escalamiento.

### Opción B — Consola con curl

En **Windows PowerShell** escribe **`curl.exe`** (con `.exe`). En Mac o Linux, `curl`. Las preguntas están listas
en archivos dentro de [`clinical-assistant-service/docs/pruebas/`](docs/pruebas): así no hay problemas con las tildes
ni los signos de interrogación.

```powershell
curl.exe -X POST http://localhost:8082/asistente/consulta -H "X-API-Key: local-dev-assistant-key-0001" -H "Content-Type: application/json" --data-binary "@clinical-assistant-service/docs/pruebas/consulta-crisis-hipertensiva.json"
```

## 6. Firmas de la API

Base: `http://localhost:8082` · Header obligatorio: `X-API-Key: local-dev-assistant-key-0001` · Formato: JSON ·
Errores: [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`) con un `codigo` `AST-xxx`.

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| `POST` | `/asistente/consulta` | Responde una pregunta con los protocolos y sus fuentes (RF-04 A) | 200, 400, 401, 503 |
| `GET` | `/asistente/estado-urgencias` | Pacientes activos por nivel de triaje (RF-04 B) | 200, 401, 503 |

### `POST /asistente/consulta`

Cuerpo:

| Campo | Tipo | Regla |
|---|---|---|
| `pregunta` | texto | Obligatorio, entre 3 y 1000 caracteres |

```json
{ "pregunta": "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?" }
```

Respuesta **200** cuando un protocolo responde la pregunta (el texto lo redacta la IA, así que puede variar un poco
entre una llamada y otra; las fuentes son las del protocolo usado):

```json
{
  "respuesta": "Reconfirmar la presión tras 5 minutos de reposo, no reducirla más de un 25% en la primera hora y vigilar signos de encefalopatía.",
  "fuentes": [
    { "protocolo": "PROTO-001", "seccion": "Medidas iniciales" }
  ]
}
```

Respuesta **200** cuando ningún protocolo la responde (siempre exactamente este texto y sin fuentes):

```json
{
  "respuesta": "No existe un protocolo disponible en el corpus oficial para esta pregunta.",
  "fuentes": []
}
```

Respuesta **503** cuando la IA no está disponible (modo degradado). Trae el header **`Retry-After: 30`** (segundos):

```json
{
  "type": "about:blank",
  "title": "El asistente clínico no está disponible en este momento",
  "status": 503,
  "detail": "Intente de nuevo en unos minutos; mientras tanto, consulte los protocolos clínicos directamente",
  "instance": "/asistente/consulta",
  "codigo": "AST-002"
}
```

### `GET /asistente/estado-urgencias`

Respuesta **200** (siempre trae los cinco niveles, aunque estén en cero):

```json
{
  "totalActivos": 23,
  "porNivel": { "CRITICO": 13, "URGENTE": 2, "URGENTE_2": 2, "NO_URGENTE": 5, "NO_URGENTE_2": 1 },
  "actualizadoEn": "2026-11-26T22:06:18.373572Z"
}
```

- Activo es todo paciente que no está en `ALTA` (registrado o en consulta).
- `actualizadoEn` es el momento del último evento aplicado: sirve para saber qué tan fresco está el dato. Es `null`
  si todavía no llega ningún evento.

## 7. Datos de prueba (preguntas)

Están en [`clinical-assistant-service/docs/pruebas/`](docs/pruebas). Protocolos del corpus:

- **PROTO-001**, crisis hipertensiva: Definición, Medidas iniciales y Contraindicaciones.
- **PROTO-002**, sepsis (qSOFA): Criterios y Acciones iniciales.
- **PROTO-003**, deshidratación pediátrica: Clasificación y Rehidratación.

| Archivo | Pregunta | Con llave de IA | Sin llave (modo degradado) |
|---|---|---|---|
| `consulta-crisis-hipertensiva.json` | ¿Cuáles son las medidas iniciales ante una crisis hipertensiva? | 200 con contenido de **PROTO-001** y su fuente | 503 `AST-002` |
| `consulta-sepsis.json` | ¿Cuándo se administra el primer antibiótico en sepsis? | 200 con contenido de **PROTO-002** ("en la primera hora") y su fuente | 503 `AST-002` |
| `consulta-deshidratacion.json` | ¿Cómo se rehidrata a un niño con deshidratación severa? | 200 con contenido de **PROTO-003** y su fuente | 503 `AST-002` |
| `consulta-diabetes-sin-protocolo.json` | ¿Cuál es el tratamiento de primera línea para la diabetes tipo 2? | 200 con el **aviso de que no existe protocolo** y `fuentes: []` | **Igual**: 200 con el aviso (no necesita a la IA) |
| `consulta-invalida-corta.json` | `?` | 400 `AST-001` | 400 `AST-001` |

Las tres primeras son las del enunciado más una del tercer protocolo; la de diabetes es el caso "sin coincidencia,
sin alucinación".

## 8. Casos de uso paso a paso

### CU-01 · Pregunta con protocolo (RF-04 A, RN-05)

Con la llave en `.env`:

```powershell
curl.exe -X POST http://localhost:8082/asistente/consulta -H "X-API-Key: local-dev-assistant-key-0001" -H "Content-Type: application/json" --data-binary "@clinical-assistant-service/docs/pruebas/consulta-sepsis.json"
```

**Esperado:** 200, una respuesta corta en español basada solo en PROTO-002 y `fuentes` con
`{"protocolo":"PROTO-002","seccion":"Acciones iniciales"}`.

### CU-02 · Pregunta sin protocolo (RN-05)

```powershell
curl.exe -X POST http://localhost:8082/asistente/consulta -H "X-API-Key: local-dev-assistant-key-0001" -H "Content-Type: application/json" --data-binary "@clinical-assistant-service/docs/pruebas/consulta-diabetes-sin-protocolo.json"
```

**Esperado:** 200 con exactamente
`{"respuesta":"No existe un protocolo disponible en el corpus oficial para esta pregunta.","fuentes":[]}`. Funciona
con o sin llave: como ningún protocolo se parece a la pregunta, **ni siquiera se llama a la IA**.

### CU-03 · Modo degradado (resiliencia)

Sin llave en `.env` (o con una llave inválida), repite CU-01. **Esperado:** 503 `AST-002` con el header
`Retry-After: 30`. Para ver el header:

```powershell
curl.exe -i -X POST http://localhost:8082/asistente/consulta -H "X-API-Key: local-dev-assistant-key-0001" -H "Content-Type: application/json" --data-binary "@clinical-assistant-service/docs/pruebas/consulta-sepsis.json"
```

Con la IA caída, el asistente reintenta una vez las fallas pasajeras y, si siguen, abre el circuit breaker. Desde ahí
responde 503 al instante sin llamar a la IA durante 30 segundos, y luego vuelve a probar. Nunca deja una llamada
colgada más de 15 segundos por intento. El servicio sigue `UP` y el triaje no se entera.

### CU-04 · Pregunta inválida

Manda `consulta-invalida-corta.json`. **Esperado:** 400:

```json
{
  "type": "about:blank",
  "title": "La pregunta no es válida",
  "status": 400,
  "detail": "la pregunta debe tener entre 3 y 1000 caracteres",
  "instance": "/asistente/consulta",
  "codigo": "AST-001",
  "errores": [{ "campo": "pregunta", "mensaje": "la pregunta debe tener entre 3 y 1000 caracteres" }]
}
```

### CU-05 · Estado de urgencias en tiempo real (RF-04 B)

1. Mira el estado actual:

   ```powershell
   curl.exe http://localhost:8082/asistente/estado-urgencias -H "X-API-Key: local-dev-assistant-key-0001"
   ```

2. Registra un paciente en el triaje (por ejemplo E1, CRITICO; ver el
   [README del triaje](../triage-service/README.md#7-datos-de-prueba-pacientes-e1-a-e6)).
3. Vuelve a mirar el estado: `CRITICO` y `totalActivos` subieron en **1** (tarda menos de un segundo).
4. Pasa ese paciente a `EN_CONSULTA`: el estado **no cambia** (sigue activo).
5. Dale el `ALTA`: `CRITICO` y `totalActivos` bajan en **1**.

El asistente nunca llama al triaje: todo llega por los eventos de `triaje.eventos`. Si el mismo evento llega dos
veces, cuenta una sola vez; si llega uno viejo después de uno nuevo, se ignora.

### CU-06 · Redis caído

```powershell
docker compose stop redis
curl.exe http://localhost:8082/asistente/estado-urgencias -H "X-API-Key: local-dev-assistant-key-0001"
```

**Esperado:** 503 `AST-003`
(`"detail":"Intente de nuevo en unos segundos; el registro de pacientes del triaje no se ve afectado"`). El servicio
sigue `UP` y las consultas a protocolos siguen funcionando. Al volver:

```powershell
docker compose start redis
```

El estado vuelve con los mismos números (Redis guarda en disco).

### CU-07 · Redis pierde todo y el asistente lo reconstruye

```powershell
docker compose exec redis redis-cli FLUSHALL
docker compose restart clinical-assistant-service
```

Justo después del `FLUSHALL` el estado sale en cero. Después del reinicio, el asistente ve que no hay proyección, lee
`triaje.eventos` **desde el principio** y vuelve a tener los mismos números que antes. Probado: 23 activos antes,
0 tras el borrado y 23 después de reconstruir.

### CU-08 · Seguridad (API key)

Llama a cualquier ruta sin `X-API-Key`. **Esperado:** 401 `AST-005`
(`"detail":"Envíe la API key en el header X-API-Key"`).

## 9. Cómo evita inventar respuestas

La regla RN-05 se cumple con tres filtros; si cualquiera falla, no sale contenido clínico:

1. **Búsqueda en el corpus.** La pregunta se compara con cada sección de los protocolos (búsqueda semántica con un
   modelo de embeddings multilingüe que corre dentro del servicio). Si ninguna sección se parece lo suficiente
   (similitud menor a 0.50), se responde el aviso de "no existe protocolo" **sin llamar a la IA**.
2. **La IA solo ve esas secciones.** Claude recibe únicamente los fragmentos encontrados, con la instrucción de usar
   solo eso y de decir si no alcanza. Responde en un formato estructurado: si responde o no, el texto y qué
   fragmentos usó.
3. **Se validan las citas.** Si la IA cita un fragmento que no se le dio, o no cita ninguno, la respuesta se
   **descarta** y se devuelve el aviso. Las `fuentes` de la respuesta salen de los fragmentos citados, nunca del
   texto de la IA.

Más detalle en [`docs/adr/0002-rag-del-asistente.md`](../docs/adr/0002-rag-del-asistente.md) y
[`docs/adr/0003-modo-degradado-del-asistente.md`](../docs/adr/0003-modo-degradado-del-asistente.md).

## 10. Códigos de error

| Código | HTTP | Cuándo |
|---|---|---|
| `AST-001` | 400 | La pregunta falta, está vacía o no tiene entre 3 y 1000 caracteres |
| `AST-002` | 503 | La IA no está disponible (modo degradado); trae `Retry-After: 30` |
| `AST-003` | 503 | Redis no está disponible para el estado de urgencias |
| `AST-004` | 400 | Solicitud mal formada (por ejemplo, JSON roto) |
| `AST-005` | 401 | Falta la API key o no es válida |
| `AST-006` | 404 | La ruta o el método no existen |
| `AST-999` | 500 | Error inesperado (no muestra detalles internos) |

## 11. Ver por dentro: métricas y trazas

**Tableros en Grafana:** abre <http://localhost:3000> y, con el botón **Otros tableros**, elige **VidaCruz · Asistente clínico**. Muestra:

- las preguntas respondidas, con fuentes y en modo degradado;
- el estado del circuit breaker;
- las respuestas por resultado;
- la latencia de cada intento a la IA (p50 y p95);
- los eventos del triaje procesados;
- el lag de Kafka.

Se actualiza cada 5 segundos. Sin `ANTHROPIC_API_KEY`, los paneles de latencia de la IA quedan vacíos (no hubo llamadas). El tablero **VidaCruz · Servicios** muestra el tráfico HTTP, los errores y los recursos de los dos servicios.

**Métricas en crudo** en <http://localhost:8082/prometheus>. Busca:

- `assistant_answers_total{outcome=...}`: preguntas por resultado (`answered`, `no_matching_protocol`,
  `declined_by_model`, `unsupported_answer`, `unavailable`).
- `assistant_llm_latency_seconds{outcome=...}`: cuánto tarda cada intento a la IA (`success`, `timeout`,
  `rate_limited`, `server_error`, `rejected`, `network`, `other`).
- `assistant_urgency_events_total{result=...}`: eventos del triaje aplicados (`applied`), ignorados por viejos
  (`ignored_older`) o saltados por ilegibles (`skipped`).
- `kafka_consumer_fetch_manager_records_lag_max`: cuántos eventos le faltan por leer al asistente.
- `resilience4j_circuitbreaker_state`: estado del circuit breaker de la IA.

**Trazas** en <http://localhost:16686> (Jaeger). Cuando registras un paciente en el triaje, la misma traza muestra
la solicitud HTTP del triaje y, unos milisegundos después, al asistente procesando su evento
(`triaje.eventos receive`).

**Logs** (JSON, con `traceId` para cruzarlos con el triaje; nunca guardan el texto de la pregunta):

```powershell
docker compose logs clinical-assistant-service
```

## 12. Problemas comunes

| Síntoma | Solución |
|---|---|
| Todas las preguntas con protocolo dan 503 | No hay `ANTHROPIC_API_KEY` en `.env`, o la llave no es válida. Revísala y ejecuta `docker compose up -d clinical-assistant-service` |
| 400 `AST-004` al escribir la pregunta a mano en la consola | La consola de Windows daña las tildes y los `¿`: usa los archivos de `docs/pruebas` con `--data-binary "@archivo"`, o usa Swagger |
| El estado de urgencias sale en cero | Todavía no registraste pacientes en el triaje, o acabas de borrar Redis (CU-07) |
| El asistente tarda en quedar `UP` | Es normal la primera vez: carga el modelo de búsqueda. Espera 1 o 2 minutos |
| En PowerShell `curl` da un error raro de parámetros | Escribe `curl.exe`, no `curl` |
| 401 en todas las llamadas | Falta `X-API-Key: local-dev-assistant-key-0001` (ojo: es distinta a la del triaje) |

## 13. Apagar y limpiar

```powershell
docker compose down
```

Apaga todo y conserva los datos. Para borrar también los datos (base del triaje y Redis):

```powershell
docker compose down -v
```

## 14. Pruebas automáticas y estructura del código

Requiere Java 21 y Docker abierto (las pruebas levantan Kafka y Redis reales con Testcontainers). La IA se simula con
WireMock, así que **no hace falta llave**:

```powershell
cd clinical-assistant-service
./gradlew build
```

Corre unas 190 pruebas, entre ellas:

- las tres consultas del enunciado;
- respuestas que citan fragmentos inexistentes;
- la IA caída: reintento, circuit breaker y 503;
- eventos duplicados, viejos o dañados;
- la reconstrucción desde Kafka.

Exige al menos 90 % de cobertura por módulo. La primera vez descarga el modelo de búsqueda.

Arquitectura limpia (scaffold de Bancolombia):

| Módulo | Qué hay |
|---|---|
| `domain/model` | Pregunta, fragmentos, respuesta y sus reglas (RN-05), estado de urgencias y los puertos |
| `domain/usecase` | Responder una pregunta, proyectar un evento del triaje y leer el estado |
| `infrastructure/entry-points/reactive-web` | API HTTP con WebFlux (RouterFunction), API key, errores RFC 9457, métricas de respuestas |
| `infrastructure/entry-points/kafka-consumer` | Consumidor reactivo de `triaje.eventos` (orden por paciente, reconstrucción) |
| `infrastructure/driven-adapters/protocol-corpus` | Corpus PROTO-001 a 003 y búsqueda semántica (Spring AI + ONNX) |
| `infrastructure/driven-adapters/claude-llm` | Llamada a Claude con el SDK oficial, resiliencia (Resilience4j) y métricas de latencia |
| `infrastructure/driven-adapters/redis` | Proyección del estado de urgencias en Redis (scripts Lua atómicos) |
| `applications/app-service` | Arranque y configuración |
| `deployment/` | `Dockerfile` y manifests de Kubernetes |
