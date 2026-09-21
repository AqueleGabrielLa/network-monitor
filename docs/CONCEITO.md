# Network Monitor — Documento Conceitual

> Referência técnica do projeto. Explica o estado atual, problemas identificados e roteiro de evolução com as decisões de design por trás de cada componente.

---

## Índice

1. [Visão geral do projeto](#1-visão-geral-do-projeto)
2. [Estado atual](#2-estado-atual)
3. [Problemas identificados](#3-problemas-identificados)
4. [Roteiro de evolução (roadmap)](#4-roteiro-de-evolução-roadmap)
5. [Referências técnicas](#5-referências-técnicas)
6. [Arquitetura alvo](#6-arquitetura-alvo)
7. [Próximos passos](#7-próximos-passos)

---

## 1. Visão geral do projeto

Monitor de rede doméstica em Java com as seguintes funcionalidades:

- Descoberta de dispositivos ativos na rede local
- Identificação de portas e serviços abertos
- Persistência de histórico de varreduras em SQLite
- Detecção de mudanças entre varreduras consecutivas:
  - Dispositivo novo na rede
  - Dispositivo que saiu da rede
  - Porta que abriu ou fechou

### Escopo

Projeto educacional com foco em redes, protocolos, TCP/IP e segurança. Evolui de ferramenta CLI para aplicação completa conforme o domínio amadurece.

---

## 2. Estado atual

### 2.1 Fluxo de execução

```
                     1                       2                       3                     4
  [Varredura de IP]  →  [Varredura de portas]  →  [Persistência SQLite]  →  [Detecção de mudanças]
   quem está vivo?        quais portas abrem?    guarda snapshot           diff com o anterior
```

### 2.2 Módulos

| Módulo | Arquivo | Responsabilidade | Implementação |
|---|---|---|---|
| Descoberta de hosts | `DeviceScanner` | Identificar IPs ativos na faixa `192.168.x.1..254` | 50 threads, `InetAddress.isReachable(200ms)` |
| Scan de portas | `QuickScanStrategy` | 10 portas comuns por host | `Socket.connect()` paralelo |
| Scan de portas | `StableScanStrategy` | Portas 1–32767 | 100 threads, timeout 50ms |
| Scan de portas | `FullScanStrategy` | Portas 1–65535 | Mesmo mecanismo |
| Orquestração | `NetworkScanner` | Executa discovery → scan → persistência → diff | Sequencial entre dispositivos |
| Modelo | `Device` | Dispositivo = IP + lista de portas abertas | POJO imutável |
| Persistência | `ScanRepository` | CRUD no SQLite | JDBC puro, portas em CSV |
| Diff | `ChangeDetector` | Gera eventos de mudança entre snapshots | Comparação de conjuntos |

### 2.3 Decisões de design

- **Strategy Pattern** — `NetworkScanner` depende da interface `PortScanStrategy`. Permite alternar entre modos de varredura sem modificar o orquestrador. Base para adição de novas estratégias (ex.: SYN scan).
- **Concorrência com `ExecutorService`** — Discovery e scan de portas utilizam pools de threads fixos com `CopyOnWriteArrayList` para acumulação segura de resultados.
- **Persistência local** — SQLite via JDBC. Adequado para uso sem servidor de banco externo.

---

## 3. Problemas identificados

### 3.1 Limitações do `InetAddress.isReachable()`

O método `isReachable()` não realiza ICMP puro. Com privilégio de root, utiliza ICMP Echo Request. Sem privilégio, fallback para TCP connect na porta 7 (echo) — serviço descontinuado na maioria dos sistemas. Resultado: dispositivos podem não ser detectados.

Alternativas disponíveis:
- ARP scan — opera na camada 2, sem necessidade de ICMP
- TCP connect em portas comuns — abordagem utilizada por ferramentas como nmap
- ICMP raw via JNI/subprocess — requer privilégio de root

### 3.2 Identificação por IP

O modelo atual identifica dispositivos pelo endereço IP. Endereços IP são atribuídos dinamicamente por DHCP, podendo mudar entre reinicializações. Isso gera falsos positivos na detecção de mudanças. O endereço MAC é o identificador adequado para dispositivos em LAN.

### 3.3 Schema não normalizado

Portas são armazenadas como string CSV:

```
ip TEXT, open_ports TEXT  -- "22,80,443"
```

Isso impede consultas por portas específicas, dificulta a detecção de mudanças e não permite tratar cada varredura como sessão com metadados próprios.

### 3.4 Precisão de timestamp

`LocalDateTime.now().toString()` possui precisão de segundo. Duas varreduras no mesmo segundo compartilham o mesmo `date_hour`, impedindo distinção no `searchLastTimestamps()`.

### 3.5 Código e testes

- `Main.java` está vazio; o método `main` está em `NetworkScanner`
- `ChangeDetectorTest` não contém implementação

### 3.6 Outros pontos

- `isReachable()` com timeout de 30s pode retornar lista incompleta silenciosamente
- Scan sequencial entre dispositivos multiplica o tempo total pelo número de hosts
- Cada scan abre nova conexão com o banco (oportunidade para implementar pooling)
- Configurações (timeouts, portas, faixa de IP) estão hardcoded

---

## 4. Roteiro de evolução (roadmap)

> Fases ordenadas por complexidade crescente. A sequência prioriza base sólida antes de funcionalidades avançadas.

---

### Fase 0 — Fundação

**Objetivo:** Estabelecer base testável, schema normalizado e configuração externa.

**Ações:**

1. **Normalizar schema** — três tabelas:

```sql
CREATE TABLE scan (
    id         INTEGER PRIMARY KEY,
    executed_at TEXT NOT NULL,
    strategy   TEXT NOT NULL
);

CREATE TABLE device (
    mac      TEXT PRIMARY KEY,
    ip       TEXT,
    hostname TEXT,
    vendor   TEXT,
    first_seen TEXT,
    last_seen  TEXT
);

CREATE TABLE scan_port (
    scan_id  INTEGER REFERENCES scan(id),
    device_id INTEGER REFERENCES device(id),
    port     INTEGER,
    PRIMARY KEY (scan_id, device_id, port)
);
```

2. **Gerar ID de scan por execução** — substituir string `date_hour` por UUID ou autonumber. Armazenar `executed_at` como metadado.

3. **Implementar testes** — criar interface `SocketFactory` para injeção de dependência. Testar estratégias com sockets fake. Testar `ChangeDetector` com dados fabricados.

4. **Configuração externa** — arquivo de propriedades para: subnet, timeouts, lista de portas comuns, pool sizes, caminho do banco.

5. **Detecção automática de subnet** — criar `DatagramSocket`, conectar em `8.8.8.8:53` (sem envio), ler endereço local do socket.

6. **Refatoração** — mover `main` para `Main.java`. Criar `ScannerService` como fachada.

---

### Fase 1 — Identidade dos Dispositivos

**Objetivo:** Identificar dispositivos por MAC, fabricante e hostname.

#### ARP Scan

Protocolo ARP opera na camada 2 (Ethernet) e resolve endereços IP para MAC. Funcionamento:

1. Broadcast na rede: "Quem tem o IP X? Me diga seu MAC."
2. Host dono do IP responde com seu MAC
3. MAC fica armazenado no cache ARP do sistema

Implementação em Java:
- `/proc/net/arp` ou `ip neigh` — leitura do cache ARP do kernel (recomendado)
- `arp-scan` via subprocess
- `pcap4j` — captura de pacotes raw

#### Hostname e mDNS

- **Reverse DNS** — `InetAddress.getHostName()` resolve PTR record
- **mDNS** — protocolo de descoberta em局域网 (AirDrop, Chromecast, impressoras)

#### Entregáveis

- Tabela `device` com `mac, vendor, hostname, first_seen, last_seen`
- Changelog utilizando MAC como chave (eliminação de falsos positivos)

---

### Fase 2 — Fingerprint de Serviços

**Objetivo:** Identificar serviço e versão nas portas abertas.

#### Banner Grabbing

Muitos serviços enviam linha de saudação após conexão. Exemplos:
- SSH: `SSH-2.0-OpenSSH_9.6p1 Ubuntu-3`
- HTTP: `Server: nginx/1.25.3`
- SMTP: `220 mail.xyz ESMTP Postfix`

Implementação: após `Socket.connect()`, ler `InputStream` por ~2 segundos.

#### Fingerprint de SO

Cada sistema operacional implementa pilha TCP com características próprias:

| Indicador | Linux | Windows | macOS/iOS |
|---|---|---|---|
| TTL inicial | 64 | 128 | 64 |
| Janela TCP | 29200 | 64240/65535 | 65535 |
| MSS | 1460 | 1460 | 1460 |

Métodos:
- Passivo (ex.: `p0f`) — observação de tráfego existente
- Ativo (ex.: `nmap -O`) — envio de pacotes específicos e análise de respostas

Implementação em Java: captura de pacotes via `pcap4j`.

#### Entregáveis

- Campo `service` nos resultados do scan
- Identificação de fabricante via OUI (3 primeiros bytes do MAC)
- Campo `os_guess` na tabela `device`

---

### Fase 3 — IPv6

**Objetivo:** Suporte a dual-stack (IPv4 + IPv6).

Pontos de atenção:
- ARP é protocolo L2 exclusivo para IPv4
- IPv6 utiliza NDP (Neighbor Discovery Protocol) — ICMPv6
- Cache NDP acessível via `ip -6 neigh`
- Dispositivos podem ter endereços IPv6 e IPv4 simultaneamente

---

### Fase 4 — Spring Boot

**Objetivo:** Transformar ferramenta CLI em aplicação web.

#### Componentes

1. **REST API**
   - `GET /api/devices` — lista dispositivos com MAC, fabricante, status
   - `GET /api/scans` — histórico de varreduras
   - `GET /api/scans/{id}/changes` — eventos de mudança
   - `POST /api/scan` — dispara scan assíncrono
   - `GET /api/scans/live` — SSE para eventos em tempo real

2. **Agendamento** — `@Scheduled(fixedDelay = 300000)` para varredura periódica

3. **Alertas** — notificações via Telegram Bot ou e-mail

4. **Persistência** — Spring Data JDBC/JPA sobre schema existente

5. **Interface** — Thymeleaf ou Vue/React + Spring Security

#### Banco de dados

SQLite para uso local (1 usuário, scans a cada 5 min). Migração para PostgreSQL quando necessário, com HikariCP para connection pooling.

---

### Fase 5 — Telemetria

**Objetivo:** Visualização de dados e análise de padrões.

- Exportação de métricas no formato **Prometheus**
- Painéis em **Grafana**: dispositivos online, portas abertas, taxa de eventos
- Análise de padrões temporais: rotinas de dispositivos, disponibilidade

---

### Fase 6 — Segurança

**Objetivo:** Aplicação de conceitos de segurança na rede local.

#### Componentes

1. **Mini-NIDS** — varredura agendada com alertas
   - Histerese: detectar mudança após N varreduras consecutivas
   - Distinção entre dispositivos conhecidos e desconhecidos

2. **Honeypot** — serviço fictício que registra tentativas de conexão

3. **Correlação CVE** — cruzamento de banners coletados com banco de vulnerabilidades

4. **HIDS** — verificação de integridade de arquivos locais via SHA-256

---

## 5. Referências técnicas

### 5.1 Camadas do modelo TCP/IP

```
         APLICAÇÃO          HTTP, SSH, DNS, SMTP...
         TRANSPORTE         TCP, UDP
         REDE/INTERNET      IPv4, IPv6, ICMP
         ENLACE/L2          Ethernet, ARP, Wi-Fi
         FÍSICA             cabo, ondas de rádio
```

### 5.2 Comparativo ICMP × TCP × ARP

| | ICMP (ping) | TCP connect | ARP |
|---|---|---|---|
| Camada | Rede (L3) | Transporte (L4) | Enlace (L2) |
| Mecanismo | Echo Request/Reply | Tenta abrir conexão | Query IP → MAC |
| Atravessa roteador | Sim | Sim | Não (mesma LAN) |
| Bloqueável por firewall | Sim | Sim | Difícil |
| Descobre MAC | Não | Não | Sim |

### 5.3 Three-way handshake TCP

```
Cliente                          Servidor
   |  SYN (seq=x)                  |
   |------------------------------>|
   |  SYN-ACK (seq=y, ack=x+1)     |
   |<------------------------------|
   |  ACK (ack=y+1)                |
   |------------------------------>|
   |=== conexão aberta, dados ====>|
```

`Socket.connect()` executa os 3 passos. O SYN scan interrompe após o segundo passo, enviando RST ao invés de ACK.

### 5.4 Portas efêmeras

O kernel atribui portas de origem na faixa dinâmica (Linux: `net.ipv4.ip_local_port_range`, padrão 32768–60999). `StableScanStrategy` limita varredura a 1–32767 para evitar ruído.

### 5.5 Endereço MAC e OUI

- **MAC** — 48 bits, identificador único da interface de rede
- **OUI** — 3 primeiros bytes (24 bits), identifica fabricante (registrado na IEEE)

### 5.6 Fingerprint de SO

Pilhas TCP de fabricantes diferentes utilizam valores padrão distintos para TTL, janela TCP, MSS e ordem de options. Análise desses campos permite identificação do sistema operacional.

### 5.7 Glossário

- **CVE** — Common Vulnerabilities and Exposures. Catálogo de vulnerabilidades conhecidas (MITRE).
- **Honeypot** — Serviço fictício que registra tentativas de conexão não autorizadas.
- **IDS/NIDS/HIDS** — Intrusion Detection System. NIDS monitora rede, HIDS monitora host.
- **Virtual threads** — Threads leves implementadas no Java 21+ (JEP 444).
- **SSE** — Server-Sent Events. Comunicação unidirecional servidor→cliente via HTTP.

---

## 6. Arquitetura alvo

```
            ┌─────────────────────────────────────────────────┐
            │  Spring Boot app (network-monitor-api)          │
            │  ├─ REST /api/devices, /api/scans, /api/changes │
            │  ├─ @Scheduled → scan a cada 5 min              │
            │  ├─ ChangeDetector → eventos                    │
            │  ├─ Alertas (Telegram / e-mail)                 │
            │  └─ SSE → dashboard ao vivo                     │
            └───────────────┬─────────────────────────────────┘
                            │ usa
            ┌───────────────▼─────────────────────────────────┐
            │  Core (biblioteca)                               │
            │  ├─ DeviceScanner (ICMP + TCP fallback)         │
            │  ├─ ArpScanner → MAC + fabricante               │
            │  ├─ PortScanStrategy: Quick/Stable/Full/SYN     │
            │  ├─ BannerGrabber → serviço + versão            │
            │  └─ OsFingerprinter (TTL/window/seq)            │
            └───────────────┬─────────────────────────────────┘
                            │ persiste
            ┌───────────────▼─────────────────────────────────┐
            │  SQLite dev → PostgreSQL prod                   │
            │  scan / device / scan_port                      │
            └──────────────────────────────────────────────────┘
```

### Ciclo de operação

1. A cada 5 minutos: ARP scan → lista de MACs
2. Scan de portas (modo stable) nos dispositivos conhecidos
3. Banner grabbing nas portas abertas
4. Diff com varredura anterior
5. Sem mudança: aguardar. Com mudança: gravar + notificar
6. Varredura completa (`--full`) em período noturno

---

## 7. Próximos passos

1. **[Fase 0]** Normalizar schema, implementar testes, criar `SocketFactory`
2. **[Fase 1]** Integrar ARP via `/proc/net/arp`, criar tabela `device`
3. **[Fase 1]** Detecção automática de subnet + configuração externa
4. **[Fase 2]** Banner grabbing em HTTP/SSH + identificação de serviço
