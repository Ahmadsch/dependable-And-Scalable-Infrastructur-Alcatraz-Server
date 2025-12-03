
# Alcatraz – Distributed Lobby Service (Spread + Spring Boot)

This project provides a small distributed lobby system.
Nodes coordinate through **Spread**.
One node acts as **master**.
All writes go to the master.
Backups mirror state through replication messages.

## Features

* Master election based on smallest node id (`node1 < node2 < node3`).
* Membership tracking through Spread.
* State replication (player list).
* HTTP redirect to current master.
* Up to 4 players.
* Game can start when at least 2 players are registered.

---

# 1. Requirements

* Java 17
* Maven
* Windows (scripts are batch)
* Spread 4.0.0 (included in repo under `spread-bin-4.0.0/`)

---

# 2. Project Structure

```
alcatraz/
 ├── scripts/
 │    ├── install-spread-jar.bat
 │    ├── start-spread.bat
 │    ├── start-cluster.bat
 ├── spread-bin-4.0.0/
 │    ├── bin/win32/spread.exe
 │    └── bin/win32/spread.conf
 ├── src/main/java/at/hcw/alcatraz/
 │    ├── controller/
 │    ├── dao/
 │    ├── spread/
 │    └── dto/
 ├── pom.xml
 └── README.md
```

---


# 3. Setup (native, Windows)

## 3.1. Install Spread Java Library

Spread does not exist in Maven Central.
Install it into your local Maven repo:

```bash
scripts/install-spread-jar.bat
```

This installs in `.m2`:

```text
groupId=org.spread
artifactId=spread
version=4.0.0
```

---

## 3.2 Start Spread Daemon (Windows host)

Spread must run before any node starts.

```bash
scripts/start-spread-node3.bat
```

The script calls:

```bash
spread-bin-4.0.0/bin/win32/spread.exe -n localhost -c spread.conf
```

Example configuration:

```conf
Spread_Segment 192.168.0.255:4803 {
    localhost   127.0.0.1
    mymachine   192.168.0.76
}
```

This starts a Spread daemon bound to `localhost`.

---

## 3.3 Start Cluster Nodes (native)

The cluster consists of 3 HTTP servers + 1 Spread daemon.

```bash
scripts/build-run-server-1.bat
```

This launches:

| Node  | HTTP port | spread.node-id |
| ----- | --------- | -------------- |
| node1 | 8080      | node1          |
| node2 | 8081      | node2          |
| node3 | 8082      | node3          |

Every node joins the same Spread group (`alcatrazGroup`).

---


# 4. Docker Deployment – 3 PCs, 3 Nodes (Spread inside each container)

In this mode:

* each physical machine runs **one** container
* each container runs **its own Spread daemon** and **the Spring Boot node**
* all three daemons form one Spread segment via IPs


## 4.1 Spread configuration for multiple hosts

For a deployment with three physical machines, each machine runs one container and all three Spread daemons form a single segment.

### 4.1.1 Determine IPv4 addresses

On **Windows**, run in `cmd`:

```bat
ipconfig | findstr IPv4
````

Select the IPv4 address from the network interface that is in the common LAN (for example `192.168.0.x`).

On **Linux**, run:

```bash
ip -4 addr
```

or

```bash
ip addr show
```

Again, select the IPv4 address in the LAN where all three machines can reach each other.

Assume the following addresses:

* PC1: `192.168.0.76`
* PC2: `192.168.0.78`
* PC3: `192.168.0.79`

### 4.1.2 Spread configuration file

After finding the local IPv4 address (step 4.1.1), only `node2` and `node3` are updated in `spread-Docker.conf`.  
`node1` stays unchanged because it already contains the IPv4 of my own machine.

```conf
Spread_Segment 192.168.0.255:4803 {
    node1 192.168.0.76
    node2 192.168.0.XX
    node3 192.168.0.YY
}
```

Important:

* The configuration file must be **identical on all three machines**.
* Each entry maps a logical node id (`node1`, `node2`, `node3`) to the IPv4 address found in step 4.1.1.
* The Dockerfile copies this file into the image as `/etc/spread.conf`, so the same segment definition is used in every container.


## 4.2 Environment variables

The container expects:

* `SPREAD_NODE` – logical node id (`node1`, `node2`, `node3`)
* `SPREAD_HOST` – IP address at which this node’s Spread daemon is reachable
  (must match the IP used in `spread-Docker.conf` for this node)
* `SERVER_PORT` – HTTP port for the Spring Boot application

The entrypoint does roughly:

```sh
spread -n $SPREAD_NODE -c /etc/spread.conf &
sleep 3
java -jar app.jar \
  --server.port=$SERVER_PORT \
  --spread.host=$SPREAD_HOST \
  --spread.port=4803 \
  --spread.node-id=$SPREAD_NODE
```

## 4.3 build commands 

```bash
docker build -t alcatraz .
```
---

## 4.3 Run commands (one container per PC, host network)

Assume:

* PC1: `192.168.0.76` → `node1`
* PC2: `192.168.0.78` → `node2`
* PC3: `192.168.0.79` → `node3`

### PC1 – node1

```bash
docker run --network host -d -e SPREAD_NODE=node1 -e SPREAD_HOST=192.168.0.76 -e SERVER_PORT=8080 --name node1 alcatraz
```

### PC2 – node2

```bash
docker run --network host -d -e SPREAD_NODE=node2 -e SPREAD_HOST=192.168.0.78 -e SERVER_PORT=8081 --name node2 alcatraz
```

### PC3 – node3

```bash
docker run --network host -d -e SPREAD_NODE=node3 -e SPREAD_HOST=192.168.0.79 -e SERVER_PORT=8082 --name node3 alcatraz
```

---
## 4.4 Swagger UI

After starting any node:

```
http://localhost:<port>/swagger-ui.html
```

---

# 5. Master Election

Each membership change triggers:

1. Extract node ids from Spread group.
2. Sort them.
3. First id = master.

Example:

```
[node1, node2, node3] → master = node1
[node2, node3]        → master = node2
```

Election state is stored in `ElectionService`.
Reads and writes are synchronized to avoid mixed states across threads.

---

# 6. HTTP Routing

All writes must go to the master.

Non-master nodes return `307 Temporary Redirect`:

```
Location: http://<ip>:<master-port>/<same-path>
X-Master-Node: node1
```

Redirect is done inside:

```
PlayerController.redirectToMaster()
```

---

# 7. API Summary

### POST `/players/register`

Register a new player:

```
{
  "playerName": "Alice",
  "callbackUrl": "http://localhost:9001"
}
```

Rules:

* max 4 players
* unique name
* unique callback
* allowed only on master

---

### DELETE `/players/unregister/{name}`

Remove a player.
Master only.


---

### GET `/players/all`

Return registered players.
Master only.

---

### POST `/players/game/start`

Rules:

* at least 2 players
* master only

The master tries to notify **all registered clients** before the game is allowed to start.

Flow:

1. Master collects all players from the registry.
2. For each player:

    * Build a list of all other players.
    * Send `POST {callbackUrl}/start` with that list.
3. If **any** callback request fails, the game does **not** start.
   The server returns `503` and the registry state stays unchanged.
4. Only if **all** clients respond successfully:

    * a `RESET` message is broadcast to the cluster
    * the controller returns `200`

Example callback request:

```
POST http://localhost:9001/start
Content-Type: application/json

[
  { "playerName": "Bob", "callbackUrl": "http://localhost:9002" },
  { "playerName": "Charlie", "callbackUrl": "http://localhost:9003" }
]
```
---


### POST `/players/game/finish`

Master resets registry and broadcasts `RESET`. Master only.


---





