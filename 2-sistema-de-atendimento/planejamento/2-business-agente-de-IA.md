# Plano de Implementação v3 — Suíte de Agentes de Atendimento \+ Wiki

### Millenium Tecnologia · Contato Preventivo (inatividade \+ manutenção) e Pós-NPS

---

## 1\. O problema que o projeto resolve

A Millenium tem dificuldade de manter contato próximo com os clientes. Eles podem estar com problemas — em produtos, serviços ou entregas — e **nem entrar em contato**. Sem contato, não há oportunidade de corrigir, melhorar ou evoluir a solução. Hoje esse atendimento de aproximação **não tem guia e não é estruturado**.

O agente \+ MCP \+ wiki entram para estruturar esse atendimento em **dois agentes**, cada um resolvendo um problema de relacionamento:

1. Agente **Preventivo** — o contato proativo. Encontra clientes para contatar por **dois gatilhos**: **(A)** cliente **antigo sem contato recente** e **(B)** cliente com **manutenção vencida** (independentemente de ter contato recente). Entrega um documento priorizado (com o porquê de cada um) e apoia a conversa com perguntas de descoberta para revelar problemas não reportados — checando **auxílio, manutenção e melhoria** dos produtos/serviços contratados.
2. Agente **Pós-NPS** — o retorno às avaliações. Responde cada NPS no tom da faixa (promotor/passivo/detrator), reconhecendo a experiência e encaminhando a tratativa. Gatilho **reativo** (o cliente respondeu), diferente do Preventivo.

Os dois compartilham a mesma wiki (tom, empatia, CNV, escuta ativa) e a mesma camada de dados; o detalhe está na §4 (suíte de agentes) e na §5 (Preventivo).

O NPS tem **papel duplo**: alimenta o Agente Pós-NPS e conta como **sinal de recência** no gatilho de inatividade (o cliente respondeu \= houve contato dele). Mas é o **N1 que dá cobertura** à maior parte dos clientes; o NPS soma quando existe.

---

## 2\. Quem o Preventivo encontra — dois gatilhos

O Preventivo coloca um cliente na fila por **dois gatilhos independentes**: entra se disparar **A ou B** (ou os dois). O **canal principal é o N1** (visitas registradas, atualizações cadastrais e de contrato) **somado às respostas do NPS**; **People CRM e Megazap são auxiliares**.

### Gatilho A — Inatividade (cliente antigo sem contato recente)

Duas dimensões, **ambas** necessárias:

**Antiguidade (é "cliente antigo"):**

- Tempo de relacionamento / data do primeiro contrato ou primeira compra ≥ *limiar* (ex.: **≥ 2 anos** de casa).

**Recência baixa (é "sem contato recente"):**

- Tempo desde o **último sinal de engajamento / conversa** ≥ *limiar* (ex.: **≥ 8–12 meses**). Contam sinais que partem do cliente **ou que registram uma interação real**:  
  - **resposta ao NPS** (o cliente respondeu — não que enviamos a pesquisa);  
  - **visita à empresa registrada no N1**;  
  - **atualização cadastral no N1**;  
  - **atualização/renovação de contrato no N1**;  
  - **atendimento registrado no Megazap** — o registro do WhatsApp **é uma conversa**, mesmo sem a resposta do cliente linha a linha.

>   
> **Enviar ≠ conversa.** Um **disparo** unilateral (marketing ou mensagem automática que a Millenium envia sem interação) **não** conta como conversa. Mas o **registro de atendimento no Megazap é uma conversa** e **conta** — não distinguimos “recebida × enviada” dentro do chamado; o próprio registro do atendimento já comprova a interação. Um cliente que só recebeu disparos de marketing — sem atendimento no Megazap, sem resposta de NPS e sem movimento no N1 — **continua inativo**.  
>   
> **Inativo \= Antiguidade alta \+ Recência baixa.** Um cliente novo que ficou quieto ainda não é "antigo sem contato"; um cliente antigo que respondeu ao NPS, teve visita/atualização no N1 ou **um atendimento no Megazap** semana passada não entra por este gatilho.

### Gatilho B — Manutenção vencida (independe de recência)

Entra na fila quem tem **preventiva vencida ou nunca realizada** — **mesmo estando em contato recente**. Aqui a recência **não exclui**: um cliente ativo com manutenção atrasada precisa ser acionado para não chegar à falha. O sinal vem do **N1**. *Na Fase 1 não há coluna de preventiva no export — usa-se um **proxy**: sem visita/movimento há ≥ **12 meses** (janela única hoje; por produto quando calibrada em `parametros/limiares.md`).*

