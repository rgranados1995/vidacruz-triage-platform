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
   salida estructurada (JSON con esquema obligatorio): `noProtocol`, `answer` y `excerptIds`. La pregunta va escapada
   y separada de las instrucciones, como dato.
3. **Validación de citas.** Se acepta la respuesta solo si cita al menos un id entregado y no está vacía. Si cita un
   id que no se entregó, si no cita nada o si el modelo dice que los fragmentos no responden, se devuelve el aviso.
   Las fuentes que ve el médico (`protocolo`, `seccion`) se toman de los metadatos del corpus, **nunca del texto del
   modelo**, así que una fuente no se puede inventar.

Una respuesta descartada en la compuerta 3 queda en el log como WARN (`UNSUPPORTED_ANSWER`), para revisarla.

### 2. Proveedor y modelo

Claude, con el **SDK oficial de Anthropic para Java** (cliente asíncrono, sin hilos esperando la API). El modelo es
`claude-opus-5-5` (`LLM_MODEL`) con esfuerzo `medium` (`LLM_EFFORT`), ambos configurables. Se usan los
**fallbacks del lado del servidor**: si un modelo rechaza la petición, la API la reintenta con otro. Un `refusal` que
llegue igual se trata como respuesta sin contenido.

Spring AI se usa para lo que el enunciado le pide al RAG: documentos, embeddings y vector store. La orquestación de
la llamada al LLM vive en el caso de uso, porque ahí están las compuertas de RN-05.

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
| `ChatModel` de Spring AI para Anthropic | El SDK oficial está al día con structured outputs y fallbacks; las compuertas no dependen del cliente |
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
- Las compuertas verifican que la respuesta **cite** el corpus, no que cada frase sea fiel al fragmento citado. Eso
  queda en manos del prompt y del modelo. Una evaluación periódica con preguntas reales es la evolución natural.
- Cambiar de modelo o de esfuerzo es configuración (`LLM_MODEL`, `LLM_EFFORT`). Cambiar el modelo de embeddings exige
  recalibrar el umbral con la prueba `ProtocolCorpusSearchTest`.
