FROM ghcr.io/navikt/sif-baseimages/java-chainguard-21:2025.11.25.1015z

ENV JDK_JAVA_OPTIONS="-Duser.timezone=Europe/Oslo \
                      -Duser.language=nb \
                      -Duser.country=NO"

COPY build/libs/*.jar app.jar

CMD ["-jar", "app.jar"]
