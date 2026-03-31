FROM ghcr.io/navikt/sif-baseimages/java-chainguard-21:2026.03.30.1339Z

COPY build/libs/*.jar app.jar

CMD ["-jar", "app.jar"]
