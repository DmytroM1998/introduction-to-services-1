# build module
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build_module

WORKDIR /api-gateway

# Copy pom.xml first, then download dependencies
COPY pom.xml .
# Since no source code is introduced yet, this layer is cached if pom.xml doesn't change
# Don't show libs being downloaded.
RUN mvn dependency:go-offline --no-transfer-progress

# Only then copy the source code and resources
COPY src/main/java ./src/main/java
COPY src/main/resources ./src/main/resources

# Build .jar file in /target dir
RUN mvn clean package -Dmaven.test.skip=true --no-transfer-progress

# run module
FROM eclipse-temurin:17-jre-alpine-3.21 AS run_module

# install wget for the health check
RUN apk add --no-cache wget

ENV MODULE_ROOT_DIR=/api-gateway
ENV APP_JAR_FILENAME=app.jar

# default (root) workdir
WORKDIR /
COPY --from=build_module $MODULE_ROOT_DIR/target/*.jar app.jar

# run the app within the container
CMD ["sh", "-c", "exec java -jar \"$APP_JAR_FILENAME\""]
