
# Alcatraz – Distributed Lobby Service (Spread + Spring Boot)

This project provides a small distributed lobby system.
Nodes coordinate through **Spread**.
One node acts as **master**.
All writes go to the master.
Backups mirror state through replication messages.

## Features

* Master election based on smallest node id (`node1 < node2 < node3`).
* Membership tracking through Spread.
* State replication (player list + game-start flag).
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

# 3. Setup

## 3.1. Install Spread Java Library

Spread does not exist in Maven Central.
Install it into your local Maven repo:

```
scripts/install-spread-jar.bat
```

This installs in .m2:

```
groupId=org.spread
artifactId=spread
version=4.0.0
```



---

## 3.2 Start Spread Daemon

Spread must run before any node starts.

```
scripts/start-spread.bat
```

The script calls:

```
spread-bin-4.0.0/bin/win32/spread.exe -n localhost -c spread.conf
```

This starts a Spread daemon bound to **localhost**.
The included configuration also uses the localhost segment for now:

```
Spread_Segment 192.168.0.255:4803 {
    localhost   127.0.0.1
    mymachine   192.168.0.76
}
```

---


## 3.3 Start Cluster Nodes

The cluster consists of 3 HTTP servers + 1 Spread daemon.

Start all nodes:

```
scripts/start-cluster.bat
```

This launches:

| Node  | HTTP port | spread.node-id |
| ----- | --------- | -------------- |
| node1 | 8080      | node1          |
| node2 | 8081      | node2          |
| node3 | 8082      | node3          |

Every node joins the same Spread group (`alcatrazGroup`).

---
## 3.4 Swagger UI

After starting any node:

```
http://localhost:<port>/swagger-ui.html
```

---

# 4. Master Election

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

# 5. HTTP Routing

All writes must go to the master.

Non-master nodes return `307 Temporary Redirect`:

```
Location: http://localhost:<master-port>/<same-path>
X-Master-Node: node1
```

Redirect is done inside:

```
PlayerController.redirectToMaster()
```

---

# 6. API Summary

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



Here is the updated version based on the new behavior (game start **fails** if any client callback is unreachable).
Clear, direct, no filler.

---

### POST `/players/game/start`

Rules:

* at least 2 players
* game not already started
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

    * `markStarted()` is executed
    * a `START` message is broadcast to the cluster
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





