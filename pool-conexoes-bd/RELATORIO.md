---
title: "Pool de Conexões de Banco de Dados: Sincronização com Semáforos e Scheduling de Requisições"
subtitle: "Projeto de Sistemas Operacionais — Cap. 5 (Scheduling de CPU) e Cap. 6 (Sincronização de Processos)"
author: "[Nomes e matrículas dos integrantes do grupo]"
date: "Inatel — [mês/ano]"
---

# 1. Introdução

Um servidor de aplicação (*backend*) recebe muitas requisições ao mesmo tempo, mas o banco de dados só aceita um número limitado de conexões abertas simultaneamente. Este projeto simula esse cenário em Java e usa dois conteúdos da disciplina:

- **Sincronização (Cap. 6):** o acesso às conexões é uma **seção crítica**. Sem controle ocorrem *race conditions*; com **semáforos** o problema é resolvido.
- **Scheduling de CPU (Cap. 5):** quando há mais requisições esperando do que conexões livres, é preciso decidir **qual requisição é atendida primeiro**. Essa decisão é um problema de escalonamento: a conexão faz o papel da CPU e cada requisição faz o papel de um processo.

**Objetivos.** (i) mostrar o que acontece sem controle; (ii) resolver com um semáforo de contagem `Semaphore(3)`; (iii) acrescentar um escalonador que define a prioridade entre requisições e comparar políticas segundo os critérios do Cap. 5.

# 2. Fundamentação teórica

## 2.1 Seção crítica e *race condition* (Cap. 6)

Processos (ou threads) cooperantes que acessam um recurso compartilhado de forma descontrolada podem produzir resultados que dependem da ordem de execução. Essa situação é uma *race condition*. A parte do código que acessa o recurso compartilhado é a **seção crítica**, e uma solução correta precisa garantir **exclusão mútua**, **progresso** e **espera limitada**.

Neste projeto, o recurso compartilhado é `ConexaoBancoDados`. Cada conexão possui um buffer de resultado próprio, então só pode atender uma consulta por vez. Se duas threads usam a mesma conexão simultaneamente, uma sobrescreve o resultado da outra.

## 2.2 Semáforos (Cap. 6)

Um semáforo é uma variável inteira acessada apenas por duas operações atômicas: `wait()` (em Java, `acquire()`) e `signal()` (em Java, `release()`).

- **Semáforo binário / mutex** (valor 0 ou 1): garante exclusão mútua sobre um único recurso.
- **Semáforo de contagem** (valor *N*): controla um recurso com *N* instâncias. O valor indica quantas instâncias estão livres. Quando chega a 0, quem chama `acquire()` bloqueia até alguém fazer `release()`. É o caso do pool de conexões: `Semaphore(3)`.
- **Semáforo de sinalização** (valor inicial 0): uma thread dorme em `acquire()` até outra fazer `release()`. Usamos esse padrão para "acordar" uma requisição quando o escalonador lhe concede uma conexão.

## 2.3 Monitores (Cap. 6)

Um monitor encapsula dados compartilhados e garante que só uma thread execute seus métodos por vez, com variáveis de condição para esperar. Em Java isso corresponde a `synchronized` com `wait()`/`notifyAll()`. A **fila de requisições prontas** do escalonador é implementada como um monitor.

## 2.4 Scheduling de CPU (Cap. 5)

O escalonador de curto prazo escolhe, entre os processos no estado *Ready*, qual passa para *Running* (*dispatch*). Os critérios de avaliação usados no capítulo são: **utilização**, ***throughput***, **tempo de turnaround**, **tempo de espera** e **tempo de resposta**. As políticas relevantes para este trabalho:

| Política | Ideia | Risco |
|---|---|---|
| **FCFS** | Atende por ordem de chegada | Requisições curtas/importantes esperam atrás das longas |
| **Prioridade** | Atende a de maior prioridade | ***Starvation***: baixa prioridade pode nunca ser atendida |
| **SJF** | Atende a de menor *burst* | Minimiza a espera média; precisa estimar a duração; pode causar starvation dos longos |
| **Prioridade + *aging*** | A prioridade aumenta com o tempo de espera | Troca um pouco de latência dos importantes por justiça |

