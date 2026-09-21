CREATE TABLE IF NOT EXISTS scan (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    executed_at TEXT NOT NULL,
    strategy    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS device (
    mac         TEXT PRIMARY KEY,
    ip          TEXT,
    hostname    TEXT,
    vendor      TEXT,
    first_seen  TEXT,
    last_seen   TEXT
);

CREATE TABLE IF NOT EXISTS scan_port (
    scan_id   INTEGER REFERENCES scan(id),
    device_id TEXT REFERENCES device(mac),
    port      INTEGER,
    PRIMARY KEY (scan_id, device_id, port)
);