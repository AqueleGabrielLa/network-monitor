# Phase 2 — Service Fingerprint

> Identify which service and version is running behind each open port (banner grabbing) and infer the device's operating system via TCP stack characteristics.

---

## Current State

### What the scanner knows today

After Phase 1, each device is identified by MAC, vendor and hostname, and the port scan reports only the numeric port:

```
[router] TP-Link (aa:bb:cc:dd:ee:ff) - 192.168.0.1
  • 80/tcp
  • 443/tcp
```

There is no information about *what* is listening on those ports (nginx? Apache? which version?) or what OS the device runs.

### Relevant structures

- `PortScanStrategy.scanIp()` returns `List<Integer>` — only port numbers.
- `RealSocketFactory.create()` opens a connection and returns it; the strategies immediately check reachability and close the socket without reading any data.
- `scan_port` table columns: `scan_id, device_id, port` — no room for banner/service.
- `device` table columns: `mac, ip, hostname, vendor, first_seen, last_seen` — no `os_guess`.
- `ScannerService.describe()` prints only port numbers.

### Problem

Ports alone do not reveal the service. Port 80 could be nginx, Apache, a router admin panel or an application server. Version information is essential for the future CVE correlation (Phase 5/6 in the roadmap) and for a useful dashboard. Likewise, knowing the OS helps distinguish a Linux NAS from a Windows workstation or an IoT device.

---

## Background

### Banner grabbing

Many services send a greeting line immediately after the TCP handshake:

| Service | Example banner |
|---|---|
| SSH | `SSH-2.0-OpenSSH_9.6p1 Ubuntu-3ubuntu13` |
| HTTP | Often silent on connect; requires sending `GET / HTTP/1.0\r\n\r\n` to elicit `Server: nginx/1.25.3` |
| SMTP | `220 mail.example.com ESMTP Postfix` |
| FTP | `220 (vsFTPd 3.0.5)` |
| Redis | On error: `-ERR unknown command` (or ping response) |
| MySQL | Binary greeting packet starting with protocol version + server version string |

**Two modes:**

1. **Passive read** — connect and read whatever the server sends without us writing anything (SSH, SMTP, FTP, Redis…).
2. **Active probe** — send a minimal protocol-appropriate payload to provoke a response (HTTP `GET /`, SMTP `EHLO`, etc.).

HTTP is the most common case and needs the active probe; most other services greet first.

### OS fingerprint via TCP

Each OS implements the TCP stack with distinct defaults:

| Indicator | Linux | Windows | macOS/iOS |
|---|---|---|---|
| Initial TTL | 64 | 128 | 64 |
| TCP window size | 29200 (varies) | 64240 / 65535 | 65535 |

**Methods:**

- **Passive (p0f-style)** — observe TTL/window of packets already flowing. Requires packet capture.
- **Active (nmap -O style)** — send crafted probes (e.g., a SYN or ACK to a closed port) and analyze TTL, window, TCP options in the SYN-ACK/RST response.

**Java limitation:** the plain `java.net.Socket` API does not expose TTL, window size or TCP options of incoming packets. Reading those fields requires raw packet capture (pcap) or raw sockets, both needing native libraries and usually elevated privileges.

**Options for this project:**

| Option | Dependency | Privilege | Fidelity |
|---|---|---|---|
| `pcap4j` + libpcap | Native (libpcap) | Root/capabilities for capture | High — full packet view |
| Active probe via `pcap4j` | Native | Root for send on raw socket | High |
| Infer from service banners only | None | None | Low — heuristic |
| Skip OS fingerprint in this phase | None | None | — |

**Recommendation:** implement **banner grabbing + service identification first** (no native deps, immediate value), and treat **OS fingerprint via pcap4j as a separate task (T5)** that can be descoped if the native dependency proves problematic on the target environment. The `device.os_guess` column is added now so the rest of the pipeline (output, persistence, changelog) is ready to consume it.

---

## Tasks

### T1. Schema migration — banner, service, os_guess

Add columns to support fingerprint data.

**File:** `src/main/resources/db/migration/V002__service_fingerprint.sql`

```sql
ALTER TABLE scan_port ADD COLUMN banner TEXT;
ALTER TABLE scan_port ADD COLUMN service TEXT;

ALTER TABLE device ADD COLUMN os_guess TEXT;
```