Também diferenciamos escalonamento **preemptivo** e **não-preemptivo**. Uma consulta SQL em andamento numa conexão não pode ser "retirada" no meio, então todas as políticas aqui são **não-preemptivas**: a decisão só ocorre quando uma conexão fica livre.

# 3. Modelagem do problema

| Conceito do banco / backend | Conceito de SO |
|---|---|
| Conexão com o banco (3 disponíveis) | CPU / recurso com 3 instâncias |
| Requisição de usuário (uma thread) | Processo |
| Duração da consulta | *CPU burst* |
| Tipo de requisição (pagamento, login, consulta, relatório) | Prioridade do processo |
| Fila de requisições aguardando conexão | Fila de prontos (*ready queue*) |
| Thread despachante | Escalonador de curto prazo (*dispatcher*) |
| Tempo na fila | Tempo de espera |

**Classes de prioridade:**

| Prioridade | Exemplo | Duração simulada |
|---|---|---|
| CRÍTICA | Pagamento / transação | 100–180 ms |
| ALTA | Login / autenticação | 90–160 ms |
| NORMAL | Consulta comum | 120–240 ms |
| BAIXA | Relatório / job em lote | 300–420 ms |

**Ciclo de vida da requisição** (análogo ao diagrama de estados do Cap. 5):
`NEW → submeter() → READY (fila) → despacho → RUNNING (usa a conexão) → TERMINATED`.

**Arquitetura do cenário 3:**

```
 threads-cliente ──submeter()──► [ FILA DE PRONTOS (monitor) ] ◄── Politica.escolher()
                                              ▲                          │
                                              │                          │
                          thread despachante: │ 1) pool.obter()  → espera conexão livre (Semaphore(3))
                                              │ 2) escolhe a próxima requisição pela política
                                              │ 3) req.autorizacao.release() → acorda a thread da requisição
                                              ▼
                       requisição executa na conexão e chama pool.devolver() → release()
```

# 4. Implementação

Linguagem: **Java** (`java.util.concurrent.Semaphore`, `synchronized`, `wait/notifyAll`). Pacote `poolbd`.

## 4.1 Cenário 1 — sem controle

20 requisições disparam ao mesmo tempo e escolhem uma das 3 conexões em rodízio, **sem verificar se ela está livre**. O contador do rodízio também não é protegido (`proximaConexao++`), outra *race condition* proposital. A classe `ConexaoBancoDados` grava o resultado no buffer, "processa" a consulta e lê o buffer de volta. Se o valor lido não for o gravado, houve mistura de dados.

## 4.2 Cenário 2 — `Semaphore(3)`

```java
public ConexaoBancoDados obter() throws InterruptedException {
    vagas.acquire();          // semáforo de CONTAGEM, iniciado em 3
    mutex.acquire();          // semáforo BINÁRIO protege a lista de conexões livres
    try { return livres.pollFirst(); } finally { mutex.release(); }
}
public void devolver(ConexaoBancoDados c) {
    mutex.acquireUninterruptibly();
    try { livres.addLast(c); } finally { mutex.release(); }
    vagas.release();          // libera a vaga: acorda a próxima requisição
}
```

O semáforo é criado com `fair = true`, então as threads bloqueadas são atendidas em ordem FIFO. A quarta requisição só entra quando uma das três primeiras chama `release()`. O código usa **os dois tipos de semáforo**: o de contagem controla *quantas* conexões estão livres, e o binário protege a estrutura de dados que guarda *quais* estão livres.

## 4.3 Cenário 3 — semáforo + scheduling

A thread despachante executa:

```java
while (true) {
    ConexaoBancoDados c = pool.obter();      // espera uma conexão livre (semáforo)
    Requisicao r = proximaRequisicao();      // monitor: política escolhe quem usa a conexão
    if (r == null) { pool.devolver(c); return; }
    r.conexao = c;
    r.autorizacao.release();                 // acorda a thread dessa requisição
}
```

O ponto central: **o semáforo decide *quando*** uma conexão está livre; **a política de scheduling decide *quem*** a recebe. A escolha é feita no instante do despacho, usando o tempo de espera atual, o que permite o *aging*.

Políticas implementadas (`Politica.java`): **FCFS**, **Prioridade estática**, **SJF** e **Prioridade + envelhecimento**. Nesta última, a cada 250 ms de espera a requisição sobe um nível de prioridade:

```java
static int prioridadeEfetiva(Requisicao r, long agora) {
    int bonus = (int) ((agora - r.tChegada) / INTERVALO_AGING_MS);
    return Math.max(0, r.prioridade.nivel - bonus);
}
```

**Carga do cenário 3:** 6 relatórios (BAIXA) chegam primeiro; em seguida chega 1 requisição a cada 40 ms (24 no total, entre críticas, altas e normais), o que satura as 3 conexões. A mesma carga (mesma semente) é usada nas quatro políticas.

# 5. Resultados

Os resultados abaixo vêm de uma execução real (saídas completas na pasta `saidas/`). Como é uma simulação com threads reais, os valores variam alguns milissegundos entre execuções, mas as conclusões se mantiveram nas repetições.

## 5.1 Cenário 1 vs. Cenário 2 (20 requisições simultâneas, limite = 3)

| Métrica | Cenário 1 (sem controle) | Cenário 2 (`Semaphore(3)`) |
|---|---|---|
| Pico de consultas simultâneas no servidor | **20** | **3** |
| Estouros do limite de conexões | **17** | 0 |
| Colisões (duas threads na mesma conexão) | **17** | 0 |
| Resultados corrompidos / misturados | **17** | 0 |
| Tempo total | ≈ 400 ms (mas com dados corrompidos) | 1164 ms |
| Espera média na fila | — (ninguém espera) | 485 ms |

Trecho real do cenário 1:

```
[  132 ms] ⚠ ESTOURO DE LIMITE: Req#12 [ALTA   ] LOGIN  entrou com 14 consultas ativas (limite = 3)
[  249 ms] ⚠ DADOS MISTURADOS: Req#05 [ALTA   ] LOGIN  esperava 'RESULTADO_DA_REQ_5' mas leu 'RESULTADO_DA_REQ_9'
```

No cenário 2, o log mostra que o servidor nunca teve mais de 3 consultas ativas e que cada nova requisição só começava depois de um `terminou na conexão #N`. O cenário 1 termina mais cedo apenas porque ignora o limite do servidor e entrega resultados errados. Em um banco real, o efeito seria erros de "too many connections" ou respostas trocadas entre usuários.

## 5.2 Cenário 3 — comparação das políticas

Espera média na fila, em ms, por classe de prioridade:

| Política | CRÍTICA | ALTA | NORMAL | BAIXA | Maior espera de BAIXA | Turnaround médio | Throughput (req/s) | Utilização |
|---|---|---|---|---|---|---|---|---|
| FCFS | 751 | 788 | 749 | 182 | 365 | 837 | 14,9 | 95,1% |
| Prioridade estática | **89** | **78** | 677 | 754 | **1575** | 606 | 14,8 | 94,2% |
| SJF | 164 | 140 | **521** | 752 | 1563 | **585** | 15,0 | 94,8% |
| Prioridade + aging | 307 | 719 | 699 | 361 | **819** | 734 | 15,0 | 95,3% |

Em todas as políticas o servidor nunca passou de 3 consultas simultâneas, sem colisões nem dados corrompidos. O semáforo mantém a segurança e a política só altera *quem espera*. Ordem de atendimento (C = crítica, A = alta, N = normal, B = baixa):

