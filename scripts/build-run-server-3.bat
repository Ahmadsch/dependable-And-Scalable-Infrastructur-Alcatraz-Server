@echo off
echo Building server...
mvn -q -DskipTests clean install

echo Starting nodes...

start "node3" cmd /k java -jar target\alcatraz-0.0.1-SNAPSHOT.jar --server.port=8082 --spread.node-id=node3 --spread.host=127.0.0.1

echo node3 started.
