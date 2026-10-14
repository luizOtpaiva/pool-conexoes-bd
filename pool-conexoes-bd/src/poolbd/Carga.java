package poolbd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Geradores de carga de trabalho (determinísticos por semente, para comparar as políticas). */
public final class Carga {
    private Carga() {}

    /** Rajada: N requisições chegam TODAS ao mesmo tempo (usada nos cenários 1 e 2). */
    public static List<Requisicao> rajada(int n, long semente) {
        Random rnd = new Random(semente);
        List<Requisicao> lista = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            int sorteio = rnd.nextInt(100);
            Prioridade p = sorteio < 15 ? Prioridade.CRITICA
                    : sorteio < 40 ? Prioridade.ALTA
                    : sorteio < 85 ? Prioridade.NORMAL : Prioridade.BAIXA;
            lista.add(criar(i, p, rnd, 0));
        }
        return lista;
    }

    /**
     * Carga mista (cenário 3): 6 relatórios (BAIXA, longos) chegam primeiro; logo depois chega um
     * fluxo contínuo de requisições mais importantes (1 a cada 40 ms), que SATURA as 3 conexões.
     * É o cenário clássico para observar starvation e o efeito do aging.
     */
    public static List<Requisicao> cargaMista(long semente) {
        Random rnd = new Random(semente);
        List<Requisicao> lista = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            lista.add(criar(0, Prioridade.BAIXA, rnd, i * 10L));
        }
        for (int i = 0; i < 24; i++) {
            int sorteio = rnd.nextInt(100);
            Prioridade p = sorteio < 20 ? Prioridade.CRITICA : sorteio < 50 ? Prioridade.ALTA : Prioridade.NORMAL;
            lista.add(criar(0, p, rnd, 60 + i * 40L));
        }
        lista.sort(Comparator.comparingLong((Requisicao r) -> r.chegadaMs));
        // renumera pelo instante de chegada
        List<Requisicao> numeradas = new ArrayList<>();
        int id = 1;
        for (Requisicao r : lista) {
            numeradas.add(new Requisicao(id++, r.prioridade, r.tipo, r.duracaoMs, r.chegadaMs));
        }
        return numeradas;
    }

    private static Requisicao criar(int id, Prioridade p, Random rnd, long chegada) {
        switch (p) {
            case CRITICA: return new Requisicao(id, p, "PAGAMENTO", 100 + rnd.nextInt(80), chegada);
            case ALTA:    return new Requisicao(id, p, "LOGIN",      90 + rnd.nextInt(70), chegada);
            case NORMAL:  return new Requisicao(id, p, "CONSULTA",  120 + rnd.nextInt(120), chegada);
            default:      return new Requisicao(id, p, "RELATÓRIO", 300 + rnd.nextInt(120), chegada);
        }
    }
}
