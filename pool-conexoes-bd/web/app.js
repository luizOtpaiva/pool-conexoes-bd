const resultado = document.getElementById("resultado");
const botao = document.getElementById("executar");
const hint = document.getElementById("hint");
let cenario = "1";

document.querySelectorAll(".choice").forEach((botaoCenario) => {
  botaoCenario.addEventListener("click", () => {
    cenario = botaoCenario.dataset.cenario;
    document.querySelectorAll(".choice").forEach((item) => {
      item.setAttribute("aria-pressed", item === botaoCenario ? "true" : "false");
    });
    hint.textContent = cenario === "3" || cenario === "todos"
      ? "O cenário 3 compara quatro políticas e leva cerca de quinze segundos."
      : "A rajada usa 20 requisições e o limite de 3 conexões.";
  });
});

botao.addEventListener("click", executar);

async function executar() {
  botao.disabled = true;
  botao.textContent = "Executando…";
  resultado.innerHTML = '<article class="welcome"><div><h2>Simulação em andamento</h2><p>As threads estão disputando as três conexões. O painel espera o Java terminar.</p></div></article>';
  const detalhe = document.getElementById("detalhe").checked ? "1" : "0";
  try {
    const resposta = await fetch(`/api/executar?cenario=${cenario}&detalhe=${detalhe}`, { method: "POST" });
    const dados = await resposta.json();
    if (!resposta.ok) throw new Error(dados.erro || "Falha ao executar.");
    desenhar(dados);
  } catch (erro) {
    resultado.innerHTML = "";
    const aviso = document.createElement("p");
    aviso.className = "erro";
    aviso.textContent = erro.message.includes("Failed to fetch")
      ? "O painel precisa estar aberto em http://localhost:8080, com o ServidorWeb no ar."
      : erro.message;
    resultado.appendChild(aviso);
  } finally {
    botao.disabled = false;
    botao.textContent = "Executar simulação";
  }
}

function desenhar(dados) {
  resultado.innerHTML = "";
  const pilha = document.createElement("div");
  pilha.className = "stack";
  dados.blocos.forEach((bloco) => pilha.appendChild(cartao(bloco)));
  pilha.appendChild(log(dados));
  resultado.appendChild(pilha);
}

function cartao(bloco) {
  const artigo = document.createElement("article");
  artigo.className = "block";
  const protegido = bloco.servidor && bloco.servidor.protegido && bloco.politicas.every((p) => p.servidor.protegido);
  const cabeca = document.createElement("div");
  cabeca.className = "block-head";
  const titulo = document.createElement("h2");
  titulo.textContent = rotulo(bloco);
  const selo = document.createElement("span");
  selo.className = "pill " + (protegido ? "ok" : "bad");
  selo.textContent = protegido ? "Seção crítica protegida" : "Seção crítica desprotegida";
  cabeca.append(titulo, selo);
  artigo.appendChild(cabeca);
  artigo.appendChild(pool(bloco.servidor));
  artigo.appendChild(statsServidor(bloco.servidor));
  if (bloco.metricas) artigo.appendChild(statsMetricas(bloco.metricas));
  if (bloco.politicas.length) {
    artigo.appendChild(grafico(bloco.politicas));
    artigo.appendChild(tabela(bloco.politicas));
    bloco.politicas.forEach((politica) => artigo.appendChild(ordem(politica)));
  }
  return artigo;
}

function rotulo(bloco) {
  if (bloco.id === "1") return "Cenário 1 · sem controle";
  if (bloco.id === "2") return "Cenário 2 · semáforo";
  return "Cenário 3 · scheduling";
}

function pool(servidor) {
  const caixa = document.createElement("div");
  caixa.className = "pool";
  const estourou = servidor.pico > servidor.limite;
  for (let i = 1; i <= servidor.limite; i++) {
    const slot = document.createElement("div");
    slot.className = "slot " + (estourou ? "estouro" : "cheia");
    const numero = document.createElement("b");
    numero.textContent = String(i);
    const estado = document.createElement("small");
    estado.textContent = estourou ? "invadida" : "no limite";
    slot.append(numero, estado);
    caixa.appendChild(slot);
  }
  if (estourou) {
    const extra = document.createElement("p");
    extra.className = "overflow";
    extra.textContent = "Pico de " + servidor.pico + " consultas, com limite de " + servidor.limite + ".";
    const wrap = document.createElement("div");
    wrap.append(caixa, extra);
    return wrap;
  }
  return caixa;
}

function statsServidor(servidor) {
  return grade([
    ["Pico simultâneo", servidor.pico, servidor.pico > servidor.limite],
    ["Estouros", servidor.estouros, servidor.estouros > 0],
    ["Colisões", servidor.colisoes, servidor.colisoes > 0],
    ["Dados misturados", servidor.corrupcoes, servidor.corrupcoes > 0],
  ]);
}

