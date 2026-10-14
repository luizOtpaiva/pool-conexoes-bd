package poolbd;

/** Log com relógio relativo ao início da simulação. A saída é sincronizada só para ficar legível. */
public final class Log {
    private static volatile long t0 = System.nanoTime();
    public static volatile boolean detalhe = true;

    private Log() {}

    public static void iniciarRelogio() { t0 = System.nanoTime(); }

    public static long agora() { return (System.nanoTime() - t0) / 1_000_000L; }

    public static synchronized void p(String msg) {
        System.out.printf("[%5d ms] %s%n", agora(), msg);
    }

    /** Só imprime quando o modo detalhado está ligado. */
    public static void d(String msg) { if (detalhe) p(msg); }

    public static synchronized void titulo(String msg) {
        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println(msg);
        System.out.println("=".repeat(78));
    }

    public static void dormir(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
