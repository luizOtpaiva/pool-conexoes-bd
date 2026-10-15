# Pool de conexões de banco de dados

Trabalho de Sistemas Operacionais (Inatel) que junta dois assuntos da matéria: sincronização com semáforos (cap. 6) e scheduling de CPU (cap. 5). Está escrito em Java e usa só a biblioteca padrão.

## O que o programa simula

Imagine um servidor que recebe muitas requisições ao mesmo tempo, mas o banco de dados aceita no máximo 3 conexões abertas. O programa cria uma thread para cada requisição e mostra o que acontece em três situações:

1. **Sem controle.** 20 threads usam as 3 conexões sem checar se alguma está livre. Dá race condition: o limite estoura e os resultados de uma consulta se misturam com os de outra.
2. **Com semáforo.** Um `Semaphore(3)` segura as threads que passam do limite até alguma conexão ser devolvida. Os problemas somem.
3. **Com semáforo e scheduling.** Agora existe uma fila de espera e um escalonador que decide qual requisição pega a conexão que acabou de liberar. A mesma carga roda com quatro políticas (FCFS, prioridade, SJF e prioridade com aging) para comparar.

Para o cenário 3 usei esta analogia com a matéria: a conexão faz o papel da CPU, a requisição é o processo, o tempo da consulta é o CPU burst e o tipo da requisição (pagamento, login, consulta comum, relatório) é a prioridade.

## Como rodar

Precisa de Java 11 ou mais novo, com o `javac` (JDK). No Mac dá para instalar com `brew install openjdk`.

O zip tem uma pasta `pool-conexoes-bd` dentro de outra. Entre na de dentro, a que tem as pastas `src` e `web`.

No terminal:

```bash
chmod +x compilar_e_executar.sh     # só na primeira vez (Linux/macOS)
./compilar_e_executar.sh            # os 3 cenários em sequência
./compilar_e_executar.sh 1          # só o cenário 1
./compilar_e_executar.sh 2          # só o cenário 2
./compilar_e_executar.sh 3          # só o cenário 3
./compilar_e_executar.sh 3 --resumo # cenário 3 sem o log de cada despacho
```

No Windows é o `compilar_e_executar.bat`, com os mesmos argumentos.

### Painel no navegador

Tem também uma página que roda as simulações e mostra o resultado em tabelas e gráficos. No Windows, o `abrir_painel.bat` faz tudo. No Mac ou Linux:

```bash
mkdir -p out && javac -encoding UTF-8 -d out src/poolbd/*.java
java -Dfile.encoding=UTF-8 -cp out poolbd.ServidorWeb
```

e depois abra http://localhost:8080. Se a porta 8080 já estiver em uso (o Jenkins, por exemplo, usa essa), passe outra porta no final do comando, como `... poolbd.ServidorWeb 8081`, e abra o endereço com ela. Rode sempre de dentro da pasta do projeto, porque o servidor procura a pasta `web`. O cenário 3 leva uns 15 segundos, já que roda as quatro políticas uma depois da outra.

## Arquivos

```
src/poolbd/              código Java
web/                     painel (HTML, CSS e JS)
saidas/                  saída de uma execução de cada cenário
RELATORIO.md / .docx     relatório do trabalho
compilar_e_executar.sh   roda no terminal (Linux/macOS)
compilar_e_executar.bat  roda no terminal (Windows)
abrir_painel.bat         abre o painel (Windows)
```

Classes em `src/poolbd`:

