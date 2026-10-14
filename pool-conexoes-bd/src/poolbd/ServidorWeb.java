package poolbd;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Painel local. Serve a pasta web/ e executa os cenários de verdade.
 * Uso: java poolbd.ServidorWeb [porta]
 */
public class ServidorWeb {
    private static final Object EXECUCAO = new Object();

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path web = pastaWeb();
        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        servidor.createContext("/", troca -> atender(troca, web));
        servidor.setExecutor(Executors.newFixedThreadPool(4));
        servidor.start();
        System.out.println("Painel disponível em http://localhost:" + porta);
    }

    private static Path pastaWeb() {
        Path local = Paths.get("web").toAbsolutePath().normalize();
        if (Files.isDirectory(local)) return local;
        Path aninhada = Paths.get("pool-conexoes-bd", "web").toAbsolutePath().normalize();
        if (Files.isDirectory(aninhada)) return aninhada;
        throw new IllegalStateException("Pasta web não encontrada. Execute dentro de pool-conexoes-bd.");
    }

    private static void atender(HttpExchange troca, Path web) throws IOException {
        String caminho = troca.getRequestURI().getPath();
        try {
            if ("/api/executar".equals(caminho)) {
                executar(troca);
                return;
            }
            if ("/api/saude".equals(caminho)) {
                responder(troca, 200, "application/json; charset=utf-8", "{\"ok\":true}");
                return;
            }
            servirArquivo(troca, web, caminho);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            responder(troca, 500, "application/json; charset=utf-8", "{\"erro\":\"" + escapar(msg) + "\"}");
        }
    }

    private static void executar(HttpExchange troca) throws Exception {
        Map<String, String> q = consulta(troca.getRequestURI().getRawQuery());
        String cenario = q.getOrDefault("cenario", "1");
        boolean detalhe = "1".equals(q.get("detalhe")) || "true".equalsIgnoreCase(q.get("detalhe"));
        if (!cenario.equals("1") && !cenario.equals("2") && !cenario.equals("3") && !cenario.equals("todos")) {
            responder(troca, 400, "application/json; charset=utf-8", "{\"erro\":\"Cenário inválido.\"}");
            return;
        }

        synchronized (EXECUCAO) {
            boolean detalheAnterior = Log.detalhe;
            PrintStream console = System.out;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PrintStream capturado = new PrintStream(buffer, true, StandardCharsets.UTF_8);
            long inicio = System.nanoTime();
            String json;
            try {
                Log.detalhe = detalhe;
                System.setOut(capturado);
                json = rodar(cenario);
            } finally {
                System.setOut(console);
                Log.detalhe = detalheAnterior;
                capturado.flush();
            }
            long duracao = (System.nanoTime() - inicio) / 1_000_000L;
            String log = buffer.toString(StandardCharsets.UTF_8);
            String corpo = json.substring(0, json.length() - 1)
                    + ",\"duracaoMs\":" + duracao
                    + ",\"log\":\"" + escapar(log) + "\"}";
            console.println("Simulação '" + cenario + "' concluída em " + duracao + " ms");
            responder(troca, 200, "application/json; charset=utf-8", corpo);
        }
    }

    private static String rodar(String cenario) throws InterruptedException {
        List<String> blocos = new ArrayList<>();
        if (cenario.equals("1") || cenario.equals("todos")) {
            blocos.add(blocoSimples("1", "Sem controle", Cenario1SemControle.executar(20), null));
        }
        if (cenario.equals("2") || cenario.equals("todos")) {
            Cenario2ComSemaforo.Saida saida = Cenario2ComSemaforo.executar(20);
            blocos.add(blocoSimples("2", "Semáforo de contagem", saida.servidor, saida.metricas));
        }
        if (cenario.equals("3") || cenario.equals("todos")) {
            blocos.add(blocoScheduling());
        }
        return "{\"cenario\":\"" + cenario + "\",\"blocos\":[" + String.join(",", blocos) + "]}";
    }

    private static String blocoScheduling() throws InterruptedException {
        Map<Politica, Metricas.Resumo> comparativo = new LinkedHashMap<>();
        List<String> politicas = new ArrayList<>();
        int pico = 0, estouros = 0, colisoes = 0, corrupcoes = 0;
        for (Politica politica : Politica.values()) {
            Cenario3ComScheduling.Resumo1 resumo = Cenario3ComScheduling.executarPolitica(politica, 7);
            comparativo.put(politica, resumo.metricas);
            pico = Math.max(pico, resumo.servidor.getMaxAtivas());
            estouros += resumo.servidor.getEstouros();
            colisoes += resumo.servidor.getColisoes();
            corrupcoes += resumo.servidor.getCorrupcoes();
            politicas.add(jsonPolitica(politica, resumo));
        }
        Metricas.imprimirComparativo(comparativo);
        boolean protegido = estouros == 0 && colisoes == 0 && corrupcoes == 0;
        String servidor = "{\"limite\":" + ServidorBanco.LIMITE_CONEXOES
                + ",\"pico\":" + pico
                + ",\"estouros\":" + estouros
                + ",\"colisoes\":" + colisoes
                + ",\"corrupcoes\":" + corrupcoes
                + ",\"protegido\":" + protegido + "}";
        return "{\"id\":\"3\",\"titulo\":\"Scheduling\",\"servidor\":" + servidor
                + ",\"politicas\":[" + String.join(",", politicas) + "]}";
    }

    private static String blocoSimples(String id, String titulo, ServidorBanco servidor, Metricas.Resumo metricas) {
        String metrica = metricas == null ? "null" : jsonMetricas(metricas);
        return "{\"id\":\"" + id + "\",\"titulo\":\"" + escapar(titulo) + "\",\"servidor\":"
                + jsonServidor(servidor) + ",\"metricas\":" + metrica + ",\"politicas\":[]}";
    }

    private static String jsonPolitica(Politica politica, Cenario3ComScheduling.Resumo1 resumo) {
        Metricas.Resumo m = resumo.metricas;
        return "{\"id\":\"" + politica.name() + "\",\"nome\":\"" + escapar(politica.descricao) + "\","
                + "\"espera\":{"
                + "\"CRITICA\":" + num(m.esperaPorClasse.get(Prioridade.CRITICA)) + ","
                + "\"ALTA\":" + num(m.esperaPorClasse.get(Prioridade.ALTA)) + ","
                + "\"NORMAL\":" + num(m.esperaPorClasse.get(Prioridade.NORMAL)) + ","
                + "\"BAIXA\":" + num(m.esperaPorClasse.get(Prioridade.BAIXA))
                + "},\"esperaMaxBaixa\":" + num(m.esperaMaxPorClasse.get(Prioridade.BAIXA)) + ","
                + "\"metricas\":" + jsonMetricas(m) + ","
                + "\"servidor\":" + jsonServidor(resumo.servidor) + ","
                + "\"ordem\":\"" + escapar(resumo.ordem) + "\"}";
    }

    private static String jsonMetricas(Metricas.Resumo m) {
        return "{\"makespan\":" + num(m.makespan)
                + ",\"throughput\":" + num(m.throughput)
                + ",\"utilizacao\":" + num(m.utilizacao)
                + ",\"esperaMedia\":" + num(m.esperaMedia)
                + ",\"esperaMax\":" + num(m.esperaMax)
                + ",\"turnaroundMedio\":" + num(m.turnaroundMedio) + "}";
    }

    private static String jsonServidor(ServidorBanco s) {
        boolean protegido = s.getEstouros() == 0 && s.getColisoes() == 0 && s.getCorrupcoes() == 0;
        return "{\"limite\":" + ServidorBanco.LIMITE_CONEXOES
                + ",\"pico\":" + s.getMaxAtivas()
                + ",\"estouros\":" + s.getEstouros()
                + ",\"colisoes\":" + s.getColisoes()
                + ",\"corrupcoes\":" + s.getCorrupcoes()
                + ",\"protegido\":" + protegido + "}";
    }

    private static String num(double valor) {
        return String.format(Locale.US, "%.2f", valor);
    }

    private static void servirArquivo(HttpExchange troca, Path web, String caminho) throws IOException {
        String relativo = "/".equals(caminho) ? "index.html" : caminho.replaceFirst("^/", "");
        relativo = URLDecoder.decode(relativo, StandardCharsets.UTF_8);
        Path arquivo = web.resolve(relativo).normalize();
        if (!arquivo.startsWith(web) || !Files.isRegularFile(arquivo)) {
            responder(troca, 404, "text/plain; charset=utf-8", "Não encontrado");
            return;
        }
        byte[] bytes = Files.readAllBytes(arquivo);
        responder(troca, 200, tipo(arquivo.getFileName().toString()), bytes);
    }

    private static String tipo(String nome) {
        if (nome.endsWith(".html")) return "text/html; charset=utf-8";
        if (nome.endsWith(".css")) return "text/css; charset=utf-8";
        if (nome.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (nome.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static Map<String, String> consulta(String bruto) {
        Map<String, String> mapa = new LinkedHashMap<>();
        if (bruto == null || bruto.isEmpty()) return mapa;
        for (String par : bruto.split("&")) {
            int i = par.indexOf('=');
            String chave = i < 0 ? par : par.substring(0, i);
            String valor = i < 0 ? "" : par.substring(i + 1);
            mapa.put(URLDecoder.decode(chave, StandardCharsets.UTF_8), URLDecoder.decode(valor, StandardCharsets.UTF_8));
        }
        return mapa;
    }

    private static String escapar(String texto) {
        if (texto == null) return "";
        StringBuilder saida = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '\\': saida.append("\\\\"); break;
                case '"': saida.append("\\\""); break;
                case '\n': saida.append("\\n"); break;
                case '\r': saida.append("\\r"); break;
                case '\t': saida.append("\\t"); break;
                default:
                    if (c < 0x20) saida.append(String.format("\\u%04x", (int) c));
                    else saida.append(c);
            }
        }
        return saida.toString();
    }

    private static void responder(HttpExchange troca, int status, String tipo, String corpo) throws IOException {
        responder(troca, status, tipo, corpo.getBytes(StandardCharsets.UTF_8));
    }

    private static void responder(HttpExchange troca, int status, String tipo, byte[] corpo) throws IOException {
        troca.getResponseHeaders().set("Content-Type", tipo);
        troca.getResponseHeaders().set("Cache-Control", "no-store");
        troca.sendResponseHeaders(status, corpo.length);
        try (OutputStream out = troca.getResponseBody()) {
            out.write(corpo);
        }
    }
}
