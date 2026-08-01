# Network Monitor

Monitor de rede doméstica desenvolvido em Java, focado em descoberta de dispositivos, reconhecimento de serviços e detecção de mudanças ao longo do tempo.

## O que ele faz

- Descoberta de dispositivos ativos na rede local via varredura de IP
- Reconhecimento de portas/serviços abertos em cada dispositivo encontrado, com três modos de varredura (rápido, estável ou completo)
- Persistência histórica de cada varredura em banco SQLite
- Detecção automática de mudanças entre a varredura atual e a anterior (dispositivo novo, dispositivo que sumiu, porta que abriu, porta que fechou)

## Exemplo de saída

```
14:32:07 [INFO] scanner.NetworkScanner - Etapa 1: procurando dispositivos ativos...
14:32:07 [INFO] scanner.DeviceScanner - Dispositivo ativo encontrado: 192.168.0.10
14:32:07 [INFO] scanner.DeviceScanner - Dispositivo ativo encontrado: 192.168.0.1
14:32:07 [INFO] scanner.NetworkScanner - Etapa 2: verificando portas de cada dispositivo...

=== Resultado final ===
192.168.0.10 -> portas abertas: [80]
192.168.0.1 -> nenhuma porta comum aberta

=== Mudanças detectadas desde o último scan ===
[PORTA FECHOU] 192.168.0.10 fechou a porta 3000
```

## Arquitetura

O projeto é dividido em módulos. A varredura de portas usa o padrão **Strategy**: `NetworkScanner` depende apenas da interface `PortScanStrategy`, recebida via construtor, sem saber qual implementação está por trás.

| Módulo | Responsabilidade |
|---|---|
| `scanner.DeviceScanner` | Varre a faixa de IP da rede e identifica hosts ativos via ICMP |
| `interfaces.PortScanStrategy` | Contrato comum entre os modos de varredura de portas |
| `scanner.QuickScanStrategy` | Testa conectividade TCP em um conjunto fixo de portas comuns por host |
| `scanner.StableScanStrategy` | Testa conectividade TCP nas portas 1–32767 por host, reduzindo o ruído de portas efêmeras |
| `scanner.FullScanStrategy` | Testa conectividade TCP em todas as portas (1–65535) por host |
| `scanner.NetworkScanner` | Orquestra os scanners acima em um fluxo único |
| `model.Device` | Representa um dispositivo com seu IP e portas abertas |
| `repository.ScanRepository` | Persiste e consulta os resultados no SQLite |
| `detector.ChangeDetector` | Compara dois snapshots e gera a lista de eventos de mudança |

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

O subnet da rede é passado como argumento de linha de comando. Para descobrir o prefixo da sua rede, rode `ipconfig` (Windows) ou `ip a` (Linux/Mac) e observe seu IP local — ex: se seu IP é `192.168.0.15`, o prefixo é `192.168.0`.

Um segundo argumento opcional seleciona o modo de varredura de portas:
- Ausente ou `--quick` — varredura rápida (conjunto fixo de portas comuns), modo padrão
- `--stable` — varredura das portas 1–32767, cobertura maior com menos ruído de portas efêmeras
- `--full` — varredura completa, todas as portas de 1 a 65535

**Rodando pela IDE (IntelliJ):**
Em `Run → Edit Configurations`, adicione o prefixo da sua rede (e opcionalmente `--stable` ou `--full`) no campo "Program arguments" (ex: `192.168.0` ou `192.168.0 --full`) e execute a classe `NetworkScanner`.

**Rodando via terminal:**
```bash
# modo rápido (padrão) — testa um conjunto fixo de portas comuns
java -jar target/network-monitor-1.0-SNAPSHOT.jar <prefixo-da-sua-rede>

# modo estável — testa as portas 1 a 32767
java -jar target/network-monitor-1.0-SNAPSHOT.jar <prefixo-da-sua-rede> --stable

# modo completo — testa todas as portas de 1 a 65535
java -jar target/network-monitor-1.0-SNAPSHOT.jar <prefixo-da-sua-rede> --full
```

Se nenhum argumento for passado, o programa exibe a instrução de uso e encerra.

O banco `network-monitor.db` é criado automaticamente na raiz do projeto na primeira execução.

## Decisões e limitações iniciais

- **Modos de varredura**: o `QuickScanStrategy` testa um conjunto pré-definido de portas comuns (80, 443, 8000, etc), priorizando velocidade. O `StableScanStrategy` e o `FullScanStrategy` cobrem faixas maiores, priorizando cobertura em detrimento do tempo de execução (podem levar dezenas de segundos por dispositivo). A escolha entre os três é feita via flag na linha de comando (`--quick`, `--stable` ou `--full`).
- **Ruído em portas efêmeras**: o sistema operacional usa uma faixa de portas dinamicamente para conexões de saída, o que pode fazer com que essas portas apareçam como "abertas" em um scan de forma transitória, sem nenhum serviço real escutando ali de propósito. No Linux, essa faixa costuma começar em torno de 32768 (configurável via `net.ipv4.ip_local_port_range`), mas o valor exato varia por sistema operacional e configuração do host. O `StableScanStrategy` limita a varredura a 1–32767 para reduzir esse ruído; o `FullScanStrategy` cobre a faixa inteira e por isso é mais suscetível a essa variabilidade. Em nenhum dos dois casos a estabilidade é garantida, é apenas uma redução de ruído.
- **Scan sequencial entre dispositivos**: atualmente o `NetworkScanner` varre as portas de cada dispositivo ativo um de cada vez, não em paralelo entre dispositivos. Em redes com muitos hosts ativos, isso multiplica o tempo total do `StableScanStrategy`/`FullScanStrategy` pelo número de dispositivos — uma paralelização entre devices é uma evolução futura possível.