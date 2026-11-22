-- Assignment 5 - local TEST database only (do not use on a real server)
CREATE DATABASE IF NOT EXISTS transport_test;
USE transport_test;

CREATE TABLE IF NOT EXISTS bus_pass (
    pass_id   VARCHAR(12)   PRIMARY KEY,
    reg_no    CHAR(9)       NOT NULL,
    name      VARCHAR(60)   NOT NULL,
    route_no  INT           NOT NULL,
    amount    DECIMAL(8,2)  NOT NULL CHECK (amount >= 0)
);

-- B2: login table for the SQL-injection experiment (test data only)
CREATE TABLE IF NOT EXISTS users (
    id   VARCHAR(20) PRIMARY KEY,
    pwd  VARCHAR(64) NOT NULL
);
INSERT IGNORE INTO users VALUES ('clerk1', 'secret123'), ('admin', 'Adm!n2026');

-- A5 / transactions
CREATE TABLE IF NOT EXISTS wallet (
    student_reg CHAR(9)     NOT NULL,
    wallet_type VARCHAR(10) NOT NULL,
    balance     DECIMAL(10,2) NOT NULL CHECK (balance >= 0),
    PRIMARY KEY (student_reg, wallet_type)
);
INSERT IGNORE INTO wallet VALUES ('192311001', 'HOSTEL', 2000), ('192311001', 'CANTEEN', 100);
