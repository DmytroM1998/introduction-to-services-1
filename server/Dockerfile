# Use this command to build the image on Apple Silicon machine
# docker buildx build --platform linux/amd64 -t resource-service . --load

# build module
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build_module

ENV MODULE_ROOT_DIR=/server
WORKDIR $MODULE_ROOT_DIR

# Copy pom.xml first, then download dependencies
COPY pom.xml .
# Since no source code is introduced yet, this layer is cached if pom.xml doesn't change
RUN mvn dependency:go-offline

# Only then copy the source code and resources
COPY src/main/java ./src/main/java
COPY src/main/resources ./src/main/resources

# build .jar file in /target dir. There are no unit tests, but still
RUN mvn clean package -Dmaven.test.skip=true

# run module
FROM eclipse-temurin:17-jre-alpine-3.21 AS run_module

ENV MODULE_ROOT_DIR=/server
ENV APP_JAR_FILENAME=app.jar

# default (root) workdir
WORKDIR /

COPY --from=build_module $MODULE_ROOT_DIR/target/*.jar $APP_JAR_FILENAME

# tells Docker the container will listen on port 8761 internally
EXPOSE 8761

# run the app/container
CMD java -jar "$APP_JAR_FILENAME"

