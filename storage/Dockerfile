# Use this command to build the image on Apple Silicon machine
# docker buildx build --platform linux/amd64 -t storage . --load

# build module
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build_module

ENV MODULE_ROOT_DIR=/storage
WORKDIR $MODULE_ROOT_DIR

# Copy pom.xml first, then download dependencies
COPY pom.xml .

# Since no source code is introduced yet, this layer is cached if pom.xml doesn't change
# Don't show libs being downloaded.
RUN mvn dependency:go-offline --no-transfer-progress

# Only then copy the source code and resources
COPY src ./src
COPY src/main/resources ./src/main/resources

# Build .jar file in /target dir
RUN mvn clean package -DskipTests --no-transfer-progress

# run module
FROM eclipse-temurin:17-jre-alpine-3.21

ENV MODULE_ROOT_DIR=/storage
ENV APP_WAR_FILENAME=app.war

RUN apk add --no-cache wget

# default (root) workdir
WORKDIR /

# Copy the built war to the workdir
COPY --from=build_module $MODULE_ROOT_DIR/target/*.war $APP_WAR_FILENAME

# tells Docker the container will listen on the port internally
EXPOSE 8086

# Launch the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.war"]
