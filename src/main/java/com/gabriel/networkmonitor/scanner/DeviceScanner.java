package com.gabriel.networkmonitor.scanner;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.*;

public class DeviceScanner {

    // Timeout em milissegundos pra cada tentativa de ping
    private static final int TIMEOUT = 200;

    /**
     * Varre uma faixa de IPs (ex: 192.168.0) do host 1 até host 254
     * e retorna a lista dos que responderam.
     */
    public List<String> scanRange(String subnet) throws InterruptedException {
        List<String> ativos = new CopyOnWriteArrayList<>(); // thread-safe

        // pool de threads
        // testar 254 IPs um por um sequencialmente seria muito lento (200ms x 254 = quase 1 minuto)
        ExecutorService executor = Executors.newFixedThreadPool(50);

        for (int i = 1; i <= 254; i++) {
            String ip = subnet + "." + i;
            executor.submit(() -> {
                try {
                    InetAddress endereco = InetAddress.getByName(ip);
                    if (endereco.isReachable(TIMEOUT)) {
                        ativos.add(ip);
                        System.out.println("Ativo: " + ip);
                    }
                } catch (Exception e) {
                    // IP não respondeu ou erro de rede, ignora e segue
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        return ativos;
    }

    public static void main(String[] args) throws InterruptedException {
        DeviceScanner scanner = new DeviceScanner();

        // prefixo da rede ("ipconfig" ou "ip a" pra descobrir)
        String subnet = "192.168.1";

        System.out.println("Escaneando rede " + subnet + ".0/24 ...");
        long inicio = System.currentTimeMillis();

        List<String> dispositivosAtivos = scanner.scanRange(subnet);

        long duracao = System.currentTimeMillis() - inicio;
        System.out.println("\nEscaneamento concluído em " + duracao + "ms");
        System.out.println("Dispositivos ativos encontrados: " + dispositivosAtivos.size());
        dispositivosAtivos.forEach(System.out::println);
    }
}