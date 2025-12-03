@echo off
echo Building server...
mvn -q -DskipTests clean install

echo Starting nodes...

start "node2" cmd /k java -jar target\alcatraz-0.0.1-SNAPSHOT.jar --server.port=8081 --spread.node-id=node2 --spread.host=127.0.0.1

echo node2 started.
