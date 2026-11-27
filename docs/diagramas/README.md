# Diagramas de VidaCruz

Diagramas interactivos de la plataforma, hechos con [Archify](https://github.com/tt-a1i/archify). Cada `.html` es un
archivo autocontenido: **descárgalo o ábrelo desde tu copia del repositorio en el navegador** (GitHub muestra el
código del HTML, no el diagrama). Dentro del diagrama puedes hacer zoom, buscar, enfocar un nodo, seguir una relación,
cambiar entre tema claro y oscuro, presentarlo y exportarlo a PNG o SVG.

| Diagrama interactivo | Imagen | Qué muestra |
|---|---|---|
| [Arquitectura de la plataforma](arquitectura.html) | [SVG](arquitectura.svg) | Los dos servicios, PostgreSQL, Kafka, Redis, la IA y la observabilidad |
| [Registro, evento y escalamiento](registro-y-escalamiento.html) | [SVG](registro-y-escalamiento.svg) | Secuencia de punta a punta: registro, outbox, Kafka, proyección y alerta por SLA |
| [RAG con tres compuertas](rag-tres-compuertas.html) | [SVG](rag-tres-compuertas.svg) | Cómo el asistente evita inventar (RN-05) y cuándo responde el aviso o el 503 |
| [Despliegue en Azure](despliegue-azure.html) | [SVG](despliegue-azure.svg) | AKS, Gateway API, PostgreSQL administrado, registro de imágenes y GitHub Actions |

Los `.svg` los exporta el propio visor (botón **Export → SVG**): llevan los estilos adentro y se adaptan solos al
tema claro u oscuro, por eso se ven bien dentro de los README de GitHub.

![Arquitectura de la plataforma](arquitectura.svg)

## Cómo regenerarlos

Las fuentes están en [`fuentes/`](fuentes). Desde la carpeta de la skill de Archify, con `<repo>` como la ruta de tu
copia del repositorio:

```powershell
node bin/archify.mjs deliver architecture <repo>/docs/diagramas/fuentes/arquitectura.architecture.json <repo>/docs/diagramas/arquitectura.html --quality showcase
```

(Igual con `sequence`, `workflow` y `architecture` para los otros tres.) Después abre cada `.html` y usa
**Export → SVG** para actualizar su imagen. Todos pasan la validación `showcase` de
Archify: 0 cruces de relaciones, 0 errores y 0 advertencias. También caben sin scroll en pantallas de escritorio, de
1440×900 a 2048×1320.

La interfaz fija del visor (botones y leyenda) está en inglés: Archify solo la trae en inglés o chino. Todo el
contenido de los diagramas está en español.