**Details:**
- Run alongside `V001` in `ScanRepository.initialize()` (execute in filename order).
- Use `ALTER TABLE ... ADD COLUMN` — SQLite supports it; no data loss.
- Existing rows keep `NULL` for the new columns; acceptable.

**Affected files:**
- `ScanRepository.java` — `initialize()` must run both migrations; `saveScan()` must persist `banner` and `service`; `searchByScanId()` must read them back.

---

### T2. Model — carry banner and service

Extend the model so fingerprint data flows through the pipeline.

**Option chosen:** keep `Device.openPorts` as `List<Integer>` for compatibility, and introduce a richer structure for ports that carry fingerprint data.

**File:** `src/main/java/com/gabriel/networkmonitor/model/PortInfo.java`

```java
public record PortInfo(int port, String service, String banner) {}
```

**File:** `src/main/java/com/gabriel/networkmonitor/model/Device.java`

- Add field `List<PortInfo> portInfos` (or replace `openPorts` usage progressively).
- Keep `getOpenPorts()` returning port numbers derived from `portInfos` so existing code (`ChangeDetector`, tests) keeps working.
- Add `withPortInfos(List<PortInfo>)` analogous to `withPorts()`.

**Migration strategy:** minimal disruption — `ChangeDetector` continues to compare port numbers; banner/service changes are displayed but do not (yet) generate change events (optional enhancement, see T7).

---

### T3. BannerGrabber — collect banner after connect

Create the component that connects to an open port and captures the service greeting / response.

**File:** `src/main/java/com/gabriel/networkmonitor/banner/BannerGrabber.java`

**Contract:**

```java
public class BannerGrabber {
    // Returns banner text (trimmed, first line or short excerpt), or null if none
    public String grab(String host, int port);
}
```

**Approach:**

1. Reuse `SocketFactory` (same injected instance as the scan strategies) to open a connection — keeps tests deterministic via `SocketFake`.
2. **Passive read:** set `socket.setSoTimeout(configuredMs)`, read up to N bytes from `InputStream` for a short window (~500–2000 ms).
3. **Active probe fallback:** if passive read yields nothing and the port is in the "needs probe" set (e.g., HTTP 80/8080/443, or configured), send a minimal payload:
   - HTTP: `GET / HTTP/1.0\r\nHost: <host>\r\n\r\n`
   - SMTP (port 25/587): `EHLO scanner\r\n` (optional; usually greets first)
4. Truncate and sanitize the banner (single line, max ~200 chars, strip control characters) for storage.
5. Always close the socket in `finally`.
6. On any `IOException`/timeout, return `null` — never fail the whole scan because of one port.

**Configuration keys** (add to `application.properties`):

```properties
# Banner grabbing
banner.enabled=true
banner.timeout=1500
banner.max-bytes=512
banner.probe-ports=80,8080,8000,443,8443
```

**Testability:** the fake socket factory used in tests can supply pre-scripted banner bytes so `BannerGrabberTest` runs without network.

---

### T4. Service identification — banner + port mapping

Derive a human-readable service name and version from the banner, with a known-port fallback.

**File:** `src/main/java/com/gabriel/networkmonitor/banner/ServiceIdentifier.java`

**Approach:**

1. **Parse banner** with ordered rules (first match wins):
   - `SSH-2.0-...` → service `ssh`, keep version suffix
   - `HTTP/1.x ...` or contains `Server:` header → `http`, extract server token (e.g., `nginx/1.25.3`)
   - `220 ... ESMTP ...` → `smtp`
   - `220 ... FTP` / `vsFTPd` → `ftp`
   - `+PONG` / Redis error → `redis`
   - MySQL binary greeting (starts with protocol byte + version string) → `mysql`
2. **Fallback by well-known port** when banner is null or unparseable:

   | Port | Service |
   |---|---|
   | 22 | ssh |
   | 25, 587 | smtp |
   | 53 | dns |
   | 80, 8080, 8000 | http |
   | 110 | pop3 |
   | 143 | imap |
   | 443, 8443 | https |
   | 445 | smb |
   | 3306 | mysql |
   | 5432 | postgresql |
   | 6379 | redis |

