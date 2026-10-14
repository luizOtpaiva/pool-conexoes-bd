# Pool de Conexões de Banco de Dados — Semáforos + Scheduling de CPU

Projeto de Sistemas Operacionais (Cap. 5 — Scheduling de CPU / Cap. 6 — Sincronização de Processos).

## Como executar (Java 11 ou superior)

```bash
./compilar_e_executar.sh            # roda os 3 cenários em sequência
./compilar_e_executar.sh 1          # só o cenário 1 (sem controle)
./compilar_e_executar.sh 2          # só o cenário 2 (Semaphore(3))
./compilar_e_executar.sh 3          # cenário 3 (semáforo + scheduling), log detalhado
./compilar_e_executar.sh 3 --resumo # cenário 3 sem o log de cada despacho
```
No Windows use `compilar_e_executar.bat` (mesmos argumentos).

## Estrutura

| Arquivo | Papel |
|---|---|
| `ConexaoBancoDados` | **Seção crítica**: conexão com buffer próprio; detecta colisão e dados misturados |
| `ServidorBanco` | Servidor com limite de 3 conexões; só mede (estouros, pico, colisões) |
| `PoolConexoes` | `Semaphore(3)` de contagem (vagas) + `Semaphore(1)` binário (protege a lista de conexões livres) |
| `Requisicao`, `Prioridade` | "Processo" com chegada, duração (burst) e prioridade (CRÍTICA/ALTA/NORMAL/BAIXA) |
| `Politica` | FCFS, Prioridade, SJF e Prioridade + aging |
| `EscalonadorRequisicoes` | Fila de prontos (monitor `synchronized`/`wait`/`notifyAll`) + thread despachante |
| `Cenario1SemControle` / `Cenario2ComSemaforo` / `Cenario3ComScheduling` | Os três experimentos |
| `Metricas`, `Carga`, `Log`, `Main` | Métricas (Cap. 5.2), geradores de carga, log, ponto de entrada |

A pasta `saidas/` contém as saídas reais de uma execução de cada cenário; `RELATORIO.md`/`RELATORIO.docx` é o relatório.
