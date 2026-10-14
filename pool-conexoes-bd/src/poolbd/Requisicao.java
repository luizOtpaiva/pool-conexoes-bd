package poolbd;

import java.util.concurrent.Semaphore;

/**
 * Uma requisição de usuário que precisa de UMA conexão com o banco.
 * Equivale a um "processo" do Cap. 5: tem tempo de chegada, burst (duração da consulta) e prioridade.
 */
public class Requisicao {
    public final int id;
    public final Prioridade prioridade;
    public final String tipo;        // descrição (PAGAMENTO, LOGIN, CONSULTA, RELATÓRIO)
    public final long duracaoMs;     // "CPU burst" = tempo que a consulta usa a conexão
    public final long chegadaMs;     // instante programado de chegada

    // Marcas de tempo reais (ms desde o início da simulação)
    public volatile long tChegada = -1;
    public volatile long tDespacho = -1;   // quando recebeu a conexão (saiu da fila de prontos)
    public volatile long tFim = -1;
    public volatile ConexaoBancoDados conexao;

    /** Semáforo de sinalização (valor inicial 0): o escalonador dá release() para "acordar" a requisição. */
    public final Semaphore autorizacao = new Semaphore(0);

    public Requisicao(int id, Prioridade prioridade, String tipo, long duracaoMs, long chegadaMs) {
        this.id = id;
        this.prioridade = prioridade;
        this.tipo = tipo;
        this.duracaoMs = duracaoMs;
        this.chegadaMs = chegadaMs;
    }

    public long espera()     { return tDespacho - tChegada; }  // tempo de espera na fila de prontos
    public long servico()    { return tFim - tDespacho; }      // tempo usando a conexão
    public long turnaround() { return tFim - tChegada; }       // chegada -> término

    @Override
    public String toString() {
        return String.format("Req#%02d [%-7s] %-10s", id, prioridade.rotulo, tipo);
    }
}
