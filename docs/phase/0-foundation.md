# Phase 0 — Foundation

> Prepare the codebase for evolution: normalized schema, unit tests, external configuration and structure refactoring.

---

## Current State

### Existing schema

```sql
CREATE TABLE scan_result (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ip TEXT NOT NULL,
    open_ports TEXT,        -- CSV: "22,80,443"
    date_hour TEXT NOT NULL -- LocalDateTime.now().toString()
)
```

Issues:
- CSV ports prevent queries by specific port
- `date_hour` as string causes collision between scans in the same second
- No scan metadata (strategy used)
- `Device` model contains only IP + ports

### Code structure

```
com.gabriel.networkmonitor/
├── Main.java                    # empty
├── scanner/
│   ├── NetworkScanner.java      # orchestration + main()
│   ├── DeviceScanner.java       # discovery via isReachable()
│   ├── QuickScanStrategy.java
│   ├── StableScanStrategy.java
│   └── FullScanStrategy.java
├── model/
│   └── Device.java              # IP + port list
├── repository/
│   └── ScanRepository.java      # raw JDBC
├── detector/
│   └── ChangeDetector.java      # diff between snapshots
└── interfaces/
    └── PortScanStrategy.java
```

### Dead code

- `Main.java` — empty class
- `ChangeDetectorTest.java` — empty class

---

## Tasks

### T1. Normalized schema

Create the following tables:

```sql
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
```

**File:** `src/main/resources/db/migration/V001__schema_normalized.sql`

**Details:**
- Run migration on `ScanRepository` initialization
- Maintain compatibility with existing data (migrate from `scan_result`)
- `device.mac` will be populated in Phase 1; use IP as temporary identifier for now

---

### T2. Scan ID per execution

Replace `LocalDateTime.now().toString()` with unique identifier per scan.

**Approach:** Auto number from `scan` table (PK `id`). Store `executed_at` as separate metadata.

**Affected files:**
- `ScanRepository.java` — `saveScan()` method must:
  1. Insert into `scan` table and get generated `id`
  2. Insert into `scan_port` referencing `scan_id`
  3. Create `device` record if not exists (using IP as placeholder until Phase 1)

---

### T3. SocketFactory interface

Create interface for dependency injection in tests.

**File:** `src/main/java/com/gabriel/networkmonitor/interfaces/SocketFactory.java`

```java
public interface SocketFactory {
    Socket create(String host, int port) throws IOException;
}
```

**File:** `src/main/java/com/gabriel/networkmonitor/scanner/RealSocketFactory.java`

```java
public class RealSocketFactory implements SocketFactory {
    @Override
    public Socket create(String host, int port) throws IOException {
        return new Socket(host, port);
    }
}
```

**File:** `src/test/java/com/gabriel/networkmonitor/scanner/SocketFake.java`

```java
public class SocketFake implements SocketFactory {
    private final Set<Integer> openPorts;

    public SocketFake(Set<Integer> openPorts) {
        this.openPorts = openPorts;
    }

    @Override
    public Socket create(String host, int port) throws IOException {
        if (openPorts.contains(port)) {
            return new Socket(); // simulates connection
        }
        throw new IOException("Connection refused");
    }
}
```

**Affected files:**
- `QuickScanStrategy.java` — receive `SocketFactory` via constructor
- `StableScanStrategy.java` — same
- `FullScanStrategy.java` — same

---

### T4. Unit tests

#### ChangeDetectorTest

**File:** `src/test/java/com/gabriel/networkmonitor/detector/ChangeDetectorTest.java`

Test cases:
1. `shouldDetectNewDevice` — previous scan empty, current scan with device
2. `shouldDetectRemovedDevice` — previous scan with device, current scan empty
3. `shouldDetectOpenedPort` — port didn't exist, now exists
4. `shouldDetectClosedPort` — port existed, now doesn't exist
5. `shouldReturnEmptyWhenNoChanges` — identical scans
6. `shouldHandleEmptyLists` — both scans empty

#### PortScanStrategyTest

**File:** `src/test/java/com/gabriel/networkmonitor/scanner/QuickScanStrategyTest.java`

Test cases:
1. `shouldReturnOpenPorts` — port configured as open
2. `shouldReturnEmptyListWhenAllClosed` — no open ports
3. `shouldHandleConnectionTimeout` — port doesn't respond

---

### T5. External configuration

**File:** `src/main/resources/application.properties`

```properties
# Network
scanner.subnet=192.168.0
scanner.timeout=200

# Common ports (QuickScanStrategy)
scanner.common-ports=22,80,443,8000,8080,8443

# Thread pool
scanner.pool-size=50

# Database
database.url=jdbc:sqlite:network-monitor.db
```

**Affected files:**
- `DeviceScanner.java` — read timeout and subnet from configuration
- `QuickScanStrategy.java` — read port list from configuration
- `ScanRepository.java` — read database URL from configuration

---

### T6. Structure refactoring

1. **Move `main()` to `Main.java`**
   - Create `Main` class with functional `main()` method
   - Remove `NetworkScanner.main()`

2. **Create `ScannerService`**
   - Facade class that encapsulates: discovery → scan → persistence → diff
   - `NetworkScanner` becomes scan orchestrator only
   - `ScannerService` coordinates the complete flow

**File:** `src/main/java/com/gabriel/networkmonitor/ScannerService.java`

```java
public class ScannerService {
    private final DeviceScanner deviceScanner;
    private final PortScanStrategy portScanner;
    private final ScanRepository repository;
    private final ChangeDetector detector;

    // constructor with dependencies

    public List<String> executeFullCycle(String subnet) {
        // 1. Discovery
        // 2. Port scan
        // 3. Persistence
        // 4. Diff with previous scan
        // 5. Return events
    }
}
```

---

### T7. Virtual threads

Replace `Executors.newFixedThreadPool(50)` with `Executors.newVirtualThreadPerTaskExecutor()`.

**Affected file:** `DeviceScanner.java`

**Change:**
```java
// Before
ExecutorService executor = Executors.newFixedThreadPool(50);

// After
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```

---

## Implementation Order

1. T1 (schema) + T2 (scan ID)
2. T3 (SocketFactory) + T4 (tests)
3. T5 (external configuration)
4. T6 (refactoring)
5. T7 (virtual threads)

---

## Acceptance Criteria

- [ ] Schema created with 3 tables (scan, device, scan_port)
- [ ] Each scan generates a unique `scan.id`
- [ ] Unit tests run without real network
- [ ] `ChangeDetectorTest` covers 6 scenarios
- [ ] External configuration functional (application.properties)
- [ ] `Main.java` contains functional `main()`
- [ ] `ScannerService` orchestrates complete flow
- [ ] Virtual threads functional in `DeviceScanner`

---

## Files Created/Modified

| File | Action |
|---|---|
| `src/main/resources/db/migration/V001__schema_normalized.sql` | Create |
| `src/main/java/.../interfaces/SocketFactory.java` | Create |
| `src/main/java/.../scanner/RealSocketFactory.java` | Create |
| `src/test/java/.../scanner/SocketFake.java` | Create |
| `src/test/java/.../detector/ChangeDetectorTest.java` | Modify |
| `src/test/java/.../scanner/QuickScanStrategyTest.java` | Create |
| `src/main/resources/application.properties` | Create |
| `src/main/java/.../ScannerService.java` | Create |
| `src/main/java/.../Main.java` | Modify |
| `src/main/java/.../scanner/NetworkScanner.java` | Modify |
| `src/main/java/.../scanner/DeviceScanner.java` | Modify |
| `src/main/java/.../repository/ScanRepository.java` | Modify |
