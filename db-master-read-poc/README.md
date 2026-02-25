

# MySQL Master-Slave Replication with Spring Boot Routing

This project demonstrates a high-availability architecture using **Spring Boot**, **MySQL Replication (Docker)**, and **Spring AOP** to automatically route database traffic based on transaction type (Read/Write Splitting).

---

## 🚀 Getting Started

### 1. Spin up the Infrastructure
Ensure your `docker-compose.yml` is ready and run the following command to start the Master (Port 3310) and Replica (Port 3311) containers:

```bash
docker compose up -d

```

### 2. Manual Synchronization & Replication Setup

Replication only syncs events that occur *after* the link is established. Since the Master initializes the database during boot, you must manually align the Replica first.

#### A. Prepare the Replica (Port 3311)

Connect to the Replica via MySQL Workbench or CLI and create the schema:

```sql
CREATE DATABASE prototype;

```

#### B. Get Master Status (Port 3310)

Connect to the Master node and check the current log position:

```sql
SHOW MASTER STATUS;

```

*Take note of the **File** (e.g., `mysql-bin.000003`) and the **Position** (e.g., `157`).*

#### C. Link Replica to Master (Port 3311)

Run these commands on the Replica to start following the Master node:

```sql
-- Create the schema first
CREATE DATABASE prototype;

-- First, stop any existing replica processes
STOP SLAVE;

-- Configure the connection to the master
CHANGE MASTER TO 
  MASTER_HOST='mysql-master',
  MASTER_USER='root',
  MASTER_PASSWORD='root',
  MASTER_LOG_FILE='mysql-bin.000003', -- Match the 'File' from Step B
  MASTER_LOG_POS=157;                -- Match the 'Position' from Step B

-- Start the replication
START SLAVE;

-- Verify status (Ensure Slave_IO_Running and Slave_SQL_Running are both 'Yes')
SHOW SLAVE STATUS;

```

### 3. Database Schema

Run this script on the **Master (Port 3310)**. Because replication is now active, it will automatically propagate to the Replica.

```sql
USE prototype;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    version INT DEFAULT 0
);

```

---

## 🧠 How It Works (The Logic)

The application uses a **Routing** pattern to decide which database node to use at runtime without changing the Repository code.

1. **Startup:** When the system boots, it creates a `TransactionRoutingDataSource` object. This object holds a map containing two physical connections:
* `MASTER` -> Points to `localhost:3310`
* `REPLICA` -> Points to `localhost:3311`


2. **Interception:** When an API call reaches the `UserService`, Spring AOP intercepts the call by looking at the `@Transactional` annotation.
3. **Context Setting:** * If the annotation is `@Transactional(readOnly = true)`, the `DataSourceAspect` sets the `DbContextHolder` to `"REPLICA"`.
* Otherwise, it defaults to `"MASTER"`.


4. **Routing:** When the Repository executes `repo.save()` or `repo.findBy()`, Spring internally calls `determineCurrentLookupKey()` on the `TransactionRoutingDataSource`. This method reads the key from the `DbContextHolder` and directs the SQL call to the corresponding database node.

---

## 📡 API Contract

### 1. Create User (Write Operation)

**Routes to:** Master (3310)

* **URL:** `POST /api/users`
* **Payload:** ```json
  {
  "name": "John Doe",
  "email": "john@example.com"
  }


* **Response:** `200 OK` with the saved User object.

### 2. Get User (Read Operation)
**Routes to:** Replica (3311)
* **URL:** `GET /api/users/{id}`
* **Response:** `200 OK` with the User object retrieved from the replica node.

---

## 🛠 Running the Application
Ensure you have **Java 17** installed (Class version 61.0).

```bash
./mvnw spring-boot:run

```

**Console Output Verification:**
When you trigger the APIs, watch your terminal for these debug logs:

* `DEBUG: RoutingDataSource is selecting: MASTER`
* `DEBUG: RoutingDataSource is selecting: REPLICA`