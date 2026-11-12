FROM maven:3.9.15-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn/ .mvn/
COPY src/ src/

RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests package \
    && jar_file="$(find target -maxdepth 1 -type f -name 'stockwise-api-*.jar' ! -name '*.jar.original' -print -quit)" \
    && test -n "$jar_file" \
    && cp "$jar_file" /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S spring \
    && adduser -S spring -G spring

COPY --from=build --chown=spring:spring /workspace/app.jar /app/app.jar

USER spring:spring

ENV PORT=10000

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
