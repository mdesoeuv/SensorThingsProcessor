# The jar is platform independent, so the build stage runs once on the
# builder's own platform instead of once per target platform under QEMU.
FROM --platform=$BUILDPLATFORM maven:3.8.5-openjdk-17 AS build

WORKDIR /usr/local/FROST
COPY . .
# The cache mount keeps the Maven repository between builds on the same builder.
RUN --mount=type=cache,target=/root/.m2 mvn -B package

# openjdk:17 was withdrawn from Docker Hub; Temurin is the maintained JRE image.
FROM eclipse-temurin:17-jre
ARG JAR_FILE=FROST-Processor.jar
WORKDIR /usr/local/FROST
COPY --from=build /usr/local/FROST/target/*-jar-with-dependencies.jar /usr/local/FROST/${JAR_FILE}
CMD ["java", "-jar", "FROST-Processor.jar"]
