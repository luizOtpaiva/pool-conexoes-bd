package poolbd;

import java.util.ArrayList;
import java.util.List;

/**
 * CENÁRIO 2 — COM SEMÁFORO DE CONTAGEM (Semaphore(3)).
 * As mesmas requisições, mas agora cada uma precisa de acquire() para obter uma conexão.
 * A 4ª só entra quando uma das 3 primeiras fizer release(). Atendimento em ordem de chegada (FIFO).
 */
public class Cenario2ComSemaforo {

    public static final class Saida {
        public final ServidorBanco servidor;
        public final Metricas.Resumo metricas;

        public Saida(ServidorBanco servidor, Metricas.Resumo metricas) {
            this.servidor = servidor;
            this.metricas = metricas;
        }
    }

    public static Saida executar(int nRequisicoes) throws InterruptedException {
        Log.titulo("CENÁRIO 2 — COM SEMÁFORO DE CONTAGEM Semaphore(" + ServidorBanco.LIMITE_CONEXOES + ") — "
                + nRequisicoes + " requisições");
        ServidorBanco servidor = new ServidorBanco();
        PoolConexoes pool = new PoolConexoes(servidor, ServidorBanco.LIMITE_CONEXOES, true);

        List<Requisicao> reqs = Carga.rajada(nRequisicoes, 42);
        List<Thread> threads = new ArrayList<>();
        Log.iniciarRelogio();
        for (Requisicao r : reqs) {
            Thread t = new Thread(() -> {
                r.tChegada = Log.agora();
                try {
                    Log.d("… " + r + " pede conexão (vagas livres agora: " + pool.vagasLivres() + ")");
                    ConexaoBancoDados c = pool.obter();      // acquire(): bloqueia se as 3 estiverem em uso
                    r.tDespacho = Log.agora();
                    try {
                        c.executar(r);                       // seção crítica: no máx. 3 threads aqui
                    } finally {
                        r.tFim = Log.agora();
                        pool.devolver(c);                    // release(): libera a vaga p/ a próxima
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "req-" + r.id);
            threads.add(t);
            t.start();
            Log.dormir(1);   // só para a ordem de chegada ser estável e o log legível
        }
        for (Thread t : threads) t.join();

        servidor.imprimirResumo("Cenário 2 — Semaphore(3)");
        Metricas.Resumo metricas = Metricas.calcular(reqs, ServidorBanco.LIMITE_CONEXOES);
        Metricas.imprimir("Cenário 2", metricas);
        return new Saida(servidor, metricas);
    }
}
