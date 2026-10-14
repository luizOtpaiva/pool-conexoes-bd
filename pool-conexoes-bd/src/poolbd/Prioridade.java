package poolbd;

/**
 * Classes de prioridade das requisições ao banco (menor nível = mais importante).
 * Analogia com o Cap. 5: é a "prioridade do processo" usada pelo escalonador.
 */
public enum Prioridade {
    CRITICA(0, "CRÍTICA", 'C'),   // ex.: pagamento / transação financeira
    ALTA(1, "ALTA", 'A'),         // ex.: login / autenticação
    NORMAL(2, "NORMAL", 'N'),     // ex.: consultas do dia a dia
    BAIXA(3, "BAIXA", 'B');       // ex.: relatórios / jobs em lote

    public final int nivel;
    public final String rotulo;
    public final char sigla;

    Prioridade(int nivel, String rotulo, char sigla) {
        this.nivel = nivel;
        this.rotulo = rotulo;
        this.sigla = sigla;
    }
}
