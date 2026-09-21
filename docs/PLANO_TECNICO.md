# Plano Técnico — Network Monitor

> Documento de referência para o desenvolvimento do projeto. Define decisões técnicas, escopo por fase e entregáveis concretos.

---

## 1. Visão Geral

Monitor de rede doméstica em Java que:

- Descobre dispositivos ativos via ARP scan (camada 2)
- Identifica portas abertas e serviços (banner grabbing)
- Armazena histórico em SQLite com schema normalizado
- Detecta mudanças entre varreduras (dispositivo novo/sumiu, porta abriu/fechou)
- Identifica fabricante via OUI e sistema operacional via fingerprint TCP

**Stack:** Java 25, JDBC + SQLite, SLF4J/Logback, Maven, JNA (para `/proc/net/arp`)

---

## 2. Decisões Técnicas

### 2.1 ARP Scan — leitura de `/proc/net/arp`

**Decisão:** Ler o cache ARP do kernel diretamente via JNA, sem subprocess nem pcap4j.

**Justificativa:**
- Sem dependência nativa (libpcap, arp-scan)
- Funciona em qualquer Linux sem instalar nada
- Educacional: mostra como o kernel armazena mapeamentos IP→MAC
- `/proc/net/arp` já tem os dados resolvidos pelo kernel; basta parsear

**Implementação:**
```java
// JNA para ler /proc/net/arp
// Formato: IP address | HW type | Flags | HW address | Mask | Device
// HW address = MAC
```

### 2.2 Spring Boot — adiado para última fase

**Decisão:** Spring Boot será implementado apenas quando todas as camadas de coleta e persistência estiverem funcionais.

**Justificativa:**
- O valor real do projeto está na camada de rede (coleta, análise, fingerprint)
- Spring Boot é um wrapper; a lógica de negócio não depende dele
- Manter o core como biblioteca pura facilita testes e reutilização
- Poderá ser consumido por CLI, REST ou qualquer interface posteriormente

### 2.3 IPv6 — incluído no escopo

**Decisão:** O roadmap inclui suporte a IPv6 em fase específica.

**Justificativa:**
- Redes domésticas modernas já atribuem endereços IPv6 via SLAAC/DHCPv6
- Dispositivos móveis frequentemente usam IPv6 em paralelo com IPv4
- A ausência de IPv6 significa dispositivos invisíveis ao scanner

**Implementação:**
- Fase dedicada para dual-stack (IPv4 + IPv6)
- ARP scan continua exclusivo para IPv4 (ARP é protocolo L2 para IPv4; IPv6 usa NDP - Neighbor Discovery Protocol)
- Para IPv6: scanner via ICMPv6 Neighbor Solicitation (requer privilégio) ou leitura do cache NDP (`ip -6 neigh`)

### 2.4 Identidade de dispositivos

**Decisão:** MAC address é a chave primária da entidade `device`.

**Justificativa:**
- IP é dinâmico (DHCP), MAC é fixo (hardware)
- OUI dos 3 primeiros bytes do MAC identifica o fabricante
- Resolve o problema de falsos positivos no changelog

### 2.5 Schema do banco

**Decisão:** Schema normalizado com 3 tabelas principais.

```sql
CREATE TABLE scan (
    id          INTEGER PRIMARY KEY,
    executed_at TEXT NOT NULL,
    strategy    TEXT NOT NULL
);

CREATE TABLE device (
    mac         TEXT PRIMARY KEY,
    ip          TEXT,
    hostname    TEXT,
    vendor      TEXT,
    os_guess    TEXT,
    first_seen  TEXT,
    last_seen   TEXT
);

CREATE TABLE scan_port (
    scan_id   INTEGER REFERENCES scan(id),
    device_id TEXT REFERENCES device(mac),
    port      INTEGER,
    banner    TEXT,
    service   TEXT,
    PRIMARY KEY (scan_id, device_id, port)
);
```

### 2.6 Concorrência

**Decisão:** Virtual threads (Java 21+).

**Justificativa:**
- Substitui pools de threads fixos por `Executors.newVirtualThreadPerTaskExecutor()`
- Escala melhor para operações de I/O bloqueante (conexões de rede)
- Mais simples e moderno

### 2.7 Testes

**Decisão:** Interface `SocketFactory` para injeção de dependência em testes.

**Justificativa:**
- Permite testar estratégias de scan sem rede real
- Mock de portas abertas/fechadas via implementação fake
- Testes unitários rápidos e determinísticos

**Implementação:**
```java
public interface SocketFactory {
    Socket create(String host, int port) throws IOException;
}

// Produção: usa Socket real
// Testes: usa SocketFake que simula portas abertas/fechadas
```

---

## 3. Arquitetura

### 3.1 Módulos

```
network-monitor/
├── core/
│   ├── scanner/
│   │   ├── DeviceScanner          # Descoberta via ARP
│   │   ├── ArpScanner             # Leitura /proc/net/arp
│   │   ├── PortScanStrategy       # Interface
│   │   ├── QuickScanStrategy      # Portas comuns
│   │   ├── StableScanStrategy     # 1-32767
│   │   ├── FullScanStrategy       # 1-65535
│   │   └── NetworkScanner         # Orquestrador
│   ├── banner/
│   │   └── BannerGrabber          # Coleta banner pós-connect
│   ├── fingerprint/
│   │   └── OsFingerprinter        # TTL/window/seq analysis
│   ├── detector/
│   │   └── ChangeDetector         # Diff entre snapshots
│   ├── model/
│   │   ├── Device
│   │   ├── Scan
│   │   └── ScanPort
│   └── repository/
│       └── ScanRepository         # JDBC + SQLite
├── cli/
│   └── Main                       # Entrada CLI
└── (futuro) api/                  # Spring Boot (fase tardia)
```

