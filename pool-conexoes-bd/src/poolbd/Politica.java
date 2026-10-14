package poolbd;

import java.util.Comparator;
import java.util.List;

/**
 * Políticas de escalonamento (Cap. 5.3) aplicadas à FILA DE PRONTOS de requisições.
 * Todas são NÃO-PREEMPTIVAS: uma consulta já em execução numa conexão não pode ser retirada
 * no meio (como um processo que não aceita preempção). A escolha só acontece quando uma
 * conexão fica livre — é o "dispatch" do escalonador de curto prazo.
 */
public enum Politica {

    FCFS("FCFS (ordem de chegada)") {
        @Override public Requisicao escolher(List<Requisicao> prontas, long agora) {
            return menor(prontas, Comparator.comparingLong((Requisicao r) -> r.tChegada));
        }
    },

    PRIORIDADE("Prioridade estática") {
        @Override public Requisicao escolher(List<Requisicao> prontas, long agora) {
            return menor(prontas, Comparator
                    .comparingInt((Requisicao r) -> r.prioridade.nivel)
                    .thenComparingLong(r -> r.tChegada));
        }
    },

    SJF("SJF (consulta mais curta primeiro)") {
        @Override public Requisicao escolher(List<Requisicao> prontas, long agora) {
            // Na prática o "burst" seria estimado (plano de execução / média histórica).
            return menor(prontas, Comparator
                    .comparingLong((Requisicao r) -> r.duracaoMs)
                    .thenComparingLong(r -> r.tChegada));
        }
    },

    PRIORIDADE_AGING("Prioridade + envelhecimento (aging)") {
        @Override public Requisicao escolher(List<Requisicao> prontas, long agora) {
            return menor(prontas, Comparator
                    .comparingInt((Requisicao r) -> prioridadeEfetiva(r, agora))
                    .thenComparingLong(r -> r.tChegada));
        }
    };

    /** A cada INTERVALO_AGING_MS esperando na fila, a requisição "sobe" um nível de prioridade. */
    public static final long INTERVALO_AGING_MS = 250;

    public final String descricao;

    Politica(String descricao) { this.descricao = descricao; }

    public abstract Requisicao escolher(List<Requisicao> prontas, long agora);

    static int prioridadeEfetiva(Requisicao r, long agora) {
        long espera = agora - r.tChegada;
        int bonus = (int) (espera / INTERVALO_AGING_MS);
        return Math.max(0, r.prioridade.nivel - bonus);
    }

    private static Requisicao menor(List<Requisicao> lista, Comparator<Requisicao> cmp) {
        Requisicao melhor = lista.get(0);
        for (Requisicao r : lista) if (cmp.compare(r, melhor) < 0) melhor = r;
        return melhor;
    }
}
