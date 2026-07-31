# Network Monitor

Monitor de rede doméstica desenvolvido em Java, focado em descoberta de dispositivos, reconhecimento de serviços e detecção de mudanças ao longo do tempo.

## O que ele faz

- Descoberta de dispositivos ativos na rede local via varredura de IP
- Reconhecimento de portas/serviços abertos em cada dispositivo encontrado
- Persistência histórica de cada varredura em banco SQLite
- Detecção automática de mudanças entre a varredura atual e a anterior (dispositivo novo, dispositivo que sumiu, porta que abriu, porta que fechou)

## Exemplo de saída

```
Etapa 1: procurando dispositivos ativos...
Ativo: 192.168.0.10
Ativo: 192.168.0.1
 
Etapa 2: verificando portas de cada dispositivo...
 
=== Resultado final ===
192.168.0.10 -> portas abertas: [80]
192.168.0.1 -> nenhuma porta comum aberta
 
=== Mudanças detectadas desde o último scan ===
[PORTA FECHOU] 192.168.0.10 fechou a porta 3000
```

## Arquitetura

O projeto é dividido em módulos

| Módulo | Responsabilidade                                                           |
|---|----------------------------------------------------------------------------|
| `scanner.DeviceScanner` | Varre a faixa de IP da rede e identifica hosts ativos via ICMP             |
| `scanner.PortScanner` | Testa conectividade TCP em um conjunto (atualmente fixo) de portas por host |
| `scanner.NetworkScanner` | Orquestra os scanners acima em um fluxo único                    |
| `model.Device` | Representa um dispositivo com seu IP e portas abertas                      |
| `repository.ScanRepository` | Persiste e consulta os resultados no SQLite                                |
| `detector.ChangeDetector` | Compara dois snapshots e gera a lista de eventos de mudança                |

## Stack técnica

- **Java 17+** — linguagem principal
- **JDBC + SQLite** (`sqlite-jdbc`) — persistência local, sem necessidade de servidor de banco externo
- **Maven** — build e gerenciamento de dependências

## Como rodar

Pré-requisitos: JDK 17+ e Maven instalados.

```bash
git clone <url-do-repositorio>
cd network-monitor
mvn clean package
```

O subnet da rede é passado como argumento de linha de comando. Para descobrir o prefixo da sua rede, rode `ipconfig` (Windows) ou `ip a` (Linux/Mac) e observe seu IP local — ex: se seu IP é `192.168.0.15`, o prefixo é `192.168.0`.

**Rodando pela IDE (IntelliJ):**
Em `Run → Edit Configurations`, adicione o prefixo da sua rede no campo "Program arguments" (ex: `192.168.0`) e execute a classe `NetworkScanner`.

**Rodando via terminal:**
```bash
java -jar target/network-monitor-1.0-SNAPSHOT.jar <prefixo-da-sua-rede>
```

Se nenhum argumento for passado, o programa exibe a instrução de uso e encerra.

O banco `network-monitor.db` é criado automaticamente na raiz do projeto na primeira execução.

## Decisões e limitações iniciais

- Portas fixas: Hoje o `QuickScanStrategy` testa um conjunto pré-definido de portas (como a 80, 8000, 443, etc), foi como delimitação inicial do escopo, visto que o scan da porta 1 a 65535 por host seria algo que demandaria mais tempo do processo de scaneamento.
  - Uma evolução para isso será a escolha de um scan rápido, com portas selecionadas como está, ou o scan completo, que se comprometia pela cobertura total, em detrimento do tempo de scan