| Classe | O que faz |
|---|---|
| `ConexaoBancoDados` | A seção crítica. Cada conexão tem um buffer e só atende uma consulta por vez. Detecta quando duas threads entram juntas e quando o dado lido não é o que a thread gravou |
| `ServidorBanco` | Representa o banco, com limite de 3. Só conta pico, estouros, colisões e dados corrompidos |
| `PoolConexoes` | Um semáforo de contagem (3 vagas) e um binário que protege a lista de conexões livres |
| `Requisicao` | O "processo": chegada, duração, prioridade e os tempos medidos. Tem um semáforo próprio para o escalonador acordá-la |
| `Prioridade` | CRÍTICA, ALTA, NORMAL e BAIXA |
| `Politica` | As quatro políticas de escalonamento |
| `EscalonadorRequisicoes` | A fila de prontos (um monitor com `synchronized`, `wait` e `notifyAll`) e a thread que despacha |
| `Cenario1SemControle`, `Cenario2ComSemaforo`, `Cenario3ComScheduling` | Os três experimentos |
| `Carga` | Gera as requisições com semente fixa, para todas as políticas pegarem a mesma carga |
| `Metricas` | Calcula espera, turnaround, throughput e utilização |
| `Log`, `Main` | Log com tempo relativo e ponto de entrada |
| `ServidorWeb` | Servidor HTTP do painel; roda os cenários e devolve o resultado em JSON |

## Como funciona

### Cenários 1 e 2

No cenário 1 cada thread pega uma conexão em rodízio e executa direto. Como ninguém verifica se a conexão está ocupada, várias threads caem na mesma. A conexão grava `RESULTADO_DA_REQ_N` no buffer, espera o tempo da consulta e lê o buffer de volta. Se o que leu for diferente do que gravou, outra thread sobrescreveu no meio.

No cenário 2 as threads passam pelo pool:

```java
public ConexaoBancoDados obter() throws InterruptedException {
    vagas.acquire();   // semáforo de contagem, começa em 3
    mutex.acquire();   // semáforo binário, protege a lista de livres
    try { return livres.pollFirst(); } finally { mutex.release(); }
}
```

O semáforo é criado como justo (`fair`), então quem espera é atendido na ordem em que chegou.

### Cenário 3

Uma thread despachante fica num laço:

1. espera uma conexão livre (`pool.obter()`);
2. pede à política qual requisição da fila deve usá-la;
3. dá `release()` no semáforo dessa requisição, que estava dormindo, para ela começar.

Ou seja, o semáforo diz quando existe conexão livre e a política diz para quem ela vai. Nenhuma política interrompe uma consulta em andamento, só decidem na hora em que uma conexão fica livre.

A carga é: 6 relatórios longos (prioridade baixa) chegam primeiro, e depois chega uma requisição a cada 40 ms, 24 no total, o suficiente para manter as 3 conexões ocupadas. Esse formato foi escolhido para dar para ver starvation.

No aging, a cada 250 ms esperando na fila a requisição sobe um nível de prioridade.

## Resultados

Espera média na fila, em milissegundos, por prioridade (uma execução real, os valores mudam um pouco a cada vez):

| Política | Crítica | Alta | Normal | Baixa | Pior espera de Baixa | Turnaround médio |
|---|---|---|---|---|---|---|
| FCFS | 751 | 788 | 749 | 182 | 365 | 837 |
| Prioridade | 89 | 78 | 677 | 754 | 1575 | 606 |
| SJF | 164 | 140 | 521 | 752 | 1563 | 585 |
| Prioridade + aging | 307 | 719 | 699 | 361 | 819 | 734 |

O que dá para tirar disso:

- Sem semáforo, das 20 requisições, 17 estouraram o limite, 17 colidiram em uma conexão e 17 voltaram com dado trocado. O pico foi de 20 consultas ao mesmo tempo. Com `Semaphore(3)` o pico foi 3 e não houve nenhum problema.
- A prioridade pura deixa pagamentos e logins esperando pouco, mas os relatórios chegam a esperar 1575 ms. Isso é starvation.
- O aging reduz a pior espera dos relatórios para 819 ms. Em troca, as requisições críticas esperam mais (307 ms contra 89 ms).
- O SJF teve o menor turnaround médio, mas não olha para a importância da requisição, só para a duração.
- Throughput (cerca de 15 requisições por segundo) e utilização (cerca de 95%) ficaram praticamente iguais nas quatro políticas. Como as conexões ficam sempre cheias, a ordem muda só quem espera, não quanto trabalho termina.

A análise completa está no `RELATORIO.md` e as saídas dessa execução estão em `saidas/`.