````markdown
# STOMP Emergency Service Platform

**Assignment 3 – SPL 251: Emergency Service Subscription**

A two-part system for subscribing to and reporting emergencies over STOMP:

---

## 🚀 Quick Start

1. **Clone the repo**  
   ```sh
   git clone https://github.com/<username>/stomp-emergency-service.git
   cd stomp-emergency-service
````

2. **Server (Java)**

   ```sh
   cd server
   mvn clean compile
   # Run in Thread-Per-Client mode:
   mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" \
                 -Dexec.args="7777 tpc"
   # Or Reactor mode:
   mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" \
                 -Dexec.args="7777 reactor"
   ```

3. **Client (C++)**

   ```sh
   cd ../client
   make
   # Connect to server (must be running first):
   ./bin/StompEMIClient <host:port>
   ```

---

## 📂 Project Layout

```
.
├── docs/
│   └── SPL251__Assignment_3_instructions.pdf   # Assignment spec
├── server/                                     # Java STOMP server
│   ├── pom.xml
│   └── src/main/java/...                       # Connections, protocols, TPC/Reactor
└── client/                                     # C++ STOMP client
    ├── include/                                # Headers
    ├── src/                                    # Implementation (threads, STOMP frames)
    ├── bin/                                    # Executables
    └── Makefile                                # Build rules
```

---

## 🔧 Build & Run

### Server

```sh
cd server
mvn clean compile
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" \
              -Dexec.args="7777 tpc"
```

### Client

```sh
cd client
make
./bin/StompEMIClient 127.0.0.1:7777
```

---

## 💬 Usage Examples

* **Login:**

  ```txt
  login 127.0.0.1:7777 alice secret
  ```

* **Subscribe to channel:**

  ```txt
  join fire_dept
  ```

* **Report events from JSON file:**

  ```txt
  report events1.json
  ```

* **Unsubscribe from channel:**

  ```txt
  exit fire_dept
  ```

* **Logout:**

  ```txt
  logout
  ```

Refer to `docs/SPL251__Assignment_3_instructions.pdf` for full protocol details, frame formats, and file specifications.

---

## 📄 License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

```
```
