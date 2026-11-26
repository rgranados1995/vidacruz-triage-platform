# ADR 0003: Modo degradado del asistente y resiliencia ante el proveedor de IA

- **Estado:** aceptada
- **Fecha:** 2026-11-26
- **Alcance:** `clinical-assistant-service`

## Contexto

El enunciado pide un circuit breaker de Resilience4j sobre las llamadas al LLM, con fallback explícito: modo
degradado, con respuesta 503 y un mensaje claro cuando el LLM no está disponible. El servicio de triaje nunca debe
depender del asistente.

En una urgencia, una respuesta rápida que diga "no disponible" es mejor que una espera larga: el médico puede ir al
protocolo directamente.

## Decisión

### Política, de afuera hacia adentro (orden recomendado por Resilience4j)

| Capa | Configuración (`resilience4j.*.instances.claude`) | Qué hace |
|---|---|---|
| Retry | `LLM_RETRY_MAX_ATTEMPTS` = 2 intentos; backoff exponencial desde `LLM_RETRY_BACKOFF` (500 ms) con jitter del 50 % | Repite solo fallas **transitorias**: 429, 408, 5xx (529 incluido), timeout o red |
| Circuit breaker | Ventana de 10 llamadas; mínimo 5; se abre con 50 % de fallas; abierto 30 s; 2 llamadas de prueba en medio abierto | Con el circuito abierto responde de inmediato, sin llamar a la API. Un circuito abierto nunca se reintenta |
| Time limiter | `LLM_TIMEOUT` (15 s) por intento, cancelando la petición en curso | Ninguna llamada queda colgada |

Peor caso de espera para el médico: unos 31 s (dos intentos de 15 s y el backoff). Con el circuito abierto, el 503
es inmediato.

Se aplica con los operadores reactivos de Resilience4j: sin bloqueos y sin anotaciones. El SDK de Anthropic no
reintenta por su cuenta (`maxRetries(0)`), para no multiplicar los intentos.

### Qué cuenta y qué no

Un mismo clasificador (`ProviderFailures`) decide las dos cosas:

| Falla | ¿Se reintenta? | ¿Cuenta para abrir el circuito? |
|---|---|---|
| 429, 408, 5xx, 529, timeout, error de red | Sí | Sí |
| 401 o 403 (llave revocada o sin permisos) | No (ningún reintento lo arregla) | Sí: deja de consumir intentos |
| 400, 404, 413, 422 (petición mal armada, error nuestro) | No | No: un bug nuestro no debe tumbar el asistente para todos |

### Fallback explícito

Todo lo que la política no logra resolver termina en `AST-002` con **HTTP 503**:

- título "El asistente clínico no está disponible en este momento";
- detalle con la indicación de intentar de nuevo y, mientras tanto, consultar los protocolos directamente;
- header `Retry-After: 30`, igual al tiempo que el circuito permanece abierto.

Se registra como WARN de una línea, con la causa (timeout, código HTTP o circuito abierto). Solo un error inesperado
se registra como ERROR con su stack trace.

### Lo que sigue funcionando en modo degradado

- Las preguntas sin protocolo responden el aviso: esa compuerta no usa el LLM.
- La validación de entrada, la seguridad y los probes.
- **Sin `ANTHROPIC_API_KEY`** el servicio arranca igual y todo lo que necesita el modelo responde 503, sin llamar a
  nadie. Así `docker compose up` funciona sin secretos.

### Probes

Ni el circuit breaker ni el proveedor entran en la readiness (`register-health-indicator: false`). Un circuito abierto
es el modo degradado funcionando, no un pod enfermo. Sacar el pod del balanceador cambiaría el aviso y el 503 por un
error de conexión.

### Independencia del triaje

El triage no llama al asistente; se comunican solo por Kafka (`triaje.eventos`). Una caída del asistente o del
proveedor de IA no afecta el registro, el flujo clínico, el monitor de SLA ni las alertas.

## Consecuencias

- Una racha de errores transitorios llega rápido al 503 (un solo reintento), a cambio de no hacer esperar al médico.
- Con varias réplicas, cada una tiene su propio circuito: lo abre por su cuenta con sus propias fallas. Es suficiente
  para este volumen. Un estado compartido sería una evolución.
- Evoluciones si el tráfico lo pide: un bulkhead (límite de llamadas simultáneas al proveedor) y un rate limiter
  según la cuota de la cuenta de Anthropic.
- Las métricas de Resilience4j (estado del circuito, reintentos, fallas) y la latencia del LLM se exponen en D11.
