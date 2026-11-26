# ADR 0001: Alertas de escalamiento al equipo médico (RF-06)

- **Estado:** aceptada
- **Fecha:** 2026-11-26
- **Alcance:** `triage-service`

## Contexto

RF-06 pide alertar al equipo médico cuando un paciente es escalado, y deja abiertos el canal, el formato y la
tecnología. Un escalamiento significa que un paciente superó el tiempo máximo de espera de su nivel (RN-02) sin entrar
a consulta: es la situación en la que una demora puede hacer daño.

Supuestos de los que parte la decisión:

1. **Una alerta no se puede perder**, aunque en ese momento nadie tenga la pantalla abierta, se reinicie el servicio
   o se caiga una conexión.
2. **Una alerta deja de importar cuando el paciente ya fue atendido.** Si sigue visible, el equipo pierde confianza en
   las alertas y deja de mirarlas.
3. **El equipo necesita saber si alguien ya la tomó**, para no duplicar esfuerzos en una urgencia con mucha carga.
4. **Las alertas se ven en pantallas compartidas** (estación de enfermería, sala de médicos), así que no deben
   mostrar datos personales del paciente.
5. Es un sistema de backend (el enunciado no pide frontend): el canal tiene que poder consumirlo cualquier cliente
   HTTP y probarse con herramientas de línea de comandos.

## Opciones consideradas

| Opción | A favor | En contra |
|---|---|---|
| **A. Alertas persistidas en el triage y stream Server-Sent Events (SSE)** | La alerta se crea en la misma transacción del escalamiento, así que existe si y solo si el paciente quedó escalado. Solo depende de PostgreSQL, que el triage ya necesita. SSE es HTTP estándar, unidireccional y encaja con `Flux`; se prueba con `curl -N` | El triage asume una responsabilidad más; un cliente web con `EventSource` no puede enviar el header de la API key (ver consecuencias) |
| B. El asistente consume `paciente_escalado` de Kafka, guarda en Redis y expone SSE | Separa la notificación del triage | Suma Kafka, el consumidor y Redis al camino de una alerta clínica; si el asistente cae, no hay alertas. Además, adelanta en este paso buena parte de la infraestructura del asistente |
| C. Webhook a Teams o Slack | Llega al celular del equipo | Depende de un servicio externo y de sus credenciales; no se puede demostrar en local sin cuentas reales; no da estado (reconocida, resuelta) |
| D. Correo (SMTP) | Conocido | Lento para una urgencia; el cliente SMTP de Java es bloqueante; tampoco da estado |
| E. Un topic de Kafka para alertas | Reutiliza la mensajería | Solo traslada el problema: alguien tiene que consumirlo y mostrarlo al equipo |

## Decisión

Opción **A**: alertas **persistidas y con estado** en el `triage-service`, con un **stream SSE** para las pantallas
del equipo.

- **Creación:** el escalamiento del monitor de SLA inserta la alerta en `medical_alert`, en la misma transacción que
  marca el flag `escalado`, escribe la auditoría y deja el evento en el outbox. `patient_id` es único: una alerta por
  paciente, igual que el escalamiento (RN-03).
- **Estados:** `PENDIENTE` → `RECONOCIDA` (un médico la toma) → `RESUELTA`. Se resuelve sola en la transacción que
  pasa al paciente a `EN_CONSULTA` o `ALTA`, sin que nadie tenga que cerrarla.
- **Contenido:** id de la alerta, id del paciente, nivel, SLA, fecha de registro, fecha de escalamiento, minutos de
  espera, estado, quién la reconoció y cuándo, y fecha del último cambio. **Sin nombre ni queja principal**: el detalle
  se consulta con `GET /triage/pacientes/{id}`.
- **API** (con la misma API key que el resto):
  - `GET /triage/alertas`: alertas activas, de la más grave a la menos grave y luego por antigüedad.
  - `GET /triage/alertas/stream`: al conectarse envía las activas y luego cada cambio (evento `alerta`). Envía un
    comentario `latido` cada `ALERTS_HEARTBEAT_INTERVAL` para que proxies y balanceadores no corten la conexión.
  - `POST /triage/alertas/{id}/reconocimiento` con `{"responsable": "..."}`: 409 (`TRG-009`) si ya fue reconocida o
    resuelta, 404 (`TRG-008`) si no existe.
- **Entre réplicas:** cada escritura hace `pg_notify('medical_alert', id)` dentro de su transacción, así que
  PostgreSQL solo avisa de cambios confirmados y en el orden de los commits. Cada réplica escucha con una conexión
  propia (fuera del pool). Si la conexión se cae, se reconecta con espera creciente (1 a 30 s) y al volver a escuchar
  publica las alertas que cambiaron mientras estuvo desconectada.
- **Clientes lentos:** cada cliente tiene un buffer propio de 256 eventos. Si lo llena, se cierra su stream; al
  reconectarse recibe de nuevo las activas. Un cliente lento nunca frena a los demás ni hace crecer la memoria.

## Consecuencias

- **Entrega al menos una vez.** Un cliente puede recibir la misma alerta más de una vez (por ejemplo, al conectarse,
  en la lista inicial y en un cambio simultáneo). Cada evento trae la alerta completa: vale la de `actualizadaEn` más
  reciente.
- **El triage no depende de nadie más para alertar**: ni de Kafka ni del asistente. Si Kafka cae, las alertas siguen
  funcionando; los eventos esperan en el outbox, como antes.
- **`responsable` lo informa el cliente.** La API key identifica al sistema, no a la persona. Cuando haya identidad
  real (JWT con el usuario), el responsable saldrá del token.
- **Clientes web:** `EventSource` del navegador no permite headers, así que una pantalla web tendría que usar `fetch`
  con streaming o pasar por un backend propio. La API key no se acepta en la URL, porque quedaría en logs y en el
  historial.
- **Integraciones de salida** (Teams, pager, SMS): quedan como extensión. Se pueden construir consumiendo
  `paciente_escalado` de `triaje.eventos`, que ya es el contrato público de integración, sin tocar el triage.
- **Pendiente:** limpieza de alertas resueltas antiguas (D12) y métrica de alertas pendientes (D11).
