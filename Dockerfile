FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache tzdata busybox-extras \
    && addgroup -S app \
    && adduser -S -G app app

ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/li-picture-cloud-0.0.1-SNAPSHOT.jar app.jar

USER app
EXPOSE 8124
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
