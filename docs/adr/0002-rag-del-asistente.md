# ADR 0002: RAG del asistente clínico: modelo, embeddings y vector store

- **Estado:** aceptada
- **Fecha:** 2026-11-26
- **Alcance:** `clinical-assistant-service` (RF-04 parte A, RN-05)

## Contexto

El asistente responde preguntas de los médicos sobre los protocolos clínicos internos. Por la naturaleza clínica,
RN-05 exige que toda respuesta tenga al menos una fuente válida del corpus. Si ningún protocolo aplica, se responde
solo el aviso de que no existe protocolo, sin contenido clínico generado.

El corpus oficial es pequeño (tres protocolos de dos o tres secciones), cambia poco y lo mantiene el área médica. Se
revisa junto con el código.

## Decisión

### 1. Tres compuertas deterministas alrededor del LLM

El modelo no decide solo qué es verdad: el código lo acota antes y lo verifica después.

1. **Recuperación con umbral.** El corpus se parte por sección (protocolo y sección son la unidad citable). La
   pregunta se convierte en embedding y se toman hasta `RAG_TOP_K` secciones con similitud ≥
   `RAG_SIMILARITY_THRESHOLD`. **Si ninguna pasa, se responde el aviso sin llamar al LLM.** La pregunta de diabetes del
   enunciado no puede alucinar porque el modelo nunca la ve.
2. **Generación acotada.** Claude recibe solo esas secciones, cada una con un id (`E1`, `E2`…), y debe responder con
   salida estructurada (JSON con esquema obligatorio): `noProtocol` y `claims`, una lista de oraciones, cada una con
   su `text` y los `excerptIds` que la dicen. La pregunta va escapada y separada de las instrucciones, como dato.
3. **Validación oración por oración.** Cada oración debe tener texto, citar solo ids entregados y no contener ninguna
   cifra ni unidad que no esté en los fragmentos que ella misma cita (`20 ml/kg` pasa si el fragmento dice
   `20 ml/kg`; `20 mg`, `30 ml/kg` o una dosis de un fármaco que el corpus no nombra con cifra no pasan). Basta una
   oración que falle para descartar **toda** la respuesta: el médico nunca recibe una mezcla de contenido sustentado
   y no sustentado. Si el modelo dice que los fragmentos no responden, también se devuelve el aviso.
   Las fuentes que ve el médico (`protocolo`, `seccion`) se toman de los metadatos del corpus, **nunca del texto del
   modelo**, así que una fuente no se puede inventar.

Una respuesta descartada en la compuerta 3 queda en el log como WARN (`UNSUPPORTED_ANSWER`), para revisarla.

### 2. Proveedor y modelo

Claude, con el **SDK oficial de Anthropic para Java** (cliente asíncrono, sin hilos esperando la API). El modelo es
`claude-opus-5-5` (`LLM_MODEL`) con esfuerzo `medium` (`LLM_EFFORT`), ambos configurables. Se usan los
**fallbacks del lado del servidor**: si un modelo rechaza la petición, la API la reintenta con otro. Un `refusal` que
llegue igual se trata como respuesta sin contenido.

### 2.1 Spring AI en el RAG y SDK oficial para la generación

El enunciado pide "Spring AI o LangChain4j" para el RAG. El RAG usa **Spring AI** en su parte de recuperación:
`Document`, `TransformersEmbeddingModel`, `SimpleVectorStore` y la búsqueda por similitud con umbral
(`VectorStoreProtocolRetriever`). La **generación** llama a Claude con el **SDK oficial de Anthropic**, detrás del
gateway `AnswerGenerator`, y no con el `ChatModel` de Spring AI. Es una decisión consciente, porque RN-05 depende de
tres cosas que el SDK da y el `ChatModel` de Spring AI 1.1 no daba al decidirlo:

| Necesidad de RN-05 | SDK oficial | `ChatModel` de Spring AI |
|---|---|---|
| Respuesta con esquema obligatorio (`noProtocol` y `claims` con `text` y `excerptIds`), garantizado por la API | Structured outputs nativos | Conversión del texto a JSON con instrucciones en el prompt: el modelo puede no cumplirlo |
| Fallback del lado del servidor si un modelo rechaza la petición | `fallbacksDefault()` | No disponible |
| Esfuerzo del modelo configurable | `outputConfig(..., effort)` | No disponible |

