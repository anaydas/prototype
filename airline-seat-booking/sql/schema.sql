-- Drop tables in reverse order of dependencies
DROP TABLE IF EXISTS seats;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS trips;

-- 1. Trips table
CREATE TABLE trips (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL
);

-- 2. Users table
CREATE TABLE users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL
);

-- 3. Seats table
-- Note: trip_id and user_id allow nulls for unassigned seats
CREATE TABLE seats (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    trip_id INT,
    user_id INT,
    CONSTRAINT fk_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id)
);