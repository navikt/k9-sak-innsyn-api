FROM ghcr.io/navikt/sif-baseimages/java-chainguard-21:2025.11.25.1015z

ENV LANG=C.UTF-8
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

COPY build/libs/*.jar app.jar

CMD ["-jar", "app.jar"]