>   
> Um cliente pode disparar **A, B ou os dois**. A fila **deduplica**: um único contato leva os dois assuntos, respeitando o limite de frequência — nunca dois toques para a mesma pessoa.

**Modelo por trás (RFM - Recência, Frequência, Monetização), para priorizar quem contatar primeiro:**

| Eixo                | O que mede                                                                         | Uso na priorização                    |
|:--------------------|:-----------------------------------------------------------------------------------|:--------------------------------------|
| **R — Recência**    | Meses desde o último **engajamento do cliente** (resposta NPS ou movimento no N1)  | **Principal.** Define a inatividade   |
| **A — Antiguidade** | Tempo de casa                                                                      | Recorta "cliente antigo"              |
| **F — Frequência**  | Nº de compras/chamados/renovações no período                                       | Distingue quem era engajado e esfriou |
| **V — Valor**       | **Valor da nota fiscal**: soma de todas as NF do cliente (= valor do contrato)     | Prioriza quem tem maior impacto (LTV) |

Assim, a fila prioriza **cliente antigo, valioso, que era ativo e ficou muito tempo sem contato** — o de maior retorno. O **valor** vem da **soma de todas as notas fiscais do cliente** (campo `ValorTotalNF`, calculado no backend = valor do contrato). 
Os **pesos atuais da PoC** são **recência 0,4 · antiguidade 0,3 · valor 0,3**, com antiguidade ≥ **24 meses** e inatividade ≥ **8 meses**. Todos os **limiares e pesos são decisão do comercial** (André/Sandro/Calebe) e ficam em `wiki/parametros.md`.

**Segmentação** (define tom e abordagem — não usa NPS como pré-requisito):

| Segmento                          | Sinais (dados de contrato)                                        | Abordagem                                                                                                       |
|:----------------------------------|:------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------|
| **Ativo silencioso**              | Contrato vigente, equipamento em uso, mas sem contato há meses    | Checkup: "como está funcionando? algo travando que não nos contaram?"                                           |
| **Ativo com manutenção vencida**  | Contato recente, mas preventiva atrasada (entra por B, não por A) | **Cuidado direto**, sem reaproximação: "notei que a preventiva do [produto] está vencida — quer que eu agende?" |
| **Contrato encerrado**            | Contrato findou e não renovou                                     | Entender por que não renovou; novas necessidades                                                                |
| **Antigo de alto valor esfriado** | Antigo, alto valor, era frequente, recência baixa                 | Prioridade máxima; reaproximação cuidadosa                                                                      |
| **Esporádico antigo**             | Compras pontuais, sem recorrência                                 | Nutrição leve, sem pressão                                                                                      |

---

## 3\. A Wiki
Conhecimento de atendimento em **Markdown**, **chamado** pelo agente via `consultar_wiki` e
**editável pelo atendente**. A wiki guarda *o que dizer* (tom, tratativas, princípios, perguntas);
o *como agir* de cada fluxo fica nas skills (ver §4).

wiki/

├── principios/         cnv.md · empatia.md · escuta-ativa.md

├── tom/

│   ├── nps-promotor.md · nps-passivo.md · nps-detrator.md

│   └── inativo.md

├── tratativas/         catraca.md · cftv.md · ponto.md · cancela.md · controle-acesso.md

├── produtos/

├── parametros/

│   ├── tempos-padrao.md

│   └── limiares.md             \# antiguidade, recência, faixas RFM (decisão do comercial)

├── perguntas-descoberta.md

├── governanca/         categorias-e-finalidades.md · protecao-identidade.md · acta-tech-formalizacao.md

└── exemplos/

**`perguntas-descoberta.md` é o coração do "descobrir problema não reportado".** É um banco de perguntas de escuta ativa que o agente adapta ao cliente. Exemplos de linha editorial:

> - "Como tem sido a operação de vocês com o \[produto\] no dia a dia?"  
> - "Teve alguma dificuldade recente que acabou não chegando até a gente?"  
> - "Se pudesse melhorar uma coisa no que entregamos, o que seria?"  
> - "Houve mudança na sua operação (mais pessoas, novos acessos, nova unidade) desde a instalação?"

Perguntas abertas, sem indução, feitas para o cliente falar. A wiki traz o "como perguntar" (CNV) e o "o que ouvir".

---

## 4\. Suíte de agentes

