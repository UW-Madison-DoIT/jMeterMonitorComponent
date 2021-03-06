CREATE TABLE MONITOR_HOST_STATUS (
    HOST_NAME VARCHAR(500),
    STATUS VARCHAR(50) NOT NULL,
    FAILURE_COUNT INTEGER,
    MESSAGE_COUNT INTEGER,
    LAST_NOTIFICATION TIMESTAMP,
    LAST_UPDATED TIMESTAMP,
    CONSTRAINT PK_MONITOR_HOST_STATUS PRIMARY KEY (HOST_NAME)
);

CREATE TABLE MONITOR_LOG (
    HOST_NAME VARCHAR(500),
    LABEL VARCHAR(2000),
    LAST_SAMPLE TIMESTAMP,
    DURATION INTEGER,
    SUCCESS VARCHAR(10),
    CONSTRAINT PK_MONITOR_LOG PRIMARY KEY (HOST_NAME, LABEL)
);

CREATE TABLE MONITOR_ERRORS (
    HOST_NAME VARCHAR(500),
    LABEL VARCHAR(2000),
    FAILURE_DATE TIMESTAMP,
    STATUS VARCHAR(50),
    EMAIL_SUBJECT VARCHAR(1000),
    EMAIL_BODY VARCHAR(4000),
    EMAIL_SENT VARCHAR(10)
);