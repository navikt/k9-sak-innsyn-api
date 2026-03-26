FROM ghcr.io/navikt/sif-baseimages/java-chainguard-21:2026.03.25.1416Z

COPY build/libs/*.jar app.jar

CMD ["-jar", "app.jar"]