3. Return a small record: `ServiceInfo(String service, String version)` or just the formatted label `ssh` / `nginx 1.25.3`.

**Output format for service:** prefer `service` (short name) in DB; compose display label in `ScannerService.describe()`.

---

### T5. OS fingerprint via pcap4j (optional / stretch)

**Goal:** populate `device.os_guess` with a best-effort OS label (e.g., `Linux`, `Windows`, `macOS`).

**File:** `src/main/java/com/gabriel/networkmonitor/fingerprint/OsFingerprinter.java`

**Approach (active probe):**

1. Open a pcap capture handle on the primary interface (requires libpcap + privileges).
2. Send a probe packet (e.g., SYN to a closed port on the target, or ACK) using raw socket / pcap send.
3. Capture the response; read TTL and TCP window size from the IP/TCP headers.
4. Map to OS guess:

   | TTL observed (initial) | Likely OS |
   |---|---|
   | 64 (or 63/62 after hops) | Linux / macOS / iOS |
   | 128 (or 127/126…) | Windows |
   | 255 | Some network devices |

   Combine with window size for finer granularity (Linux 29200 vs Windows 64240 vs macOS 65535).

**Dependencies to add to `pom.xml`:**

```xml
<dependency>
    <groupId>org.pcap4j</groupId>
    <artifactId>pcap4j-core</artifactId>
    <version>1.8.2</version>
</dependency>
<dependency>
    <groupId>org.pcap4j</groupId>
    <artifactId>pcap4j-packetfactory-x</artifactId>
    <version>1.8.2</version>
</dependency>
```

**Risk:** native libpcap must be installed (`libpcap0.8` / `libpcap`); capture usually needs root or `cap_net_raw`. On restricted environments this task may be deferred — hence the `banner.enabled`-style kill switch:

```properties
fingerprint.os.enabled=false
```

**Fallback when disabled:** leave `os_guess` as `NULL`; output simply omits the OS field.

**Testing:** unit-test the TTL/window → OS mapping table as a pure function; integration with real packets is out of scope for unit tests.

---

### T6. Wire fingerprint into the scan pipeline

Integrate BannerGrabber + ServiceIdentifier into the orchestration.

**Affected files:**

- `ScannerService.executeFullCycle()`:
  1. Discovery (unchanged)
  2. Port scan (unchanged) → open ports per device
  3. **New:** for each open port, `bannerGrabber.grab(ip, port)` → `serviceIdentifier.identify(port, banner)`
  4. Build `PortInfo` list per device
  5. Optional: `osFingerprinter.guess(ip)` → set `device.osGuess`
  6. Persist (banner + service + os_guess)
  7. Diff (unchanged)
  8. Print enriched output

- `NetworkScanner.fullScan()` — either stays port-only and `ScannerService` does the banner pass afterwards, or receives `BannerGrabber` and does both in one loop (prefer **separate pass after port scan** to keep strategies single-purpose).

**Suggested flow:**

```
DeviceScanner.scanRange(subnet)        → List<Device> (no ports)
NetworkScanner.fullScan(devices)       → List<Device> (ports only)
BannerPass.enrich(devices)             → List<Device> (ports + service + banner)
[optional] OsFingerprinter.guess(...)   → Device.osGuess
ScanRepository.saveScan(devices)
ChangeDetector.detect(previous, current)
```

---

### T7. Output and ChangeDetector enrichment

**Output (`ScannerService.describe()`):**

```
[router] TP-Link (aa:bb:cc:dd:ee:ff) - 192.168.0.1 [Linux]
  • 22/tcp   ssh  OpenSSH_9.6p1
  • 80/tcp   http nginx/1.25.3
  • 443/tcp  https
```

- Show `service` next to the port; append version/banner excerpt when present.
- Show `os_guess` in brackets after IP when present.

**ChangeDetector (optional enhancement):**

- Current events: new device, device gone, IP changed, port opened/closed.
- **New optional event:** `[SERVIÇO MUDOU]` when `service` (or normalized banner fingerprint) changes for the same MAC+port — indicates upgrade/downgrade or service swap on same port.
- Keep comparing by port number for opened/closed events so existing tests remain valid.

---

### T8. Tests

