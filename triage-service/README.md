# triage-service — Servicio de Triage de VidaCruz

Registra a los pacientes que llegan a urgencias, los **clasifica solo** por sus signos vitales (5 niveles), vigila
cuánto esperan y, si alguien espera más de lo permitido (su **SLA**), lo **escala** y **alerta** al equipo médico.
Todo queda **auditado** y cada cambio se publica como **evento en Kafka** para el asistente clínico.

[![Registro, evento y escalamiento de punta a punta](../docs/diagramas/registro-y-escalamiento.svg)](../docs/diagramas/registro-y-escalamiento.svg)

> Este servicio trabaja junto con [`clinical-assistant-service`](../clinical-assistant-service/README.md). Los dos
> se levantan juntos con **un solo comando** desde la raíz del repositorio.

---

## Índice

1. [Lo que necesitas instalar](#1-lo-que-necesitas-instalar)
2. [Descargar el proyecto](#2-descargar-el-proyecto)
3. [Levantar todo con un comando](#3-levantar-todo-con-un-comando)
4. [Comprobar que está vivo](#4-comprobar-que-está-vivo)
5. [Dos formas de probar: navegador o consola](#5-dos-formas-de-probar-navegador-o-consola)
6. [Firmas de la API](#6-firmas-de-la-api)
7. [Datos de prueba (pacientes E1 a E6)](#7-datos-de-prueba-pacientes-e1-a-e6)
8. [Casos de uso paso a paso](#8-casos-de-uso-paso-a-paso)
9. [Reglas de negocio que puedes comprobar](#9-reglas-de-negocio-que-puedes-comprobar)
10. [Códigos de error](#10-códigos-de-error)
11. [Ver por dentro: auditoría, eventos, métricas y trazas](#11-ver-por-dentro-auditoría-eventos-métricas-y-trazas)
12. [Problemas comunes](#12-problemas-comunes)
13. [Apagar y limpiar](#13-apagar-y-limpiar)
14. [Pruebas automáticas y estructura del código](#14-pruebas-automáticas-y-estructura-del-código)

---

## 1. Lo que necesitas instalar

| Programa | Para qué | Obligatorio |
|---|---|---|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Levanta todo: base de datos, Kafka, Redis y los dos servicios | **Sí** |
| [Git](https://git-scm.com/downloads) | Descargar el proyecto | **Sí** |
| Un navegador | Probar con Swagger (botones, sin escribir comandos) | Recomendado |
| Java 21 | Solo si quieres correr las pruebas automáticas | No |

En Docker Desktop: **Settings → Resources → Memory**, deja al menos **8 GB**. En Windows, Docker Desktop debe usar
WSL 2 (viene así por defecto).

No necesitas instalar Java, Gradle, PostgreSQL ni Kafka: todo se compila y corre dentro de Docker.

## 2. Descargar el proyecto

Abre una terminal (en Windows, **PowerShell**) y escribe:

```powershell
git clone https://github.com/rgranados1995/vidacruz-triage-platform.git
cd vidacruz-triage-platform
```

**Todos los comandos de este README se escriben desde esa carpeta** (la raíz del repositorio), no desde
`triage-service`.

**Opcional:** si quieres que el asistente clínico responda con IA de verdad, copia el archivo de ejemplo y pon tu
llave de Anthropic en `ANTHROPIC_API_KEY`. Sin llave todo funciona igual y el asistente responde en "modo degradado".

```powershell
Copy-Item .env.example .env
notepad .env
```

## 3. Levantar todo con un comando

**Modo demo (recomendado para probar):** el SLA del nivel CRITICO baja a **1 minuto** y el monitor revisa cada
**5 segundos**, así ves el escalamiento y la alerta sin esperar.

```powershell
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

**Modo normal (SLA reales del enunciado: 5, 10, 30, 60 y 120 minutos):**

```powershell
docker compose up -d --build
```

La **primera vez tarda entre 10 y 15 minutos** porque descarga imágenes, dependencias y el modelo de búsqueda del
asistente. Las siguientes veces tarda menos de un minuto. Necesita internet solo la primera vez.

Qué se levanta:

| Contenedor | Puerto en tu máquina | Qué es |
|---|---|---|
| `triage-service` | **8081** | Este servicio |
| `clinical-assistant-service` | **8082** | Asistente clínico |
| `postgres` | 5432 | Base de datos del triaje (pacientes, auditoría, alertas) |
| `kafka` | 29092 | Eventos `triaje.eventos` |
| `redis` | 6379 | Estado de urgencias del asistente |
| `jaeger` | **16686** | Trazas (para ver una solicitud de punta a punta) |
| `grafana` | **3000** | Tableros de métricas, ya configurados |
| `prometheus` | 9090 | Guarda las métricas de los dos servicios |

## 4. Comprobar que está vivo

```powershell
docker compose ps
```

Todos deben decir `running` (o `healthy`). Luego abre en el navegador:

- <http://localhost:8081/health/readiness> → debe mostrar `{"status":"UP"}`
- <http://localhost:8082/health/readiness> → debe mostrar `{"status":"UP"}`

Si dice que no conecta, espera un minuto más: el servicio todavía está arrancando.

## 5. Dos formas de probar: navegador o consola

### La llave (API key)

Todas las rutas `/triage/**` piden el header **`X-API-Key`**. En local la llave es:

```text
local-dev-api-key-0001
```

(Puedes cambiarla con `TRIAGE_API_KEY` en `.env`. Sin la llave la respuesta es **401**.)

### Opción A — Navegador con Swagger (la más fácil)

1. Abre <http://localhost:8081/swagger-ui.html>
2. Haz clic en el botón **Authorize** (arriba a la derecha), pega `local-dev-api-key-0001` y pulsa **Authorize** y
   luego **Close**.
3. Abre cualquier operación (por ejemplo `POST /triage/pacientes`), pulsa **Try it out**, pega el JSON de un paciente
   de la [sección 7](#7-datos-de-prueba-pacientes-e1-a-e6) y pulsa **Execute**.
4. Abajo ves la respuesta. Copia el `id` del paciente para las siguientes operaciones.

### Opción C — Postman (todo listo para importar)

En Postman: **Import** → elige el archivo [`docs/postman/VidaCruz.postman_collection.json`](../docs/postman/VidaCruz.postman_collection.json) de la raíz del repositorio. Trae las pruebas de los dos servicios en orden (carpetas 0 a 3), con las llaves ya puestas, los `id` guardados solos entre una petición y otra y una verificación en cada respuesta. Puedes ir petición por petición con **Send** o correr todo con **Run collection** (desmarca la carpeta 9, que es el stream en vivo y no termina nunca). La carpeta 2 necesita el modo demo y espera sola el minuto del escalamiento.

### Opción B — Consola con curl

En **Windows PowerShell** escribe siempre **`curl.exe`** (con `.exe`): `curl` a secas es otro comando de PowerShell.
En Mac o Linux escribe `curl`. Los cuerpos JSON están listos en archivos dentro de
[`triage-service/docs/pruebas/`](docs/pruebas), así no tienes que escribir JSON ni pelear con las tildes.

```powershell
curl.exe -X POST http://localhost:8081/triage/pacientes -H "X-API-Key: local-dev-api-key-0001" -H "Content-Type: application/json" --data-binary "@triage-service/docs/pruebas/pacientes/e1-critico-dolor-toracico.json"
```

El `@` delante del nombre del archivo le dice a curl "manda el contenido de este archivo".

## 6. Firmas de la API

Base: `http://localhost:8081` · Header obligatorio: `X-API-Key: local-dev-api-key-0001` · Formato: JSON ·
Errores: [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`) con un `codigo` `TRG-xxx`.

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| `POST` | `/triage/pacientes` | Registra y clasifica un paciente (RF-01) | 201, 400, 401 |
| `GET` | `/triage/pacientes/{id}` | Detalle del paciente, su estado, si está escalado y cuánto lleva en el estado (RF-02) | 200, 400, 401, 404 |
| `PATCH` | `/triage/pacientes/{id}/estado` | Cambia el estado clínico (RF-02) | 200, 400, 401, 404, 409 |
| `GET` | `/triage/reportes/operacion?desde=...&hasta=...` | Reporte por hora (RF-05) | 200, 400, 401 |
| `GET` | `/triage/alertas` | Alertas de escalamiento activas (RF-06) | 200, 401 |
| `GET` | `/triage/alertas/stream` | Alertas en tiempo real (Server-Sent Events) | 200, 401 |
| `POST` | `/triage/alertas/{id}/reconocimiento` | Un médico toma una alerta pendiente | 200, 400, 401, 404, 409 |

### `POST /triage/pacientes` — registrar

Cuerpo (todos los campos son obligatorios):

| Campo | Tipo | Rango válido |
|---|---|---|
| `nombreCompleto` | texto | 3 a 100 caracteres |
| `edad` | entero | 0 a 120 años |
| `frecuenciaCardiaca` | entero | 20 a 250 lpm |
| `presionSistolica` | entero | 60 a 260 mmHg |
| `presionDiastolica` | entero | 30 a 160 mmHg, y **menor que la sistólica** |
| `spo2` | entero | 70 a 100 % |
| `temperatura` | decimal | 34.0 a 42.0 °C |
| `dolor` | entero | 0 a 10 |
| `gcs` | entero | 3 a 15 (escala de Glasgow) |
| `quejaPrincipal` | texto | hasta 500 caracteres |

```json
{
  "nombreCompleto": "Ana María Pérez",
  "edad": 54,
  "frecuenciaCardiaca": 130,
  "presionSistolica": 180,
  "presionDiastolica": 110,
  "spo2": 97,
  "temperatura": 36.8,
  "dolor": 8,
  "gcs": 15,
  "quejaPrincipal": "Dolor torácico"
}
```

Respuesta **201** (el header `Location` apunta al detalle del paciente):

```json
{
  "id": "86a9f3d4-ebb6-4712-9be3-75abaca27f39",
  "nivel": "CRITICO",
  "slaMinutos": 5,
  "estado": "REGISTRADO",
  "fechaRegistro": "2026-11-26T22:05:04.050032Z"
}
```

`nivel` es uno de `CRITICO`, `URGENTE`, `URGENTE_2`, `NO_URGENTE`, `NO_URGENTE_2`. En modo demo, `slaMinutos` de
CRITICO es `1`.

### `GET /triage/pacientes/{id}` — consultar

Respuesta **200**:

```json
{
  "id": "86a9f3d4-ebb6-4712-9be3-75abaca27f39",
  "nombreCompleto": "Ana María Pérez",
  "edad": 54,
  "frecuenciaCardiaca": 130,
  "presionSistolica": 180,
  "presionDiastolica": 110,
  "spo2": 97,
  "temperatura": 36.8,
  "dolor": 8,
  "gcs": 15,
  "quejaPrincipal": "Dolor torácico",
  "nivel": "CRITICO",
  "slaMinutos": 1,
  "estado": "EN_CONSULTA",
  "escalado": true,
  "fechaRegistro": "2026-11-26T22:05:04.050032Z",
  "fechaCambioEstado": "2026-11-26T22:06:18.373572Z",
  "fechaInicioConsulta": "2026-11-26T22:06:18.373572Z",
  "tiempoEnEstadoActual": "PT13.001324S"
}
```

`tiempoEnEstadoActual` es una duración ISO 8601: `PT13.0S` son 13 segundos y `PT1M14S` es 1 minuto y 14 segundos.

### `PATCH /triage/pacientes/{id}/estado` — cambiar de estado

Cuerpo: `{ "estado": "EN_CONSULTA" }` o `{ "estado": "ALTA" }`. Responde **200** con el mismo detalle de arriba en
el estado nuevo.

```text
REGISTRADO ──► EN_CONSULTA ──► ALTA
     └──────────────────────────▲   (alta directa)
```

Cualquier otro cambio (por ejemplo `ALTA → EN_CONSULTA`) responde **409**.

### `GET /triage/reportes/operacion` — reporte por hora

Parámetros `desde` y `hasta`: fecha y hora ISO 8601 **con zona**. Usa `Z` (hora UTC) para no complicarte; si usas
un desfase como `-05:00` está bien, pero un `+` hay que escribirlo `%2B` en la URL. El rango máximo es de 31 días.

```text
/triage/reportes/operacion?desde=2026-11-26T21:00:00Z&hasta=2026-11-26T23:00:00Z
```

Respuesta **200** (una fila por hora del rango, más el resumen de todo el rango):

```json
{
  "desde": "2026-11-26T21:00:00Z",
  "hasta": "2026-11-26T23:00:00Z",
  "horas": [
    {
      "hora": "2026-11-26T22:00:00Z",
      "totalRegistrados": 6,
      "porNivel": { "CRITICO": 2, "URGENTE": 1, "URGENTE_2": 1, "NO_URGENTE": 1, "NO_URGENTE_2": 1 },
      "atendidos": 2,
      "esperaPromedioMinutos": 0.74,
      "esperaP95Minutos": 1.19
    }
  ],
  "resumen": {
    "totalRegistrados": 7,
    "porNivel": { "CRITICO": 3, "URGENTE": 1, "URGENTE_2": 1, "NO_URGENTE": 1, "NO_URGENTE_2": 1 },
    "atendidos": 2,
    "esperaPromedioMinutos": 0.74,
    "esperaP95Minutos": 1.19
  }
}
```

- `atendidos`: cuántos entraron a consulta. La espera es desde el registro hasta la **primera** entrada a consulta.
- `esperaPromedioMinutos` y `esperaP95Minutos` son `null` en una hora sin pacientes atendidos.
- Todo se calcula en **una sola consulta SQL** (funciones de ventana, `FILTER`, `PERCENTILE_CONT`, `GROUPING SETS`).

### `GET /triage/alertas` — alertas activas

Respuesta **200**: lista de alertas `PENDIENTE` o `RECONOCIDA` (las resueltas no aparecen):

```json
[
  {
    "id": 16,
    "pacienteId": "5872ed13-987c-4519-a2c2-871ff271e8dd",
    "nivel": "CRITICO",
    "slaMinutos": 1,
    "fechaRegistro": "2026-11-26T22:05:04.314094Z",
    "fechaEscalamiento": "2026-11-26T22:06:04.903748Z",
    "minutosEsperaAlEscalar": 1,
    "estado": "PENDIENTE",
    "reconocidaPor": null,
    "fechaReconocimiento": null,
    "fechaResolucion": null,
    "actualizadaEn": "2026-11-26T22:06:04.903748Z"
  }
]
```

Estados de una alerta: `PENDIENTE` (nadie la tomó) → `RECONOCIDA` (un médico la tomó) → `RESUELTA` (el paciente entró
a consulta o fue dado de alta). La alerta no lleva el nombre del paciente: el médico abre el paciente por su id.

### `GET /triage/alertas/stream` — alertas en vivo

Server-Sent Events (`Accept: text/event-stream`). Al conectarte recibes primero las alertas activas y luego cada
cambio en el momento en que ocurre. Cada 15 segundos llega un `:latido` para que la conexión no se corte.

```text
event:alerta
data:{"id":16,"pacienteId":"5872ed13-...","nivel":"CRITICO","estado":"PENDIENTE",...}

:latido
```

### `POST /triage/alertas/{id}/reconocimiento` — tomar una alerta

Cuerpo: `{ "responsable": "Dra. Rojas" }`. Respuesta **200** con la alerta en `RECONOCIDA`. Si ya estaba reconocida o
resuelta: **409** con el motivo.

## 7. Datos de prueba (pacientes E1 a E6)

Son los pacientes de ejemplo del enunciado, listos en
[`triage-service/docs/pruebas/pacientes/`](docs/pruebas/pacientes). La última columna es lo que el sistema debe
responder.

| # | Archivo | FC | P. sist | P. diast | SpO2 | Temp | Dolor | GCS | Queja | Nivel esperado | SLA |
|---|---|---|---|---|---|---|---|---|---|---|---|
| E1 | `e1-critico-dolor-toracico.json` | 130 | 180 | 110 | 97 | 36.8 | 8 | 15 | Dolor torácico | **CRITICO** | 5 min |
| E2 | `e2-urgente-dificultad-respirar.json` | 95 | 150 | 95 | 91 | 37.0 | 4 | 15 | Dificultad para respirar | **URGENTE** | 10 min |
| E3 | `e3-urgente2-fiebre.json` | 88 | 130 | 85 | 98 | 38.2 | 6 | 15 | Fiebre | **URGENTE_2** | 30 min |
| E4 | `e4-no-urgente-herida.json` | 72 | 120 | 80 | 99 | 36.5 | 2 | 15 | Herida por cortar | **NO_URGENTE** | 60 min |
| E5 | `e5-no-urgente2-recetario.json` | 70 | 115 | 75 | 99 | 36.4 | 0 | 15 | Renovación de recetario | **NO_URGENTE_2** | 120 min |
| E6 | `e6-critico-inconsciente.json` | 35 | 80 | 50 | 85 | 35.9 | 0 | 6 | Inconsciente | **CRITICO** | 5 min |
| — | `invalido-fuera-de-rango.json` | 300 | 120 | 130 | 99 | 36.5 | 2 | 15 | Control | **400** con 4 errores | — |

**Registrar los seis de una vez** (PowerShell):

```powershell
Get-ChildItem triage-service\docs\pruebas\pacientes\e*.json | ForEach-Object { curl.exe -s -X POST http://localhost:8081/triage/pacientes -H "X-API-Key: local-dev-api-key-0001" -H "Content-Type: application/json" --data-binary "@$($_.FullName)"; "" }
```

Resultado real:

```text
{"id":"86a9f3d4-...","nivel":"CRITICO","slaMinutos":1,"estado":"REGISTRADO","fechaRegistro":"2026-11-26T22:05:04.050032Z"}
{"id":"585e8193-...","nivel":"URGENTE","slaMinutos":10,"estado":"REGISTRADO","fechaRegistro":"2026-11-26T22:05:04.140626Z"}
{"id":"23274f17-...","nivel":"URGENTE_2","slaMinutos":30,"estado":"REGISTRADO","fechaRegistro":"2026-11-26T22:05:04.187600Z"}
{"id":"19b904bb-...","nivel":"NO_URGENTE","slaMinutos":60,"estado":"REGISTRADO","fechaRegistro":"2026-11-26T22:05:04.233148Z"}
{"id":"287845bd-...","nivel":"NO_URGENTE_2","slaMinutos":120,"estado":"REGISTRADO","fechaRegistro":"2026-11-26T22:05:04.273142Z"}
{"id":"5872ed13-...","nivel":"CRITICO","slaMinutos":1,"estado":"REGISTRADO","fechaRegistro":"2026-11-26T22:05:04.314094Z"}
```

(En modo demo el SLA de CRITICO sale en `1`; en modo normal, `5`.)

Otros archivos: [`estados/`](docs/pruebas/estados) (`en-consulta.json`, `alta.json`, `registrado.json`) y
[`alertas/reconocimiento.json`](docs/pruebas/alertas/reconocimiento.json).

## 8. Casos de uso paso a paso

En los comandos, cambia `<id>` por el `id` que te devolvió el registro y `<idAlerta>` por el `id` de la alerta. En
PowerShell puedes guardarlo en una variable para no copiarlo a mano:

```powershell
$k = "X-API-Key: local-dev-api-key-0001"
$p = curl.exe -s -X POST http://localhost:8081/triage/pacientes -H $k -H "Content-Type: application/json" --data-binary "@triage-service/docs/pruebas/pacientes/e4-no-urgente-herida.json" | ConvertFrom-Json
$p.id
```

Después usas `$p.id` en lugar de `<id>` (por ejemplo `http://localhost:8081/triage/pacientes/$($p.id)`).

### CU-01 · Registrar y clasificar (RF-01, RN-01, RN-02)

Registra E1 (ver [sección 5](#opción-b--consola-con-curl)). **Esperado:** 201, `"nivel":"CRITICO"`,
`"estado":"REGISTRADO"`. Haz lo mismo con E2 a E6 y compara con la tabla de la sección 7.

### CU-02 · Consultar un paciente (RF-02)

```powershell
curl.exe http://localhost:8081/triage/pacientes/<id> -H "X-API-Key: local-dev-api-key-0001"
```

**Esperado:** 200 con todos los datos, `"escalado":false` y `tiempoEnEstadoActual` creciendo cada vez que lo llamas.

### CU-03 · Flujo normal: consulta y alta (RF-02, RN-04)

```powershell
curl.exe -X PATCH http://localhost:8081/triage/pacientes/<id>/estado -H "X-API-Key: local-dev-api-key-0001" -H "Content-Type: application/json" --data-binary "@triage-service/docs/pruebas/estados/en-consulta.json"
curl.exe -X PATCH http://localhost:8081/triage/pacientes/<id>/estado -H "X-API-Key: local-dev-api-key-0001" -H "Content-Type: application/json" --data-binary "@triage-service/docs/pruebas/estados/alta.json"
```

**Esperado:** 200 y 200. Después de la consulta, `fechaInicioConsulta` tiene valor; después del alta,
`"estado":"ALTA"`.

### CU-04 · Alta directa (RF-02)

Registra E5 y, sin pasar por consulta, mándale `alta.json`. **Esperado:** 200, `"estado":"ALTA"` y
`"fechaInicioConsulta":null`.

### CU-05 · Transición no permitida (RF-02)

A un paciente que ya está en `ALTA`, mándale `en-consulta.json` otra vez. **Esperado:** 409:

```json
{
  "type": "about:blank",
  "title": "Transición de estado no permitida",
  "status": 409,
  "detail": "de \"alta\" a \"en consulta\"",
  "instance": "/triage/pacientes/19b904bb-e9a9-4c2f-925b-883f1ccab20a/estado",
  "codigo": "TRG-003"
}
```

Mandar `registrado.json` a cualquier paciente también da 409 (nunca se vuelve a `REGISTRADO`).

### CU-06 · Datos inválidos (validaciones de RF-01)

```powershell
curl.exe -X POST http://localhost:8081/triage/pacientes -H "X-API-Key: local-dev-api-key-0001" -H "Content-Type: application/json" --data-binary "@triage-service/docs/pruebas/pacientes/invalido-fuera-de-rango.json"
```

**Esperado:** 400 con **todos** los campos inválidos a la vez, cada uno con su mensaje:

```json
{
  "type": "about:blank",
  "title": "Los datos del paciente no son válidos",
  "status": 400,
  "detail": "Revise los campos indicados en errores",
  "instance": "/triage/pacientes",
  "codigo": "TRG-001",
  "errores": [
    { "campo": "nombreCompleto", "mensaje": "El nombre completo debe tener entre 3 y 100 caracteres" },
    { "campo": "edad", "mensaje": "La edad debe estar entre 0 y 120 años" },
    { "campo": "frecuenciaCardiaca", "mensaje": "La frecuencia cardíaca debe estar entre 20 y 250 lpm" },
    { "campo": "presionDiastolica", "mensaje": "La presión diastólica debe ser menor que la sistólica" }
  ]
}
```

### CU-07 · Escalamiento automático y alerta (RF-03, RN-03, RF-06)

Con el **modo demo** levantado:

1. Registra E1 (CRITICO, SLA de 1 minuto) y **no** lo pases a consulta.
2. Espera unos 70 segundos.
3. Consulta el paciente: **esperado** `"escalado":true` y sigue en `"estado":"REGISTRADO"`.
4. Mira las alertas activas: aparece una alerta `PENDIENTE` con su `pacienteId`.

```powershell
curl.exe http://localhost:8081/triage/alertas -H "X-API-Key: local-dev-api-key-0001"
```

El escalamiento ocurre **una sola vez** por paciente (RN-03): por más rondas que pasen, no hay una segunda alerta ni
un segundo evento. Un paciente que ya entró a consulta o recibió el alta **nunca** se escala.

### CU-08 · Ver las alertas en vivo (RF-06)

En una terminal deja abierto el stream:

```powershell
curl.exe -N http://localhost:8081/triage/alertas/stream -H "X-API-Key: local-dev-api-key-0001" -H "Accept: text/event-stream"
```

En **otra** terminal registra un paciente crítico (CU-07). En la primera terminal verás aparecer, sin hacer nada, el
`event:alerta` cuando se escale, luego otro cuando alguien la reconozca y otro cuando el paciente entre a consulta.
Para cerrar el stream pulsa `Ctrl+C`.

### CU-09 · Reconocer una alerta (RF-06)

```powershell
curl.exe -X POST http://localhost:8081/triage/alertas/<idAlerta>/reconocimiento -H "X-API-Key: local-dev-api-key-0001" -H "Content-Type: application/json" --data-binary "@triage-service/docs/pruebas/alertas/reconocimiento.json"
```

**Esperado:** 200 con `"estado":"RECONOCIDA"` y `"reconocidaPor":"Dra. Rojas"`. Si lo repites: 409 con
`"detail":"la alerta ya fue reconocida por Dra. Rojas"`. Cuando el paciente entra a consulta, la alerta pasa sola a
`RESUELTA` y desaparece de la lista de activas.

### CU-10 · Reporte de operación (RF-05)

Cambia las fechas por la hora en que registraste pacientes (en UTC; en Colombia es tu hora local + 5 horas):

```powershell
curl.exe "http://localhost:8081/triage/reportes/operacion?desde=2026-11-26T21:00:00Z&hasta=2026-11-26T23:00:00Z" -H "X-API-Key: local-dev-api-key-0001"
```

**Esperado:** 200 con una fila por hora, conteo por nivel, atendidos, espera promedio y p95. Si `desde` es posterior a
`hasta`: 400 `TRG-007`. Si la fecha no trae zona (sin `Z`): 400 `TRG-004` con un mensaje que explica el formato.

### CU-11 · Seguridad (API key)

Repite cualquier llamada **sin** el header `X-API-Key`. **Esperado:** 401 `TRG-005`
(`"detail":"Envíe la API key en el header X-API-Key"`).

## 9. Reglas de negocio que puedes comprobar

**RN-01 · Clasificación** (se evalúa en orden; gana el nivel más grave que se cumpla):

| Nivel | Se cumple si (cualquiera) |
|---|---|
| CRITICO | FC < 40 o FC > 120, SpO2 < 90, P. sistólica < 90, GCS ≤ 8 |
| URGENTE | 90 ≤ SpO2 ≤ 93, 90 ≤ P. sistólica ≤ 100, Temp ≥ 38.5, 9 ≤ GCS ≤ 13 |
| URGENTE_2 | 90 ≤ FC ≤ 120, 38.0 ≤ Temp < 38.5, Dolor ≥ 7 |
| NO_URGENTE | Ninguna de las anteriores |
| NO_URGENTE_2 | NO_URGENTE y la queja completa es una de las administrativas: documentos, recolección de resultados, renovación de recetario, vacunación, seguimiento (sin importar mayúsculas, tildes ni espacios de más) |

**RN-02 · SLA:** CRITICO 5 min, URGENTE 10, URGENTE_2 30, NO_URGENTE 60, NO_URGENTE_2 120. Se configuran con
`SLA_MINUTES_*` en `docker-compose.yml`, y el intervalo del monitor con `SLA_MONITOR_INTERVAL`.

**RN-03 · Escalamiento una sola vez**, nunca en consulta ni en alta. Funciona aunque haya varias réplicas del
servicio.

**RN-04 · Auditoría:** cada registro, entrada a consulta, escalamiento y alta queda guardado con fecha, paciente,
estado anterior, estado nuevo y tipo de evento. No hay ninguna ruta para borrarla, y la base de datos rechaza un
`UPDATE` o `DELETE` sobre ella.

## 10. Códigos de error

Todos los errores tienen la misma forma (`type`, `title`, `status`, `detail`, `instance`, `codigo` y, en errores de
datos, `errores` con `campo` y `mensaje`).

| Código | HTTP | Cuándo |
|---|---|---|
| `TRG-001` | 400 | Datos del paciente fuera de rango o faltantes (lista todos los campos) |
| `TRG-002` | 404 | No existe un paciente con ese id |
| `TRG-003` | 409 | Transición de estado no permitida |
| `TRG-004` | 400 | Solicitud mal formada: JSON roto, id que no es UUID, estado desconocido, fecha sin zona |
| `TRG-005` | 401 | Falta la API key o no es válida |
| `TRG-006` | 404 | La ruta o el método no existen (por ejemplo, un `DELETE`: no hay forma de borrar pacientes ni auditoría) |
| `TRG-007` | 400 | Rango del reporte inválido (`desde` no es anterior a `hasta` o supera 31 días) |
| `TRG-008` | 404 | No existe una alerta con ese id |
| `TRG-009` | 409 | La alerta ya fue reconocida o resuelta |
| `TRG-999` | 500 | Error inesperado (no muestra detalles internos) |

## 11. Ver por dentro: auditoría, eventos, métricas y trazas

**Auditoría de un paciente** (directo en PostgreSQL):

```powershell
docker compose exec postgres psql -U triage -d triage -c "SELECT event_type, previous_status, new_status, occurred_at FROM clinical_audit WHERE patient_id = '<id>' ORDER BY occurred_at"
```

```text
      event_type      | previous_status |   new_status    |          occurred_at
----------------------+-----------------+-----------------+-------------------------------
 REGISTERED           |                 | REGISTERED      | 2026-11-26 22:05:04.050032+00
 ESCALATED            | REGISTERED      | REGISTERED      | 2026-11-26 22:06:04.903748+00
 CONSULTATION_STARTED | REGISTERED      | IN_CONSULTATION | 2026-11-26 22:06:18.373572+00
```

**Eventos en Kafka** (`triaje.eventos`, formato CloudEvents 1.0 en JSON, la clave es el id del paciente). Se
publican desde un *outbox* en la misma transacción del cambio, así que nunca se pierde un evento. Este comando es
para PowerShell; en Git Bash antepón `MSYS_NO_PATHCONV=1` para que no cambie la ruta:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic triaje.eventos --from-beginning --property print.key=true
```

```json
{"id": "4106ed3f-...", "data": {"nivel": "CRITICO", "estado": "REGISTRADO", "escalado": true,
 "ocurridoEn": "2026-11-26T22:06:04.903748Z", "pacienteId": "86a9f3d4-...", "slaMinutos": 1,
 "fechaRegistro": "2026-11-26T22:05:04.050032Z", "estadoAnterior": "REGISTRADO"},
 "time": "2026-11-26T22:06:04.903748Z", "type": "paciente_escalado", "source": "/vidacruz/triage-service",
 "subject": "86a9f3d4-...", "specversion": "1.0", "datacontenttype": "application/json"}
```

(El orden de los campos puede variar: es JSON.)

Tipos: `paciente_registrado`, `paciente_en_consulta`, `paciente_escalado`, `paciente_alta`. Los eventos **no llevan
el nombre** del paciente. Pulsa `Ctrl+C` para salir.

**Tableros en Grafana** (lo más fácil): abre <http://localhost:3000>. No pide usuario: entras directo al tablero
**VidaCruz · Urgencias**. Arriba, el botón **Otros tableros** lleva a los demás:

| Tablero | Qué muestra |
|---|---|
| **VidaCruz · Urgencias** | Pacientes clasificados, escalamientos y alertas reconocidas y sin reconocer, por nivel y por minuto |
| **VidaCruz · Asistente clínico** | Respuestas por resultado, latencia de la IA, circuit breaker, eventos procesados y lag de Kafka |
| **VidaCruz · Servicios** | Peticiones por segundo, errores 4xx/5xx, latencia p95 por ruta, memoria, CPU, pool de PostgreSQL y Redis |

Se actualizan cada 5 segundos. Registra pacientes (o corre la colección de Postman) y míralos moverse. Arriba a la
derecha cambias el rango de tiempo. Prometheus, que guarda las métricas, está en <http://localhost:9090>.

**Métricas en crudo** en <http://localhost:8081/prometheus>. Busca:

- `triage_patients_classified_total{level=...}`: pacientes clasificados por nivel.
- `triage_patients_escalated_total{level=...}`: escalamientos.
- `triage_alerts_acknowledged_total{level=...}`: alertas reconocidas.

**Trazas** en <http://localhost:16686> (Jaeger): elige `triage-service` y pulsa **Find Traces**. Un registro muestra
la solicitud HTTP y, en la misma traza, cuando el asistente procesa su evento. Los logs de los dos servicios llevan el
mismo `traceId`:

```powershell
docker compose logs triage-service
```

## 12. Problemas comunes

| Síntoma | Solución |
|---|---|
| `port is already allocated` al levantar | Otro programa usa ese puerto (por ejemplo, un PostgreSQL instalado en 5432, u otro Grafana en 3000). Ciérralo o detén ese servicio |
| Un panel de Grafana dice *No data* | Todavía no hay tráfico de ese tipo (por ejemplo, la latencia de la IA sin `ANTHROPIC_API_KEY`), o el rango de tiempo es muy corto |
| El servicio no responde después de levantar | Espera 1 o 2 minutos y revisa `docker compose ps`; mira los logs con `docker compose logs triage-service` |
| Un contenedor se cae por memoria | Sube la memoria de Docker Desktop a 8 GB (sección 1) |
| En PowerShell `curl` da un error raro de parámetros | Escribe `curl.exe`, no `curl` |
| 400 `TRG-004` "cuerpo inválido" al escribir JSON a mano en la consola | La consola de Windows daña las tildes: usa los archivos de `docs/pruebas` con `--data-binary "@archivo"` o usa Swagger |
| 401 en todas las llamadas | Falta el header `X-API-Key: local-dev-api-key-0001` (o en `.env` pusiste otra llave) |
| El paciente no se escala | Levantaste el modo normal (SLA de 5 minutos para CRITICO): espera o usa el modo demo |

## 13. Apagar y limpiar

```powershell
docker compose down
```

Apaga todo y **conserva** los datos. Para borrar también la base de datos y empezar de cero:

```powershell
docker compose down -v
```

## 14. Pruebas automáticas y estructura del código

Requiere Java 21 y Docker abierto (las pruebas levantan PostgreSQL y Kafka reales con Testcontainers):

```powershell
cd triage-service
./gradlew build
```

Corre unas 370 pruebas (unitarias, de integración y de aceptación por HTTP) y exige al menos 90 % de cobertura por
módulo. Las pruebas de mutación son aparte: `./gradlew pitest pitestReportAggregate`.

`ArchitectureTest.java` (reglas de ArchUnit de la arquitectura limpia) lo genera el plugin del scaffold en cada build, con las rutas de la máquina que compila; por eso no está en git, pero corre igual con las demás pruebas. Las pruebas también corren desde una carpeta con tildes en Windows (`gradle.properties` lo resuelve).

Arquitectura limpia (scaffold de Bancolombia):

| Módulo | Qué hay |
|---|---|
| `domain/model` | Paciente, reglas RN-01 a RN-03, validaciones, alertas, reporte y los puertos (interfaces) |
| `domain/usecase` | Casos de uso: registrar, cambiar estado, escalar, publicar eventos, reporte, alertas |
| `infrastructure/entry-points/reactive-web` | API HTTP con WebFlux (RouterFunction), seguridad por API key, errores RFC 9457, Swagger |
| `infrastructure/entry-points/scheduler` | Monitor de SLA y relay del outbox |
| `infrastructure/driven-adapters/r2dbc-postgresql` | PostgreSQL reactivo (R2DBC), migraciones Flyway, SQL del reporte, LISTEN/NOTIFY de alertas |
| `infrastructure/driven-adapters/kafka-producer` | Publicación de los eventos con reactor-kafka |
| `infrastructure/driven-adapters/micrometer-metrics` | Métricas de negocio |
| `infrastructure/helpers/triage-contract` | Contrato de los eventos (CloudEvents) |
| `applications/app-service` | Arranque de la aplicación y configuración |
| `deployment/` | `Dockerfile` y manifests de Kubernetes |

Decisiones documentadas: [`docs/adr`](../docs/adr) (alertas, RAG, modo degradado, despliegue en Azure).