| Agente         | Gatilho                                                                                 | Tools (dados)                                                                             | Wiki                                                          | Saída                                                                                 |
|:---------------|:----------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------|:--------------------------------------------------------------|:--------------------------------------------------------------------------------------|
| **Preventivo** | (A) cliente antigo sem contato recente · (B) manutenção vencida (independe de recência) | `clientes_inativos`, `clientes_para_contato`, `historico_relacionamento`, `ficha_cliente` | `tom/*`, `perguntas-descoberta`, `tratativas/*`, `produtos/*` | **(A)** Documento de clientes a contatar · **(B)** Perguntas \+ respostas na conversa |
| **Pós-NPS**    | Nova resposta de NPS (quando houver)                                                    | `situacao_nps`, `ficha_cliente`                                                           | `tom/nps-*`, `tratativas/*`                                   | Rascunho no tom da faixa                                                              |

O agente central é o **Preventivo**, movido a dado de contrato (inatividade \+ manutenção).

Cada agente é uma **Skill separada** — um arquivo Markdown (`SKILL.md`) **editável pelo
atendente**, com a lógica daquele fluxo (gatilho, passos, tools, qual wiki chamar). A coluna
**Wiki** acima mostra o que cada skill **consulta** via `consultar_wiki`.

---

## 5\. Agente Preventivo

### 5.1 Saída A — o documento de clientes a contatar

O atendente pede algo como *"gere a lista de clientes para contato preventivo desta semana"*. O agente cruza os **dois gatilhos** — `clientes_inativos` (A) e `clientes_para_contato` (B, manutenção vencida) — **deduplica** e, com `historico_relacionamento`, **produz um documento priorizado** (planilha ou Markdown), por cliente:

| Coluna                                    | Origem                       | Para quê                                       |
|:------------------------------------------|:-----------------------------|:-----------------------------------------------|
| Razão social \+ contato do representante  | N1                           | Quem contatar (respeitando identidade)         |
| Produtos/contratos                        | N1                           | Contexto da conversa                           |
| **Motivo (gatilho)**                      | Regra A/B                    | Inatividade · manutenção vencida · ambos       |
| Última movimentação (meses)               | N1                           | Justifica o gatilho de inatividade             |
| Tempo de casa · valor                     | N1                           | Prioridade (Recência, Frequência, Monetização) |
| Segmento                                  | Regra de segmentação         | Define o tom da abordagem                      |
| Ângulo sugerido \+ 2–3 perguntas iniciais | Wiki                         | Ponto de partida da conversa                   |

Esse documento é o entregável que hoje não existe: transforma "temos que falar com os clientes" em uma **fila priorizada e justificada**.

### 5.2 Saída B — copiloto de conversa (perguntas e respostas)

Durante o contato, o atendente usa o agente em dois momentos:

- **Antes/início:** "me dê boas perguntas de descoberta para o \[cliente\], considerando que ele tem \[produtos\] e está há \[X meses\] sem contato" → o agente puxa `perguntas-descoberta.md` \+ a ficha e devolve perguntas sob medida, feitas para **revelar problemas não reportados**.  
- **Durante:** "o cliente disse que a catraca trava no horário de pico, como respondo?" → o agente consulta `tratativas/catraca.md`, produtos e, se for o caso, `estimar_servico`, e sugere uma resposta com CNV (acolher → esclarecer → propor próximo passo).

Isso estrutura o atendimento em tempo real, mantendo o tom e evitando que cada atendente improvise sozinho.

### 5.3 Novas tools (camada MCP)

- `clientes_inativos(limite)` → **gatilho A**: clientes antigos sem contato recente, já priorizados por RFM (recência, antiguidade, monetização) e com o segmento e a marca de confiança do cruzamento. "Engajamento" = resposta NPS ou movimento no N1; **disparo enviado não conta**. Os limiares (antiguidade, meses sem engajamento) **não são argumentos da tool** — ficam no servidor (`application.properties` / `wiki/parametros.md`), hoje **24 e 8 meses**.  
- `clientes_para_contato(preventiva_vencida, limite)` → com `preventiva_vencida=true`, só o **gatilho B** (preventiva vencida — **proxy pela última visita**, janela de **12 meses**); sem o parâmetro, a **união de A e B deduplicada**, priorizada por RFM.  
- `historico_relacionamento(termo)` → linha do tempo de **engajamento/conversas do cliente** (CNPJ ou razão social como termo): última resposta de NPS, últimas vendas/atualizações de contrato no N1, **atendimentos registrados no Megazap**, contratos ativos/encerrados. **Ignora disparos de marketing** enviados pela Millenium (o atendimento do Megazap, por ser conversa, entra).
- `ficha_cliente(termo, revelarCnpj)` e `situacao_nps(termo)` → ficha consolidada (Cliente 360) e situação de NPS. CNPJ **mascarado por padrão**.

