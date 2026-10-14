package poolbd;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * O "servidor de banco de dados": só suporta LIMITE_CONEXOES consultas simultâneas.
 * Esta classe apenas INSTRUMENTA (mede) a simulação; usa AtomicInteger para que as próprias
 * métricas não sofram race condition.
 */
public class ServidorBanco {
    public static final int LIMITE_CONEXOES = 3;

    private final AtomicInteger ativas = new AtomicInteger();
    private final AtomicInteger maxAtivas = new AtomicInteger();
    private final AtomicInteger estouros = new AtomicInteger();
    private final AtomicInteger colisoes = new AtomicInteger();
    private final AtomicInteger corrupcoes = new AtomicInteger();

    /** Chamado quando uma consulta começa. Retorna quantas consultas estão ativas agora. */
    public int entrar(Requisicao r) {
        int a = ativas.incrementAndGet();
        maxAtivas.accumulateAndGet(a, Math::max);
        if (a > LIMITE_CONEXOES) {
            estouros.incrementAndGet();
            Log.p("⚠ ESTOURO DE LIMITE: " + r + " entrou com " + a + " consultas ativas (limite = "
                    + LIMITE_CONEXOES + ")");
        }
        return a;
    }

    public void sair() { ativas.decrementAndGet(); }

    public void registrarColisao()  { colisoes.incrementAndGet(); }
    public void registrarCorrupcao() { corrupcoes.incrementAndGet(); }

    public int getMaxAtivas()  { return maxAtivas.get(); }
    public int getEstouros()   { return estouros.get(); }
    public int getColisoes()   { return colisoes.get(); }
    public int getCorrupcoes() { return corrupcoes.get(); }

    public void imprimirResumo(String cenario) {
        System.out.println();
        System.out.println("--- Resumo (" + cenario + ") ---");
        System.out.printf("Limite do servidor ................ %d conexões simultâneas%n", LIMITE_CONEXOES);
        System.out.printf("Pico de consultas simultâneas ..... %d%n", getMaxAtivas());
        System.out.printf("Estouros do limite ................ %d%n", getEstouros());
        System.out.printf("Colisões (2+ threads na mesma conexão) %d%n", getColisoes());
        System.out.printf("Resultados corrompidos/misturados . %d%n", getCorrupcoes());
        System.out.println("Veredito: " + (getEstouros() == 0 && getColisoes() == 0 && getCorrupcoes() == 0
                ? "✔ seção crítica protegida — nenhuma violação."
                : "✘ seção crítica DESPROTEGIDA — houve violações."));
    }
}
