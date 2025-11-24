FROM maven:3.9.6-eclipse-temurin-17 AS build-backend
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


# ============================
# Runtime Container
# ============================
FROM eclipse-temurin:17-jre
WORKDIR /app

# 32-bit Runtime für i686-Binary
RUN apt-get update && apt-get install -y libc6-i386 && rm -rf /var/lib/apt/lists/*

# Spread Config
COPY spread-Docker.conf /etc/spread.conf

# Dein fertiges Linux-Binary (i686)
COPY spread-bin-4.0.0/bin/i686-pc-linux-gnu/spread /usr/local/bin/spread
RUN chmod +x /usr/local/bin/spread

# Spring Boot JAR
COPY --from=build-backend /app/target/alcatraz-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
EXPOSE 8081
EXPOSE 8082
EXPOSE 4803/udp
EXPOSE 4803/tcp

ENTRYPOINT ["/bin/sh", "-c", "\
  /usr/local/bin/spread -n $SPREAD_NODE -c /etc/spread.conf & \
  sleep 4 && \
  java -jar app.jar \
    --server.port=$SERVER_PORT \
    --spread.host=$SPREAD_HOST \
    --spread.port=4803 \
    --spread.node-id=$SPREAD_NODE \
"]