A fila final é a **união de A e B, deduplicada** (um cliente que dispara os dois aparece uma vez, com os dois assuntos). Reaproveita `ficha_cliente`. O documento é gravado em `.docx` pela tool `salvar_documento_docx` (Apache POI). O detalhe do cruzamento que sustenta essas tools está no **3‑cruzamento‑de‑dados**.

### 5.4 Fluxo

clientes\_inativos (A) \+ clientes\_para\_contato (B) → deduplicar → priorizar (RFM) → GERAR DOCUMENTO

   → (atendente escolhe cliente) → historico\_relacionamento \+ ficha\_cliente

   → consultar\_wiki(tom do segmento \+ perguntas-descoberta) → PERGUNTAS de descoberta

   → durante a conversa: consultar\_wiki(tratativas) → RESPOSTAS sugeridas

   → registro do que foi descoberto (alimenta melhoria de produto/serviço)

O último passo fecha o ciclo que motiva o projeto: o que o cliente revela vira insumo para **melhorar produto/serviço** — não só resolver o caso pontual.

### 5.5 Regras de segurança (Guardrails)

- **LGPD / não perturbar:** a fila exclui quem pediu para não ser contatado; base legal a confirmar com a Acta (relacionamento B2B tende a legítimo interesse).  
- **Identidade:** contatar o representante correto; não expor dados entre representantes.  
- **Tom por gatilho:** para inatividade (A), sem cobrança — proibido "você sumiu"; a primeira conversa é para ouvir. Para manutenção em cliente ativo (B), tom direto de cuidado, **sem** reaproximação forçada (regra na wiki).  
- **Frequência / dedup:** um cliente que dispara A e B é contatado **uma vez**, com os dois assuntos; limite de tentativas por cliente, com registro.  
- **Humano na revisão:** o agente sugere; quem fala com o cliente é a pessoa.

---

## 6\. Governança de dados e LGPD (Acta Tech) — portão

Inalterado da v2 quanto ao processo (esqueleto → validação com André/Diego/Sandro/Calebe/ Bruna/Igor → apresentação à Acta com a matriz → formalização anexada). Ajuste na matriz por causa da mudança de eixo:

| Categoria de dado                                     | Finalidade                                   | Base legal (confirmar c/ Acta)                   | Onde é usado        |
|:------------------------------------------------------|:---------------------------------------------|:-------------------------------------------------|:--------------------|
| Cadastro PJ (razão social, CNPJ)                      | Identificar cliente                          | Execução de contrato / legítimo interesse        | Todos               |
| Contato do representante                              | Contato proativo (preventivo)                | **Legítimo interesse (relacionamento/retenção)** | Preventivo          |
| Histórico contratual (recência, valor, tempo de casa) | Identificar inatividade e manutenção vencida | Legítimo interesse                               | Preventivo          |
| Resposta NPS (quando houver)                          | Sinal de recência \+ enriquecer abordagem    | Consentimento no formulário                      | Pós-NPS, Preventivo |

Como o Preventivo se apoia em **dado de contrato** (não em NPS), o legítimo interesse tende a ser a base central — mas é exatamente o ponto a formalizar com a Acta. 
Mantêm-se: proteção de identidade, retenção definida, auditoria (quem consultou/gerou/enviou) e termos comerciais/DPA com a Anthropic. 
Mesmo em MCP local, o resultado das tools vai ao modelo — minimização é obrigatória (CNPJ mascarado por padrão, etc.).

---

## 7\. Pipeline de dados e cruzamento

> O detalhamento completo (dicionário canônico, cascata, resolução por prioridade e evidência dos
> dados reais) está no documento-irmão **3‑cruzamento‑de‑dados**. Abaixo, o essencial de negócio.

**Hierarquia de fontes (categoria · prioridade do field mapping):**

- **Principal — N1 (três exports):** Contatos/cadastro (`CADASTRO_ORGANIZACAO` · **1** — identidade-mestre), Saídas por NF (`NOTA_FISCAL` · **2** — venda e valor), Sistema contratado (`PRODUTO` · **3** — contrato e vigência).  
- **Principal — respostas do NPS** (`NPS` · **5**): o cliente respondeu (não que enviamos).  
- **Auxiliar — Megazap** (`WHATSAPP` · **4**): cada **registro de atendimento é uma conversa** e conta como engajamento/recência (não separamos “recebida × enviada”). **People CRM** (futura, fora do field mapping atual). Um **disparo de marketing** enviado, esse não conta.

Quando o mesmo campo vem de mais de uma fonte, vence a de **menor prioridade** — a identidade é sempre a do N1‑Contatos.

**Campos por cliente a garantir (export e, depois, API):**

