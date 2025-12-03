@echo off
echo Building server...
cd .. && mvn -q -DskipTests clean install

echo Starting nodes...

start "node1" cmd /k java -jar target\alcatraz-0.0.1-SNAPSHOT.jar --server.port=8080 --spread.node-id=node1 --spread.host=192.168.0.76

echo node1 started.