La orquestación (las compuertas) vive en el caso de uso, así que el cliente del LLM se puede cambiar: migrar al
`ChatModel` de Spring AI cuando cubra esas tres cosas sería un adaptador nuevo detrás de `AnswerGenerator`, sin
tocar el dominio ni las pruebas de las compuertas.

### 3. Embeddings locales

`sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` (Apache-2.0, multilingüe, int8 para AVX2) en ONNX,
dentro de la JVM con Spring AI Transformers. Se fija por commit y SHA-256: los tests lo descargan con una tarea de
Gradle y la imagen Docker lo descarga en el build. El cálculo del embedding corre en `boundedElastic`, nunca en el
event loop.

Sin segunda llave ni red: el aviso de "sin protocolo" funciona aunque no haya internet, y los tests usan
embeddings reales y reproducibles.

### 4. Vector store en memoria

`SimpleVectorStore` de Spring AI, construido al arrancar desde `classpath:corpus/*.md`. Cada réplica tiene una copia
idéntica y no hay infraestructura que operar. Si un protocolo tiene un formato inválido, el servicio no arranca: un
corpus a medio cargar nunca se sirve.

### 5. Calibración del umbral (0,50)

Medido el 2026-11-26 con el modelo real:

| Preguntas | Mejor similitud |
|---|---|
| Del corpus (las del enunciado y paráfrasis) | 0,586 a 0,758, siempre en la sección correcta |
| Fuera del corpus (diabetes, insulina, fractura, vacunas, paracetamol, neumonía…) | 0,037 a 0,440 |
| "Manejo inicial del infarto" (vocabulario parecido) | 0,531: pasa el umbral y lo frena la compuerta 2, probado |

## Opciones descartadas

| Opción | Por qué no |
|---|---|
| `ChatModel` de Spring AI para Anthropic | Sin structured outputs garantizados por la API, fallbacks ni esfuerzo (ver 2.1); queda como adaptador alternativo |
| Embeddings de Voyage u OpenAI | Segunda llave y dependencia de red también para el camino "sin protocolo" |
| pgvector | El asistente no tiene base relacional; sumaría una base solo para tres documentos |
| Redis Stack (RediSearch) | El compose usa Redis 7.4 sin módulos; se reserva para la proyección del estado de urgencias |
| Búsqueda léxica (BM25) | Las palabras compartidas ("primera hora", "inicial") dan falsos positivos con este corpus |

## Consecuencias

- Agregar un protocolo es agregar un `.md` y desplegar. Si el corpus crece a miles de documentos o se edita fuera del
  ciclo de despliegue, se migra el vector store (pgvector o Redis Stack) sin tocar el dominio: la búsqueda está
  detrás del gateway `ProtocolRetriever`.
- La imagen del asistente usa Ubuntu (glibc) en lugar de Alpine, porque ONNX Runtime y los tokenizers son nativos.
  Pesa unos 646 MB, con un modelo de 118 MB.
- La compuerta 3 es una regla conservadora y determinista: verifica las citas y las cifras de cada oración, que es
  lo que daña a un paciente si se inventa (dosis, tiempos, umbrales). No verifica el sentido de una oración sin cifras
  (por ejemplo, un fármaco nombrado sin dosis): eso queda en manos del prompt y del modelo. La evolución es un juez
  de implicación (entailment) por oración y una evaluación periódica con preguntas reales.
- Cambiar de modelo o de esfuerzo es configuración (`LLM_MODEL`, `LLM_EFFORT`). Cambiar el modelo de embeddings exige
  recalibrar el umbral con la prueba `ProtocolCorpusSearchTest`.
- El corpus es el del enunciado palabra por palabra, y lo fija la prueba `OfficialCorpusTest`: una sección editada
  por error haría que el asistente cite un texto que el protocolo no dice.