**File:** `src/test/java/com/gabriel/networkmonitor/banner/BannerGrabberTest.java`

1. `shouldReturnBannerWhenServerSendsGreeting` — fake socket provides `SSH-2.0-OpenSSH_9.6`
2. `shouldReturnNullOnTimeout` — fake socket throws `SocketTimeoutException`
3. `shouldSendHttpProbeWhenPassiveReadIsEmpty` — ports in `probe-ports` get payload written
4. `shouldTruncateLongBanner` — banner longer than `max-bytes` is cut
5. `shouldReturnNullWhenConnectionRefused` — factory throws `IOException`

**File:** `src/test/java/com/gabriel/networkmonitor/banner/ServiceIdentifierTest.java`

1. `shouldIdentifySshFromBanner`
2. `shouldIdentifyHttpFromServerHeader`
3. `shouldIdentifySmtpFromEsmtpBanner`
4. `shouldFallbackToKnownPortWhenBannerIsNull` — port 22 → `ssh`
5. `shouldReturnUnknownForUnmappedPort` — port 54321, no banner → `null` or `unknown`

**File:** `src/test/java/com/gabriel/networkmonitor/fingerprint/OsFingerprinterTest.java` (if T5 implemented)

1. `shouldGuessLinuxWhenTtlIs64`
2. `shouldGuessWindowsWhenTtlIs128`
3. `shouldReturnUnknownWhenTtlIsAmbiguous`

**File:** `src/test/java/.../detector/ChangeDetectorTest.java`

6. `shouldDetectServiceChange` (if T7 service-change event is implemented)

---

## Implementation Order

1. T1 (schema) + T2 (model)
2. T3 (BannerGrabber) + T4 (ServiceIdentifier) + T8 tests for both
3. T6 (pipeline wiring) + T7 (output)
4. T5 (OS fingerprint — optional, after the rest is stable)

---

## Acceptance Criteria

- [ ] Migration `V002` adds `banner`, `service`, `os_guess` without breaking `V001` data
- [ ] BannerGrabber captures banners for SSH/SMTP/HTTP without hanging the scan
- [ ] HTTP ports receive a minimal GET probe when passive read is empty
- [ ] ServiceIdentifier maps common ports and banners to service names
- [ ] `scan_port.banner` and `scan_port.service` are persisted and loaded correctly
- [ ] Output shows service (and version when available) next to each port
- [ ] `device.os_guess` populated when fingerprint enabled; `NULL` otherwise
- [ ] Banner/service failures on one port never abort the full scan
- [ ] Unit tests run without real network (fake sockets only)
- [ ] `fingerprint.os.enabled=false` skips pcap entirely (no native dependency required to run default mode)

---

## Files Created/Modified

| File | Action |
|---|---|
| `src/main/resources/db/migration/V002__service_fingerprint.sql` | Create |
| `src/main/java/.../model/PortInfo.java` | Create |
| `src/main/java/.../banner/BannerGrabber.java` | Create |
| `src/main/java/.../banner/ServiceIdentifier.java` | Create |
| `src/main/java/.../fingerprint/OsFingerprinter.java` | Create (T5, optional) |
| `src/main/java/.../model/Device.java` | Modify |
| `src/main/java/.../ScannerService.java` | Modify |
| `src/main/java/.../repository/ScanRepository.java` | Modify |
| `src/main/java/.../detector/ChangeDetector.java` | Modify (optional service-change event) |
| `src/main/resources/application.properties` | Modify |
| `pom.xml` | Modify (pcap4j, only if T5 implemented) |
| `src/test/java/.../banner/BannerGrabberTest.java` | Create |
| `src/test/java/.../banner/ServiceIdentifierTest.java` | Create |
| `src/test/java/.../fingerprint/OsFingerprinterTest.java` | Create (if T5) |
| `src/test/java/.../detector/ChangeDetectorTest.java` | Modify |

---

## Out of Scope (later phases)

- **CVE correlation** with banners — roadmap Phase 5/6.
- **Passive OS fingerprint** from live traffic (p0f-style) — requires sustained capture, beyond this phase.
- **Protocol-specific probes** beyond HTTP (e.g., TLS certificate extraction for HTTPS service identity) — possible follow-up; HTTPS banner grab typically yields nothing useful without a TLS handshake.
