package poolbd;

import java.util.ArrayList;
import java.util.List;

/**
 * Escalonador de requisições. Duas peças do Cap. 6 trabalham juntas aqui:
 *  1) A FILA DE PRONTOS é um MONITOR (synchronized + wait/notifyAll): vários clientes inserem e a
 *     thread despachante retira, sem race condition.
 *  2) O PoolConexoes (semáforo de contagem) diz QUANDO há conexão livre; a Politica (Cap. 5)
 *     diz QUAL requisição da fila recebe essa conexão.
 *
 * Ciclo de vida da requisição (igual ao diagrama de estados do Cap. 5):
 *   NEW -> submeter() -> READY (fila) -> despacho -> RUNNING (usa a conexão) -> TERMINATED
 */
public class EscalonadorRequisicoes {
    private final List<Requisicao> prontas = new ArrayList<>();
    private boolean encerrado = false;

    private final Politica politica;
    private final PoolConexoes pool;
    private final List<Requisicao> ordemDeAtendimento = new ArrayList<>();

    public EscalonadorRequisicoes(Politica politica, PoolConexoes pool) {
        this.politica = politica;
        this.pool = pool;
    }

    /** Chamado pela thread da requisição ao chegar: entra na fila de prontos. */
    public synchronized void submeter(Requisicao r) {
        r.tChegada = Log.agora();
        prontas.add(r);
        Log.d("＋ " + r + " chegou à fila (tamanho da fila: " + prontas.size() + ")");
        notifyAll();
    }

    /** Avisa que não chegarão mais requisições. */
    public synchronized void encerrar() {
        encerrado = true;
        notifyAll();
    }

    /** Monitor: espera haver alguém na fila e aplica a política para escolher o próximo. */
    private synchronized Requisicao proximaRequisicao() throws InterruptedException {
        while (prontas.isEmpty() && !encerrado) wait();
        if (prontas.isEmpty()) return null;                  // encerrado e fila vazia
        long agora = Log.agora();
        Requisicao escolhida = politica.escolher(prontas, agora);
        prontas.remove(escolhida);
        escolhida.tDespacho = agora;
        ordemDeAtendimento.add(escolhida);
        if (Log.detalhe) {
            Log.p("➜ DESPACHO " + escolhida + " espera=" + escolhida.espera() + "ms  (restam "
                    + prontas.size() + " na fila)");
        }
        return escolhida;
    }

    /** Corpo da thread despachante (o "dispatcher"). Roda até encerrar() e a fila esvaziar. */
    public void despachar() {
        try {
            while (true) {
                ConexaoBancoDados c = pool.obter();           // espera uma conexão livre (semáforo)
                Requisicao r = proximaRequisicao();           // política escolhe QUEM usa a conexão
                if (r == null) {                              // acabou o trabalho
                    pool.devolver(c);
                    return;
                }
                r.conexao = c;
                r.autorizacao.release();                      // acorda a thread dessa requisição
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized List<Requisicao> getOrdemDeAtendimento() {
        return new ArrayList<>(ordemDeAtendimento);
    }

    public Politica getPolitica() { return politica; }
}
