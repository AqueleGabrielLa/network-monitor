# Network Monitor

Monitor de rede doméstica desenvolvido em Java, focado em descoberta de dispositivos, reconhecimento de serviços e detecção de mudanças ao longo do tempo.

## O que ele faz

- Descoberta de dispositivos ativos na rede local via leitura do cache ARP (MAC + fabricante + hostname)
- Reconhecimento de portas/serviços abertos em cada dispositivo encontrado, com três modos de varredura (rápido, estável ou completo)
- Persistência histórica de cada varredura em banco SQLite
- Detecção automática de mudanças entre a varredura atual e a anterior (dispositivo novo, dispositivo que sumiu, porta que abriu, porta que fechou, IP que mudou)

## Exemplo de saída

```
15:37:09 [INFO] scanner.DeviceScanner - Dispositivo ativo encontrado: 192.168.1.10 [aa:bb:cc:11:22:33]
15:37:09 [INFO] scanner.DeviceScanner - Dispositivo ativo encontrado: 192.168.1.1 [00:11:32:aa:bb:cc] - Acme Networks

=== Resultado final ===
[home-router] Acme Networks (00:11:32:aa:bb:cc) - 192.168.1.1
  • 22/tcp
  • 80/tcp
  • 443/tcp

=== Mudanças detectadas desde o último scan ===
[PORTA ABRIU] aa:bb:cc:11:22:33 [192.168.1.10] abriu a porta 3000
[IP MUDOU] 192.168.1.10 -> 192.168.1.15 (MAC aa:bb:cc:11:22:33)
```

## Arquitetura

O projeto é dividido em módulos. A varredura de portas usa o padrão **Strategy**: `NetworkScanner` depende apenas da interface `PortScanStrategy`, recebida via construtor, sem saber qual implementação está por trás. A descoberta de dispositivos usa o cache ARP do kernel (camada 2), que resolve IP → MAC sem depender de ICMP.

| Módulo | Responsabilidade |
|---|---|
| `scanner.ArpScanner` | Lê o cache ARP (`/proc/net/arp`) e retorna pares IP + MAC + interface |
| `scanner.DeviceScanner` | Usa o ARP para descobrir dispositivos e resolve hostname (DNS reverso) e fabricante (OUI) |
| `network.OuiLookup` | Resolve os 3 primeiros bytes do MAC para o fabricante, via base OUI da IEEE |
| `network.SubnetDetector` | Detecta a subnet da rede automaticamente via `DatagramSocket` |
| `interfaces.PortScanStrategy` | Contrato comum entre os modos de varredura de portas |
| `scanner.QuickScanStrategy` | Testa conectividade TCP em um conjunto fixo de portas comuns por host |
| `scanner.StableScanStrategy` | Testa conectividade TCP nas portas 1–32767 por host, reduzindo o ruído de portas efêmeras |
| `scanner.FullScanStrategy` | Testa conectividade TCP em todas as portas (1–65535) por host |
| `scanner.NetworkScanner` | Orquestra os scanners acima em um fluxo único |
| `model.Device` | Representa um dispositivo com MAC, IP, hostname, fabricante e portas abertas |
| `repository.ScanRepository` | Persiste e consulta os resultados no SQLite (MAC é a chave do dispositivo) |
| `detector.ChangeDetector` | Compara dois snapshots por MAC e gera a lista de eventos de mudança |

## Stack técnica

- **Java 17+** — linguagem principal
- **JDBC + SQLite** (`sqlite-jdbc`) — persistência local, sem necessidade de servidor de banco externo
- **SLF4J + Logback** — logging estruturado (nível, timestamp, classe de origem)
- **Maven** — build e gerenciamento de dependências

## Como rodar

Pré-requisitos: JDK 17+ e Maven instalados.

```bash
git clone <url-do-repositorio>
cd network-monitor
mvn clean package
```

O subnet da rede é opcional: se não for passado, é detectado automaticamente a partir do IP local (ex: IP `192.168.1.15` → subnet `192.168.1`). Para descobrir o prefixo da sua rede manualmente, rode `ipconfig` (Windows) ou `ip a` (Linux/Mac).

