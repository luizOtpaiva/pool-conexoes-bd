package poolbd;

import java.util.ArrayList;
import java.util.List;

/**
 * CENÁRIO 1 — SEM CONTROLE.
 * Todas as requisições disparam ao mesmo tempo e escolhem uma das 3 conexões em rodízio, SEM verificar
 * se ela está livre. Resultado esperado: estouro do limite do servidor, colisões e dados misturados.
 */
public class Cenario1SemControle {
    private static int proximaConexao = 0;   // contador compartilhado e NÃO protegido (outra race condition)

    public static void executar(int nRequisicoes) throws InterruptedException {
        Log.titulo("CENÁRIO 1 — SEM CONTROLE (" + nRequisicoes + " requisições simultâneas, limite do servidor = "
                + ServidorBanco.LIMITE_CONEXOES + ")");
        ServidorBanco servidor = new ServidorBanco();
        ConexaoBancoDados[] conexoes = new ConexaoBancoDados[ServidorBanco.LIMITE_CONEXOES];
        for (int i = 0; i < conexoes.length; i++) conexoes[i] = new ConexaoBancoDados(i + 1, servidor);

        List<Requisicao> reqs = Carga.rajada(nRequisicoes, 42);
        List<Thread> threads = new ArrayList<>();
        Log.iniciarRelogio();
        for (Requisicao r : reqs) {
            Thread t = new Thread(() -> {
                int idx = proximaConexao++ % conexoes.length;   // lê-incrementa-grava sem proteção
                conexoes[idx].executar(r);                      // ninguém checa se a conexão está livre
            }, "req-" + r.id);
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        servidor.imprimirResumo("Cenário 1 — sem controle");
    }
}