### 3.2 Fluxo de Execução

```
1. ArpScanner → lista de (IP, MAC) da rede local
2. DeviceScanner → resolve hostname via DNS reverso
3. PortScanStrategy → portas abertas por dispositivo
4. BannerGrabber → serviço + versão nas portas abertas
5. OsFingerprinter → SO provável via TTL/window
6. ScanRepository → persiste snapshot
7. ChangeDetector → compara com scan anterior
8. Output → console (CLI) ou futuro REST API
```

---

## 4. Roadmap

### Fase 0 — Fundação

**Entregáveis:**
- [ ] Schema normalizado (scan, device, scan_port)
- [ ] Substituir `LocalDateTime.now()` por UUID/autonumber no scan
- [ ] Interface `SocketFactory` + `SocketFake` para testes
- [ ] Testes unitários para `ChangeDetector` com dados fabricados
- [ ] Configuração externa (subnet, timeouts, portas comuns)
- [ ] Mover `main` para `Main.java`, criar `ScannerService` como fachada
- [ ] Virtual threads nos pools existentes

**Critério de conclusão:** Todos os testes passam, schema criado corretamente, config externa funcional.

### Fase 1 — Identidade dos Dispositivos

**Entregáveis:**
- [ ] `ArpScanner` lendo `/proc/net/arp` via JNA
- [ ] `DeviceScanner` usando ARP ao invés de `InetAddress.isReachable()`
- [ ] Tabela `device` populada com MAC, vendor (OUI), hostname
- [ ] Detecção automática de subnet via `DatagramSocket` em `8.8.8.8:53`
- [ ] Changelog usando MAC como chave (não IP)

**Critério de conclusão:** Scanner identifica dispositivos com MAC + fabricante + hostname, changelog não gera falsos positivos com mudança de IP.

### Fase 2 — Fingerprint de Serviços

**Entregáveis:**
- [ ] `BannerGrabber` — coleta banner pós-connect (SSH, HTTP, SMTP)
- [ ] Campo `banner` e `service` na tabela `scan_port`
- [ ] `OsFingerprinter` — análise de TTL e window size via pcap4j
- [ ] Campo `os_guess` na tabela `device`
- [ ] Output formatado com identificação de serviço

**Critério de conclusão:** Output mostra `IP → MAC → fabricante → SO → porta → serviço/versão`.

### Fase 3 — IPv6

**Entregáveis:**
- [ ] `NdScanner` — leitura de cache NDP via `ip -6 neigh`
- [ ] Dual-stack no output (IPv4 + IPv6)
- [ ] Correlação de dispositivos entre IPv4 e IPv6 (mesmo MAC)

**Critério de conclusão:** Dispositivos IPv6 aparecem no output alongside IPv4.

### Fase 4 — Telemetria (opcional)

**Entregáveis:**
- [ ] Export de métricas no formato Prometheus
- [ ] Dashboard básico em Grafana (dispositivos online, portas abertas)
- [ ] Análise de padrões ao longo do tempo (uptime, rotinas)

**Critério de conclusão:** Métricas visíveis no Grafana, dados históricos consultáveis.

### Fase 5 — Segurança (opcional)

**Entregáveis:**
- [ ] Mini-NIDS: alertas via Telegram bot
- [ ] Honeypot canário (serviço fake que registra tentativas)
- [ ] Correlação CVE com banners coletados
- [ ] Histerese no ChangeDetector (N scans seguidos antes de alertar)

**Critério de conclusão:** Alertas funcionando, honeypot capturando tentativas.

### Fase 6 — Interface Web (última)

**Entregáveis:**
- [ ] Spring Boot com REST API
- [ ] `@Scheduled` para varredura contínua
- [ ] Dashboard web (Thymeleaf ou Vue)
- [ ] Spring Security com login básico

**Critério de conclusão:** App acessível via browser, autenticada, com scan agendado.

---

## 5. Convenções

### Git

- Commits semânticos: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`
- Branch principal: `main`
- Branches de feature: `fase/0-schema`, `fase/1-arp-scan`, etc.

### Código

- Java 25 com virtual threads
- Formatação: Google Java Style Guide
- Testes: JUnit 5 com naming descritivo (`shouldDetectNewDeviceWhenMacIsUnknown`)
- Logger: SLF4J com variável `log` estática

### Banco

- Migrations manuais (SQL files em `src/main/resources/db/`)
- SQLite para desenvolvimento, possibilidade de migração para PostgreSQL

---

## 6. Referências

- [CONCEITO.md](./CONCEITO.md) — documento original com explicações conceituais
- [ARP cache no Linux](https://www.kernel.org/doc/Documentation/networking/arp_cache.txt)
- [Java Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)
- [OUI lookup](https://macvendors.com/)
