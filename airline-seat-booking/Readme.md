✈️ Airline Seat Booking Prototype
This project is a Java-based simulation designed to demonstrate Database Concurrency Control. It explores how a system behaves when 120 users try to book the remaining 120 seats on a flight at the exact same time.

# 🛠️ 1. Database Setup
Create a MySQL database named prototype.

Run the scripts located in the /sql folder using your terminal or a DB client (like MySQL Workbench):


**Create Schema**

```sql/schema.sql```

**Insert Data**

```sql/data.sql```


# 🚀 2. The Three Approaches

The application tests three different SQL locking strategies to handle the "race condition" of multiple users grabbing the same seat.

**Approach 1: No Locking (Plain SELECT)**

SQL: SELECT ... WHERE user_id IS NULL LIMIT 1

Result: Inconsistent. Multiple threads read the same seat as "null" simultaneously.

Phenomenon: "Lost Updates." You will likely find empty seats at the end because many threads failed during the update phase or overwrote each other.

**Approach 2: Pessimistic Locking (FOR UPDATE)**

SQL: SELECT ... FOR UPDATE

Result: Consistent but Slow. MySQL locks the row, forcing threads to wait in a single-file line.

Problem: High "Lock Contention." If 120 threads wait too long, some may timeout, leading to unassigned seats (often Row 20 remains empty).

**Approach 3: High Concurrency Locking (SKIP LOCKED)**

SQL: SELECT ... FOR UPDATE SKIP LOCKED

Result: Fast and Consistent. If a thread finds a seat locked by someone else, it immediately skips to the next available one.

Performance: This is the most efficient way to handle "First-come, first-served" booking systems in modern databases.

# 💻 3. Running the App
Requirements
JDK 8 (The code is compatible with Java 8).

Maven for dependency management.

MySQL 8.0+ (Required for the SKIP LOCKED feature).

Run Command
Use Maven to compile and run the specific approach you want to test:

```
mvn clean compile
mvn exec:java -Dexec.mainClass="org.anay.app.ApproachThree"
```


# 📊 4. Sample Output
The application prints a console-based visual map after the threads complete:

Plaintext

--- DB Reset Complete ---

[Approach 3] User James Smith booked seat 1-A

[Approach 3] User Mary Garcia booked seat 1-B

...


--- Flight Seat Map ---

A  B  C   D  E  F

1 [✓][✓][✓] [✓][✓][✓]

2 [✓][✓][✓] [✓][✓][✓]

...

20 [✓][✓][✓] [✓][✓][✓]

Total Time: 145ms

# ⚙️ 5. Configuration

Update the following constants in org.anay.util.BookingManager to match your local setup:

DB_URL: jdbc:mysql://localhost:3306/prototype

USER: XXXXXX

PASS: XXXXXX