- Data do primeiro contrato/compra (**antiguidade**).  
- Data do **último sinal de engajamento / conversa** (**recência**): última resposta de NPS, última venda no N1, última atualização/renovação de contrato, **último atendimento registrado no Megazap**. **Nunca** usar data de disparo de marketing. *Obs.: no export atual não há coluna de “visita” — a última visita é um **proxy** pelas datas disponíveis.*  
- Nº desses sinais no período (**frequência**).  
- Valor (**soma das NF**): o **valor do contrato** é a **soma de todas as notas fiscais** do cliente, acumulada no campo `ValorTotalNF` pelo backend.  
- Status do contrato (ativo/encerrado) e representante/contato.

**Cruzamento (o "join" entre as bases):** construir uma visão única por cliente cruzando **N1 (×3) \+ respondentes do NPS \+ (auxiliar) Megazap/People**, numa **cascata de confiança**: CNPJ (exato) → telefone (provável) → **nome fantasia / razão social normalizados** (incerto); dentro do N1, o **código do cliente** (ex.: `11111- …`) amarra as três planilhas. O NPS não traz CNPJ e casa melhor por **nome fantasia** — por isso a **padronização** de razão social/fantasia é o passo decisivo.

- **Fase 1 (Excel):** juntar as planilhas com normalização determinística (acentos, "Ltda/S.A.", espaços, maiúsculas, ordem de palavras) **antes** do match; N1‑Contatos é a base-mestre, NPS e auxiliares enriquecem. Match **incerto → conferência humana**.  
- **Fase 2 (integração):** o mesmo cruzamento via **API do N1** (mestre) \+ respostas de NPS, com enriquecimento opcional de People/Megazap, consolidado no backend.

Fase 1 assume a limitação de campos que faltarem no export (nos dados reais o CNPJ quase não casa entre fontes, então o casamento recai sobre nome normalizado); a lista de inativos sai do que houver de sinais de engajamento e melhora conforme a integração amadurece.

---

## 8\. Roadmap

| Frente                   | Entregas                                                                                                            | Depende de                                       |
|:-------------------------|:--------------------------------------------------------------------------------------------------------------------|:-------------------------------------------------|
| **Governança (Inicial)** | Esqueleto → validação → Acta (matriz) → formalização                                                                | Plano aprovado pela Acta para LGPD               |
| **Wiki**                 | `principios/`, `tom/`, `perguntas-descoberta`, `tratativas/`, `parametros/limiares`                                 | Conteúdo do treinamento \+ limiares do comercial |
| **Dados**                | Campos de recência/antiguidade/valor no export/API do N1                                                            | Descoberta de API                                |
| **Skills (agentes)**     | Prova de conceito (PoC) → uma `SKILL.md` por agente: **Preventivo** (documento \+ copiloto, gatilhos A+B) → Pós-NPS | Wiki \+ dados                                    |
| **Treinamento humano**   | Atendimento com comunicação não violenta/empatia/escuta ativa                                                       | Paralelo; alimenta a wiki                        |

Sequência: **Governança \+ Wiki \+ campos de dados primeiro**; depois o **Agente Preventivo** como primeiro em produção (é o de maior valor), com as duas saídas e os dois gatilhos; Pós-NPS na sequência. Acompanhamento longitudinal/dashboards fica para a fase de BI.

---

## 9\. Riscos e decisões

| Risco / decisão                                                         | Encaminhamento                                                                                                                      |
|:------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------|
| O N1 registra e datazona visitas e atualizações cadastrais/contratuais? | **Confirmar na descoberta**; é o que sustenta a recência. Se faltar, aproximar com as datas que houver                              |
| Casar bases sem CNPJ (NPS com CNPJ opcional)                            | Padronizar razão social antes do match; priorizar CNPJ quando existir                                                               |
| Limiares de antiguidade/recência/valor                                  | Decisão do comercial em `parametros/limiares.md`                                                                                    |
| Base legal do contato proativo                                          | **Confirmar com a Acta** antes de rodar com dado real (portão)                                                                      |
| Baixa resposta de NPS                                                   | NPS conta como sinal de recência quando existe; o **N1 dá a cobertura** do resto                                                    |
| Confundir disparo com conversa                                          | Regra fixa: **enviar ≠ conversa** — disparo de marketing não conta, mas o **atendimento registrado no Megazap conta como conversa** |
| Contato proativo mal calibrado incomoda                                 | Segmentação \+ CNV \+ limite de frequência \+ humano na revisão                                                                     |
| Descobertas não viram melhoria                                          | Registrar o que o cliente revela e encaminhar a produto/serviço                                                                     |

---
