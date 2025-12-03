FROM ghcr.io/navikt/sif-baseimages/java-chainguard-21:2025.11.25.1015z

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

ENV JDK_JAVA_OPTIONS="-Dfile.encoding=UTF-8 \
                      -Duser.timezone=Europe/Oslo \
                      -Duser.language=nb \
                      -Duser.country=NO"

COPY build/libs/*.jar app.jar

CMD ["-jar", "app.jar"]
