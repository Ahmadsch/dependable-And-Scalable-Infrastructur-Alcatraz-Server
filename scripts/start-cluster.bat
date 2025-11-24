@echo off
echo Building server...
mvn -q -DskipTests clean install

echo Starting nodes...

start "node1" cmd /k java -jar target\alcatraz-0.0.1-SNAPSHOT.jar --server.port=8080 --spread.node-id=node1

start "node2" cmd /k java -jar target\alcatraz-0.0.1-SNAPSHOT.jar --server.port=8081 --spread.node-id=node2

start "node3" cmd /k java -jar target\alcatraz-0.0.1-SNAPSHOT.jar --server.port=8082 --spread.node-id=node3

echo Cluster started.