- **Prioridade estática:** `#01B #02B #03B #09C #10C #13C #16C #14A #17A #19A #21A #07N … #28N #30N #04B #05B #06B`. Os três últimos relatórios foram atendidos **depois de todos os demais**.
- **Prioridade + aging:** `#01B #02B #03B #09C #10C #13C #16C #07N #08N #04B #14A #22C #05B #06B …`. Os relatórios foram intercalados com as demais requisições.

# 6. Análise e discussão

1. **A seção crítica exige controle.** Sem semáforo, a falta de exclusão mútua gerou estouro do limite, colisões e resultados trocados, a *race condition* descrita no Cap. 6. Com `Semaphore(3)` o pico ficou em 3 e as violações em 0.

2. **Semáforo de contagem vs. mutex.** Um mutex permitiria só uma conexão por vez e subutilizaria o banco. O semáforo de contagem expressa exatamente "até 3 ao mesmo tempo". O semáforo binário continua necessário para proteger a lista de conexões livres, que também é uma seção crítica.

3. **Prioridade estática favorece o que importa, mas causa *starvation*.** Pagamentos e logins esperaram ≈ 80 ms, contra ≈ 750 ms no FCFS. Em compensação, relatórios de baixa prioridade chegaram a esperar 1575 ms, e só foram atendidos quando não havia mais ninguém mais importante.

4. ***Aging* resolve a starvation, com um custo.** A maior espera de relatórios caiu de 1575 ms para 819 ms (≈ 48% menos). A espera das requisições críticas subiu de 89 ms para 307 ms, porque requisições antigas passaram a competir com elas. É o *trade-off* entre latência dos importantes e justiça.

5. **SJF** teve o menor turnaround médio (585 ms), como a teoria prevê, e boa espera para críticas e altas por serem consultas curtas. Mas prioriza duração, não importância: ignora o tipo da requisição e também deixa os relatórios longos esperando (1563 ms). Além disso, exige estimar o tempo da consulta.

6. **Throughput e utilização praticamente não mudam** (≈ 15 req/s, ≈ 95%): com o mesmo trabalho total e as 3 conexões saturadas, a ordem de atendimento não altera a quantidade de trabalho concluída por segundo. A escolha da política é, então, uma decisão sobre **quem espera**, não sobre desempenho global.

7. **Limitação (não-preempção).** As três primeiras conexões foram ocupadas por relatórios (BAIXA) que chegaram primeiro, e requisições críticas posteriores tiveram de esperar o fim delas. Isso é o efeito de uma política não-preemptiva. Um sistema real poderia reservar conexões para a classe crítica ou cancelar consultas longas.

# 7. Conclusão

O projeto mostrou, de forma executável, que (i) acesso concorrente a um recurso limitado sem sincronização gera resultados incorretos; (ii) um **semáforo de contagem** garante o limite de conexões simultâneas e, junto com um semáforo binário e um monitor, mantém as estruturas consistentes; e (iii) o **scheduling de CPU** pode ser aplicado diretamente ao pool de conexões para definir a prioridade entre requisições. A política **Prioridade com envelhecimento** foi a que melhor equilibrou atendimento rápido para o que é crítico e ausência de *starvation*. Esse é o tipo de equilíbrio buscado em sistemas reais, como filas de prioridade em servidores de aplicação e em gerenciadores de pool de conexões.

# 8. Como executar

Requer Java 11+. Na pasta do projeto: `./compilar_e_executar.sh` (Linux/macOS) ou `compilar_e_executar.bat` (Windows). Argumentos: `1`, `2`, `3` ou `todos`; `--resumo` oculta o log detalhado do cenário 3.

# Referências

- Material da disciplina C012: *Capítulo 5 — Scheduling de CPU* e *Capítulo 6 — Sincronização de Processos* (Inatel).
- SILBERSCHATZ, A.; GALVIN, P. B.; GAGNE, G. *Fundamentos de Sistemas Operacionais*. LTC.
- Documentação Java: `java.util.concurrent.Semaphore`.
