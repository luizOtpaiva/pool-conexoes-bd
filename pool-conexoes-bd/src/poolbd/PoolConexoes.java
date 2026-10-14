package poolbd;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;

/**
 * Pool de conexões protegido por DOIS semáforos (Cap. 6):
 *  - vagas : semáforo de CONTAGEM, iniciado em N (=3). Conta quantas conexões estão livres.
 *            obter() faz acquire() (bloqueia se vagas == 0) e devolver() faz release().
 *  - mutex : semáforo BINÁRIO (valor 1). Protege a estrutura 'livres' (uma lista compartilhada),
 *            que é, ela própria, uma seção crítica.
 */
public class PoolConexoes {
    private final Semaphore vagas;
    private final Semaphore mutex = new Semaphore(1);
    private final Deque<ConexaoBancoDados> livres = new ArrayDeque<>();

    /** @param justo true = fila FIFO de threads bloqueadas no semáforo (sem starvation entre threads) */
    public PoolConexoes(ServidorBanco servidor, int tamanho, boolean justo) {
        this.vagas = new Semaphore(tamanho, justo);
        for (int i = 1; i <= tamanho; i++) livres.addLast(new ConexaoBancoDados(i, servidor));
    }

    /** wait(vagas) -> wait(mutex) -> retira conexão -> signal(mutex). */
    public ConexaoBancoDados obter() throws InterruptedException {
        vagas.acquire();            // bloqueia enquanto as 3 conexões estiverem em uso
        mutex.acquire();
        try {
            return livres.pollFirst();
        } finally {
            mutex.release();
        }
    }

    /** Devolve a conexão e sinaliza uma vaga: acorda quem estiver esperando. */
    public void devolver(ConexaoBancoDados c) {
        mutex.acquireUninterruptibly();
        try {
            livres.addLast(c);
        } finally {
            mutex.release();
        }
        vagas.release();            // signal(vagas)
    }

    public int vagasLivres() { return vagas.availablePermits(); }
}