function statsMetricas(m) {
  return grade([
    ["Espera média", ms(m.esperaMedia), false],
    ["Turnaround", ms(m.turnaroundMedio), false],
    ["Throughput", req(m.throughput), false],
    ["Utilização", pct(m.utilizacao), false],
  ]);
}

function grade(itens) {
  const g = document.createElement("div");
  g.className = "stats";
  itens.forEach(([nome, valor, alerta]) => {
    const s = document.createElement("div");
    s.className = "stat" + (alerta ? " alerta" : "");
    const rotuloItem = document.createElement("span");
    rotuloItem.textContent = nome;
    const numero = document.createElement("b");
    numero.textContent = String(valor);
    s.append(rotuloItem, numero);
    g.appendChild(s);
  });
  return g;
}

function grafico(politicas) {
  const card = document.createElement("div");
  card.className = "chart-card";
  const legenda = document.createElement("div");
  legenda.className = "legend";
  [["i-c", "Crítica"], ["i-a", "Alta"], ["i-n", "Normal"], ["i-b", "Baixa"]].forEach(([cls, nome]) => {
    const item = document.createElement("span");
    const marca = document.createElement("i");
    marca.className = cls;
    item.append(marca, document.createTextNode(nome));
    legenda.appendChild(item);
  });
  const maximo = Math.max(...politicas.flatMap((p) => Object.values(p.espera)), 1);
  const graf = document.createElement("div");
  graf.className = "chart";
  politicas.forEach((politica) => {
    const grupo = document.createElement("div");
    grupo.className = "group";
    const barras = document.createElement("div");
    barras.className = "bars";
    ["CRITICA", "ALTA", "NORMAL", "BAIXA"].forEach((chave, indice) => {
      const barra = document.createElement("span");
      barra.className = ["i-c", "i-a", "i-n", "i-b"][indice];
      const valor = politica.espera[chave];
      barra.style.height = Math.max(4, (valor / maximo) * 140) + "px";
      barra.title = politica.nome + " · " + chave + ": " + ms(valor);
      barras.appendChild(barra);
    });
    const nome = document.createElement("small");
    nome.textContent = politica.nome.replace(" (ordem de chegada)", "").replace(" (consulta mais curta primeiro)", "").replace(" (aging)", "");
    grupo.append(barras, nome);
    graf.appendChild(grupo);
  });
  card.append(legenda, graf);
  return card;
}

function tabela(politicas) {
  const wrap = document.createElement("div");
  wrap.className = "table-wrap";
  const tabelaEl = document.createElement("table");
  const cabeca = document.createElement("tr");
  ["Política", "Crítica", "Alta", "Normal", "Baixa", "Maior baixa", "Turnaround", "req/s"].forEach((texto) => {
    const th = document.createElement("th");
    th.textContent = texto;
    cabeca.appendChild(th);
  });
  tabelaEl.appendChild(cabeca);
  politicas.forEach((politica) => {
    const linha = document.createElement("tr");
    [politica.nome, ms(politica.espera.CRITICA), ms(politica.espera.ALTA), ms(politica.espera.NORMAL),
      ms(politica.espera.BAIXA), ms(politica.esperaMaxBaixa), ms(politica.metricas.turnaroundMedio),
      req(politica.metricas.throughput)].forEach((texto) => {
      const td = document.createElement("td");
      td.textContent = texto;
      linha.appendChild(td);
    });
    tabelaEl.appendChild(linha);
  });
  wrap.appendChild(tabelaEl);
  return wrap;
}

function ordem(politica) {
  const bloco = document.createElement("div");
  const titulo = document.createElement("h3");
  titulo.textContent = politica.nome;
  titulo.style.marginTop = "16px";
  titulo.style.fontSize = "16px";
  const faixa = document.createElement("div");
  faixa.className = "ordem";
  politica.ordem.split(/\s+/).filter(Boolean).forEach((token) => {
    const tag = document.createElement("span");
    const letra = token.slice(-1).toLowerCase();
    tag.className = "tag " + letra;
    tag.textContent = token;
    faixa.appendChild(tag);
  });
  bloco.append(titulo, faixa);
  return bloco;
}

function log(dados) {
  const detalhe = document.createElement("details");
  detalhe.className = "log-card block";
  detalhe.open = dados.cenario !== "3" && dados.cenario !== "todos";
  const sumario = document.createElement("summary");
  sumario.textContent = "Log da execução · " + ms(dados.duracaoMs);
  const pre = document.createElement("pre");
  pre.className = "log";
  pre.textContent = dados.log || "(sem saída)";
  detalhe.append(sumario, pre);
  return detalhe;
}

function ms(valor) {
  return Math.round(valor).toLocaleString("pt-BR") + " ms";
}

function pct(valor) {
  return valor.toLocaleString("pt-BR", { maximumFractionDigits: 1 }) + "%";
}

function req(valor) {
  return valor.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}
