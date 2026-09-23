FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/backend-0.1.0-SNAPSHOT.jar app.jar

RUN mkdir -p /data/meetings && chown -R 10001:10001 /data
USER 10001

ENV APP_STORAGE_ROOT=/data/meetings
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