O modo de varredura de portas é selecionado via flag:
- Ausente ou `--quick` — varredura rápida (conjunto fixo de portas comuns), modo padrão
- `--stable` — varredura das portas 1–32767, cobertura maior com menos ruído de portas efêmeras
- `--full` — varredura completa, todas as portas de 1 a 65535

**Rodando pela IDE (IntelliJ):**
Em `Run → Edit Configurations`, adicione opcionalmente a subnet (e/ou `--stable` / `--full`) no campo "Program arguments" (ex: `192.168.1` ou `--full`) e execute a classe `Main`.

**Rodando via terminal:**
```bash
# modo rápido (padrão) — testa um conjunto fixo de portas comuns
java -jar target/network-monitor-1.0-SNAPSHOT.jar

# modo estável — testa as portas 1 a 32767
java -jar target/network-monitor-1.0-SNAPSHOT.jar --stable

# modo completo com subnet explícita
java -jar target/network-monitor-1.0-SNAPSHOT.jar 192.168.1 --full
```

## Configuração

Os valores padrão ficam no arquivo `src/main/resources/application.properties`:

| Chave | Padrão | O que controla |
|---|---|---|
| `scanner.subnet` | `192.168.1` | subnet usada quando não é auto-detectada |
| `scanner.timeout` | `200` | timeout (ms) por porta testada |
| `scanner.common-ports` | `22,80,443,8000,8080,8443` | portas do modo `--quick` |
| `scanner.stable-wait` | `90` | tempo máximo de espera (s) do `--stable` |
| `scanner.full-wait` | `180` | tempo máximo de espera (s) do `--full` |
| `database.url` | `jdbc:sqlite:network-monitor.db` | caminho do banco |

O subnet e o modo podem ser informados em qualquer ordem.

O banco `network-monitor.db` é criado automaticamente na raiz do projeto na primeira execução.

## Decisões e limitações iniciais

- **Descoberta via cache ARP**: os dispositivos são descobertos lendo o cache ARP do kernel (`/proc/net/arp`). Isso resolve IP → MAC (camada 2) sem depender de ICMP e elimina falsos positivos no changelog quando um IP muda via DHCP — o MAC é usado como chave do dispositivo. Como o cache depende de tráfego recente, dispositivos inativos podem demorar a aparecer.
- **MAC e fabricante**: o fabricante é resolvido pelos 3 primeiros bytes do MAC (OUI) contra a base MA-L da IEEE. MACs com o bit "localmente administrado" ligado (LAA) não aparecem na base e retornam sem fabricante.
- **Modos de varredura**: o `QuickScanStrategy` testa um conjunto pré-definido de portas comuns (80, 443, 8000, etc), priorizando velocidade. O `StableScanStrategy` e o `FullScanStrategy` cobrem faixas maiores, priorizando cobertura em detrimento do tempo de execução (podem levar dezenas de segundos por dispositivo). A escolha entre os três é feita via flag na linha de comando (`--quick`, `--stable` ou `--full`).
- **Ruído em portas efêmeras**: o sistema operacional usa uma faixa de portas dinamicamente para conexões de saída, o que pode fazer com que essas portas apareçam como "abertas" em um scan de forma transitória, sem nenhum serviço real escutando ali de propósito. No Linux, essa faixa costuma começar em torno de 32768 (configurável via `net.ipv4.ip_local_port_range`), mas o valor exato varia por sistema operacional e configuração do host. O `StableScanStrategy` limita a varredura a 1–32767 para reduzir esse ruído; o `FullScanStrategy` cobre a faixa inteira e por isso é mais suscetível a essa variabilidade. Em nenhum dos dois casos a estabilidade é garantida, é apenas uma redução de ruído.
- **Scan sequencial entre dispositivos**: atualmente o `NetworkScanner` varre as portas de cada dispositivo ativo um de cada vez, não em paralelo entre dispositivos. Em redes com muitos hosts ativos, isso multiplica o tempo total do `StableScanStrategy`/`FullScanStrategy` pelo número de dispositivos — uma paralelização entre devices é uma evolução futura possível.
- **IP histórico**: a tabela `device` guarda o IP atual de cada MAC. Um scan antigo exibido depois mostra o IP *atual* do dispositivo, não o que ele tinha na época do scan.