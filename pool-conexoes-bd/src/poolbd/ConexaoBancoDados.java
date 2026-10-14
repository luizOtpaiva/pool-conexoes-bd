package poolbd;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * SEÇÃO CRÍTICA: uma conexão com o banco. Ela tem um buffer de resultado próprio, então só pode
 * atender UMA consulta por vez. Se duas threads usam a mesma conexão ao mesmo tempo, uma sobrescreve
 * o resultado da outra (dados "se misturando").
 */
public class ConexaoBancoDados {
    public final int id;
    private final ServidorBanco servidor;

    private volatile String buffer = "";                         // dado compartilhado da conexão
    private final AtomicInteger usuarios = new AtomicInteger();  // só para detectar a colisão

    public ConexaoBancoDados(int id, ServidorBanco servidor) {
        this.id = id;
        this.servidor = servidor;
    }

    /** Executa a consulta ocupando a conexão por r.duracaoMs. Retorna true se o resultado veio íntegro. */
    public boolean executar(Requisicao r) {
        int ativas = servidor.entrar(r);
        if (usuarios.incrementAndGet() > 1) {
            servidor.registrarColisao();
            Log.p("⚠ COLISÃO na conexão #" + id + ": " + r + " entrou enquanto outra consulta ainda a usava");
        }
        Log.d("▶ " + r + " executando na conexão #" + id + " (consultas ativas no servidor: " + ativas + ")");

        // ---- início da seção crítica ----
        String esperado = "RESULTADO_DA_REQ_" + r.id;
        buffer = esperado;               // grava o resultado da consulta no buffer da conexão
        Log.dormir(r.duracaoMs);         // tempo de processamento da consulta
        String lido = buffer;            // lê de volta o resultado para devolver ao usuário
        // ---- fim da seção crítica ----

        boolean integro = esperado.equals(lido);
        if (!integro) {
            servidor.registrarCorrupcao();
            Log.p("⚠ DADOS MISTURADOS: " + r + " esperava '" + esperado + "' mas leu '" + lido + "'");
        }
        usuarios.decrementAndGet();
        servidor.sair();
        Log.d("■ " + r + " terminou na conexão #" + id);
        return integro;
    }
}
