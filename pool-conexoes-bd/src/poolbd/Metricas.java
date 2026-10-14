package poolbd;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Critérios de avaliação do Cap. 5.2: throughput, tempo de espera, turnaround, utilização. */
public final class Metricas {
    private Metricas() {}

    public static class Resumo {
        public double esperaMedia, esperaMax, turnaroundMedio, throughput, utilizacao, makespan;
        public final Map<Prioridade, Double> esperaPorClasse = new LinkedHashMap<>();
        public final Map<Prioridade, Double> esperaMaxPorClasse = new LinkedHashMap<>();
    }

    public static Resumo calcular(List<Requisicao> reqs, int conexoes) {
        Resumo s = new Resumo();
        long ini = Long.MAX_VALUE, fim = 0, soma = 0, somaServ = 0, somaTurn = 0, max = 0;
        for (Requisicao r : reqs) {
            ini = Math.min(ini, r.tChegada);
            fim = Math.max(fim, r.tFim);
            soma += r.espera();
            somaTurn += r.turnaround();
            somaServ += r.servico();
            max = Math.max(max, r.espera());
        }
        int n = reqs.size();
        s.makespan = fim - ini;
        s.esperaMedia = (double) soma / n;
        s.esperaMax = max;
        s.turnaroundMedio = (double) somaTurn / n;
        s.throughput = n * 1000.0 / s.makespan;
        s.utilizacao = 100.0 * somaServ / (conexoes * s.makespan);
        for (Prioridade p : Prioridade.values()) {
            long somaP = 0, maxP = 0; int cont = 0;
            for (Requisicao r : reqs) {
                if (r.prioridade == p) { somaP += r.espera(); maxP = Math.max(maxP, r.espera()); cont++; }
            }
            s.esperaPorClasse.put(p, cont == 0 ? 0.0 : (double) somaP / cont);
            s.esperaMaxPorClasse.put(p, (double) maxP);
        }
        return s;
    }

    public static void imprimir(String titulo, Resumo s) {
        System.out.println();
        System.out.println("--- Métricas (" + titulo + ") ---");
        System.out.printf("Tempo total (makespan) .......... %.0f ms%n", s.makespan);
        System.out.printf("Throughput ...................... %.1f req/s%n", s.throughput);
        System.out.printf("Utilização das conexões ......... %.1f %%%n", s.utilizacao);
        System.out.printf("Espera média / máxima ........... %.0f ms / %.0f ms%n", s.esperaMedia, s.esperaMax);
        System.out.printf("Turnaround médio ................ %.0f ms%n", s.turnaroundMedio);
    }

    public static void imprimirComparativo(Map<Politica, Resumo> resultados) {
        Log.titulo("COMPARATIVO DAS POLÍTICAS — espera média na fila (ms) por classe de prioridade");
        System.out.printf("%-38s %7s %7s %7s %7s | %8s %9s %8s %6s%n",
                "Política", "CRÍT", "ALTA", "NORMAL", "BAIXA", "BAIXAmáx", "turnarnd", "req/s", "util%");
        System.out.println("-".repeat(112));
        for (Map.Entry<Politica, Resumo> e : resultados.entrySet()) {
            Resumo s = e.getValue();
            System.out.printf("%-38s %7.0f %7.0f %7.0f %7.0f | %8.0f %9.0f %8.1f %6.1f%n",
                    e.getKey().descricao,
                    s.esperaPorClasse.get(Prioridade.CRITICA), s.esperaPorClasse.get(Prioridade.ALTA),
                    s.esperaPorClasse.get(Prioridade.NORMAL), s.esperaPorClasse.get(Prioridade.BAIXA),
                    s.esperaMaxPorClasse.get(Prioridade.BAIXA), s.turnaroundMedio, s.throughput, s.utilizacao);
        }
        System.out.println("(BAIXAmáx = maior espera de uma requisição de baixa prioridade; indica starvation)");
    }

    /** Ex.: "#03C #01B #07N ..." — mostra em que ordem as requisições receberam conexão. */
    public static String ordem(List<Requisicao> ordem) {
        StringBuilder sb = new StringBuilder();
        int col = 0;
        for (Requisicao r : ordem) {
            sb.append(String.format("#%02d%c ", r.id, r.prioridade.sigla));
            if (++col % 15 == 0) sb.append("\n    ");
        }
        return sb.toString().trim();
    }
}
