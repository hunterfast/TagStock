# Bauen und Ausfuehren in einem Rutsch, damit auf dem Server kein JDK noetig ist.
FROM eclipse-temurin:21-jdk AS bau
WORKDIR /bau
COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.title="TagStock-Server" \
      org.opencontainers.image.description="Bestand, Teams und Leih-Anfragen fuer die TagStock-App" \
      org.opencontainers.image.source="https://github.com/hunterfast/TagStock"

# Nicht als root laufen lassen. 99:100 ist auf Unraid "nobody:users", damit der
# Server in /mnt/user/appdata schreiben darf. Ist die Kennung im Basis-Abbild
# schon vergeben, vergibt useradd die naechste freie.
RUN useradd --system --uid 99 --gid 100 --create-home tagstock \
    || useradd --system --create-home tagstock
WORKDIR /app
COPY --from=bau /bau/build/libs/tagstock-server.jar app.jar

# Die Datenbank liegt im Volume, damit sie Updates ueberlebt.
ENV TAGSTOCK_DB=/data/tagstock.db \
    TAGSTOCK_PORT=8080 \
    JAVA_OPTS=-Xmx256m
VOLUME ["/data"]
EXPOSE 8080

RUN mkdir -p /data && chown -R tagstock:tagstock /data /app
USER tagstock

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -q -O- "http://127.0.0.1:${TAGSTOCK_PORT}/api/v1/status" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
