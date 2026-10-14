package poolbd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CENÁRIO 3 — SEMÁFORO + SCHEDULING DE CPU.
 * Além de limitar a 3 conexões, agora há uma fila de prontos e um escalonador que decide QUAL requisição
 * recebe a conexão que ficou livre. A mesma carga é executada com 4 políticas diferentes para comparar.
 */
public class Cenario3ComScheduling {

    public static Resumo1 executarPolitica(Politica politica, long semente) throws InterruptedException {
        Log.titulo("CENÁRIO 3 — política: " + politica.descricao);
        ServidorBanco servidor = new ServidorBanco();
        PoolConexoes pool = new PoolConexoes(servidor, ServidorBanco.LIMITE_CONEXOES, true);
        EscalonadorRequisicoes escalonador = new EscalonadorRequisicoes(politica, pool);
        List<Requisicao> reqs = Carga.cargaMista(semente);

        Thread dispatcher = new Thread(escalonador::despachar, "dispatcher");
        dispatcher.start();

        List<Thread> clientes = new ArrayList<>();
        Log.iniciarRelogio();
        for (Requisicao r : reqs) {
            Thread t = new Thread(() -> {
                try {
                    Log.dormir(r.chegadaMs);                 // simula o instante de chegada
                    escalonador.submeter(r);                 // NEW -> READY (entra na fila de prontos)
                    r.autorizacao.acquire();                 // dorme até o escalonador despachar (READY -> RUNNING)
                    r.conexao.executar(r);                   // usa a conexão
                    r.tFim = Log.agora();                    // RUNNING -> TERMINATED
                    pool.devolver(r.conexao);                // release() do semáforo de contagem
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "req-" + r.id);
            clientes.add(t);
            t.start();
        }
        for (Thread t : clientes) t.join();
        escalonador.encerrar();
        dispatcher.join();

        servidor.imprimirResumo("Cenário 3 — " + politica.descricao);
        Metricas.Resumo m = Metricas.calcular(reqs, ServidorBanco.LIMITE_CONEXOES);
        Metricas.imprimir(politica.descricao, m);
        System.out.println();
        System.out.println("Ordem em que receberam conexão (C=crítica A=alta N=normal B=baixa):");
        String ordem = Metricas.ordem(escalonador.getOrdemDeAtendimento());
        System.out.println("    " + ordem);
        return new Resumo1(m, servidor, ordem);
    }

    /** Pequeno agregador do resultado de uma execução. */
    public static class Resumo1 {
        public final Metricas.Resumo metricas;
        public final ServidorBanco servidor;
        public final String ordem;
        Resumo1(Metricas.Resumo m, ServidorBanco s, String ordem) {
            this.metricas = m;
            this.servidor = s;
            this.ordem = ordem;
        }
    }

    public static void executarTodas() throws InterruptedException {
        Map<Politica, Metricas.Resumo> resultados = new LinkedHashMap<>();
        for (Politica p : Politica.values()) {
            resultados.put(p, executarPolitica(p, 7).metricas);
        }
        Metricas.imprimirComparativo(resultados);
    }
}
