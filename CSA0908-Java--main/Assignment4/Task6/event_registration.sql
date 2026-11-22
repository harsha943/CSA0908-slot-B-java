-- Task 6 - Campus Event Registration database (MySQL)
CREATE DATABASE IF NOT EXISTS campus_events;
USE campus_events;

CREATE TABLE IF NOT EXISTS events (
    event_id   INT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS registrations (
    reg_id        INT PRIMARY KEY AUTO_INCREMENT,
    student_reg   CHAR(9)      NOT NULL,
    student_name  VARCHAR(60)  NOT NULL,
    email         VARCHAR(80)  NOT NULL,
    event_id      INT          NOT NULL,
    registered_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event FOREIGN KEY (event_id) REFERENCES events(event_id),
    CONSTRAINT uq_student_event UNIQUE (student_reg, event_id)   -- no double registration
);

INSERT IGNORE INTO events(name) VALUES ('Dance'), ('Music'), ('Drama'), ('Quiz'), ('Coding Contest');
