# Phase 1 — Device Identity

> Identify devices by MAC address, manufacturer (OUI) and hostname instead of IP. Eliminate false positives in changelog when IP changes via DHCP.

---

## Current State

### How discovery works today

`DeviceScanner.scanRange()` uses `InetAddress.isReachable()` which falls back to TCP connect on port 7 (echo) when ICMP is unavailable. This is unreliable and identifies devices only by IP.

### Problem

IP addresses are dynamic (DHCP). A device can change IP between scans, causing false "new device" events in the changelog. The real identifier for a device on LAN is the MAC address.

---

## Tasks

### T1. ArpScanner — read /proc/net/arp

Create `ArpScanner` class to read the kernel's ARP cache.

**File:** `src/main/java/com/gabriel/networkmonitor/scanner/ArpScanner.java`

**Approach:**
- Read `/proc/net/arp` line by line
- Parse columns: IP address, HW type, Flags, HW address (MAC), Mask, Device
- Return list of `ArpEntry` objects (IP, MAC, interface)

**File:** `src/main/java/com/gabriel/networkmonitor/model/ArpEntry.java`

```java
public record ArpEntry(String ip, String mac, String device) {}
```

**Details:**
- `/proc/net/arp` format:
  ```
  IP address       HW type     Flags       HW address            Mask     Device
  192.168.0.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        enp3s0
  ```
- Filter by flags `0x2` (complete entries only)
- Normalize MAC to lowercase with colons

---

### T2. OUI lookup — identify manufacturer

Create `OuiLookup` class to resolve MAC prefix to manufacturer name.

**File:** `src/main/java/com/gabriel/networkmonitor/network/OuiLookup.java`

**Approach:**
- Use a bundled CSV file with OUI mappings (first 3 bytes of MAC → vendor)
- Load into memory on startup (small dataset, ~30KB)
- Lookup by extracting first 6 hex chars of MAC

**File:** `src/main/resources/oui.csv`

Source: https://standards-oui.ieee.org/ or Wireshark's `manuf` file

**Format:**
```
aa:bb:cc,Apple,Inc.
dd:ee:ff,Samsung Electronics
```

---

### T3. Hostname resolution

Add hostname resolution to discovered devices.

**Approach:**
- Use `InetAddress.getByName(ip).getHostName()` for reverse DNS
- Store in `device.hostname` column
- Handle failures gracefully (hostname may be null)

**Affected files:**
- `DeviceScanner.java` — resolve hostname after ARP discovery

---

### T4. Update DeviceScanner to use ARP

Replace `InetAddress.isReachable()` with `ArpScanner`.

**Current flow:**
```
for each IP 1-254:
  ping via isReachable()
```

**New flow:**
```
ArpScanner.scan() → list of (IP, MAC)
for each entry:
  resolve hostname
  store device with MAC
```

**Affected files:**
- `DeviceScanner.java` — rewrite `scanRange()` to use `ArpScanner`
- `NetworkScanner.java` — adapt to new device model

---

### T5. Update schema for MAC-based identity

Modify `device` table to use MAC as primary key (already defined in Phase 0).

**Ensure:**
- `device.mac` is populated with real MAC from ARP
- `device.vendor` is populated from OUI lookup
- `device.hostname` is populated from reverse DNS
- `scan_port.device_id` references `device.mac`

**Migration:** Update `V001__schema_normalized.sql` if needed (already correct).

---

### T6. Auto-detect subnet

Create utility to detect local subnet automatically.

**Approach:**
```java
DatagramSocket socket = new DatagramSocket();
socket.connect(InetAddress.getByName("8.8.8.8"), 53);
String localIp = socket.getLocalAddress().getHostAddress();
// Extract subnet from localIp (e.g., "192.168.0.15" → "192.168.0")
```

**File:** `src/main/java/com/gabriel/networkmonitor/network/SubnetDetector.java`

**Details:**
- If subnet is provided via CLI, use it
- Otherwise, auto-detect from local IP
- Store in config as fallback

---

### T7. Update ChangeDetector to use MAC

Modify change detection to use MAC as device identifier instead of IP.

**Current behavior:**
- Compares devices by IP
- IP change → false "new device" event

**New behavior:**
- Compares devices by MAC
- IP change → update IP in device record, no false event

**Affected files:**
- `ChangeDetector.java` — use MAC as key in `indexForIp()` → `indexForMac()`
- `ScanRepository.java` — update queries to use MAC

---

### T8. Output formatting

Update console output to show MAC, vendor and hostname.

**Current:**
```
192.168.0.10 -> portas abertas: [22, 80, 443]
```

**New:**
```
[router] TP-Link (aa:bb:cc:dd:ee:ff) - 192.168.0.1
  • 80/tcp   http
```

---

## Implementation Order

1. T1 (ArpScanner) + T2 (OUI lookup)
3. T3 (hostname) + T4 (update DeviceScanner)
4. T5 (schema validation)
5. T6 (auto-detect subnet)
6. T7 (ChangeDetector MAC-based)
7. T8 (output formatting)

---

## Acceptance Criteria

- [ ] ArpScanner reads /proc/net/arp and returns IP + MAC pairs
- [ ] OUI lookup resolves MAC prefix to manufacturer name
- [ ] DeviceScanner uses ARP instead of isReachable()
- [ ] device table populated with real MAC, vendor, hostname
- [ ] ChangeDetector uses MAC as device key
- [ ] IP change does not produce false "new device" event
- [ ] Auto-detect subnet works when no CLI argument provided
- [ ] Output shows MAC, vendor and hostname

---

## Files Created/Modified

| File | Action |
|---|---|
| `src/main/java/.../scanner/ArpScanner.java` | Create |
| `src/main/java/.../model/ArpEntry.java` | Create |
| `src/main/java/.../network/OuiLookup.java` | Create |
| `src/main/resources/oui.csv` | Create |
| `src/main/java/.../network/SubnetDetector.java` | Create |
| `src/main/java/.../scanner/DeviceScanner.java` | Modify |
| `src/main/java/.../detector/ChangeDetector.java` | Modify |
| `src/main/java/.../repository/ScanRepository.java` | Modify |
| `src/main/java/.../scanner/NetworkScanner.java` | Modify |
| `src/main/java/.../Main.java` | Modify |
