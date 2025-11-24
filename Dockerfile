FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY spread-bin-4.0.0/java/spread.jar ./spread.jar

RUN mvn install:install-file \
    -Dfile=./spread.jar \
    -DgroupId=org.spread \
    -DartifactId=spread \
    -Dversion=4.0.0 \
    -Dpackaging=jar

RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/alcatraz-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
EXPOSE 8081
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
