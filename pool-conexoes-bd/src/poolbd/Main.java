package poolbd;

/**
 * Uso:  java poolbd.Main [1|2|3|todos] [--resumo]
 *   1  = sem controle            2 = Semaphore(3)            3 = Semaphore(3) + scheduling
 *   --resumo = no cenário 3, não imprime o log de cada despacho (só métricas e ordem de atendimento)
 */
public class Main {
    public static void main(String[] args) throws Exception {
        String modo = "todos";
        for (String a : args) {
            if (a.equals("--resumo")) Log.detalhe = false; else modo = a;
        }
        switch (modo) {
            case "1": Cenario1SemControle.executar(20); break;
            case "2": Cenario2ComSemaforo.executar(20); break;
            case "3": Cenario3ComScheduling.executarTodas(); break;
            default:
                Cenario1SemControle.executar(20);
                Cenario2ComSemaforo.executar(20);
                Cenario3ComScheduling.executarTodas();
        }
    }
}
