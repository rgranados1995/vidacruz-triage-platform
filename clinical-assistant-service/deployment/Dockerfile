# Build stage: compiles the boot jar with the project's own Gradle wrapper, so the image does not depend on a jar
# built outside Docker.
FROM eclipse-temurin:21.0.13.1_2-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,id=clinical-assistant-service-gradle,target=/root/.gradle sh ./gradlew :app-service:bootJar --no-daemon

# Embedding model of the protocol search, pinned by commit and checked by SHA-256 (same files as the tests use):
# sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2 (Apache-2.0), int8 for AVX2.
FROM eclipse-temurin:21.0.13.1_2-jdk-alpine AS model
ARG MODEL_URL=https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/e8f8c211226b894fcb81acc59f3b34ba3efd5f42
WORKDIR /models
RUN wget -q -O model.onnx "${MODEL_URL}/onnx/model_quint8_avx2.onnx" \
    && wget -q -O tokenizer.json "${MODEL_URL}/tokenizer.json" \
    && echo "98a01d88b7de996cdea58c32ca71208c09968d143798814b2ea09d3439dc334f  model.onnx" | sha256sum -c - \
    && echo "2c3387be76557bd40970cec13153b3bbf80407865484b209e655e5e4729076b8  tokenizer.json" | sha256sum -c -

# Runtime stage: JRE only, non-root user. Ubuntu instead of Alpine because ONNX Runtime and the Hugging Face
# tokenizers are native libraries built for glibc.
FROM eclipse-temurin:21.0.13.1_2-jre-noble
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=model --chown=app:app /models /opt/models
COPY --from=build --chown=app:app /workspace/applications/app-service/build/libs/clinical-assistant-service.jar app.jar
USER app
# The native libraries are extracted at startup into a folder the user can write
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    DJL_CACHE_DIR=/tmp/djl \
    EMBEDDING_MODEL_URI=file:/opt/models/model.onnx \
    EMBEDDING_TOKENIZER_URI=file:/opt/models/tokenizer.json
HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=5 \
    CMD wget -qO- "http://localhost:${SERVER_PORT}/health/readiness" || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
