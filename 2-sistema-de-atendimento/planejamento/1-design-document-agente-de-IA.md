# Design Document — Agente de Atendimento com MCP
### Millenium Tecnologia

---

## 1. Sumário

A Millenium tem dificuldade de manter contato próximo com os clientes antigos sem atendimento: 
clientes enfrentam problemas e **nem entram em contato**. 
O objetivo do sistema é estruturar um atendimento **proativo e guiado** para os atendentes 
(guia hoje inexistente) apoiado por um agente de IA (software Claude) que lê **dados dos clientes** (via MCP) e o 
**Wiki** (guia de atendimento e da estrutura da empresa), e produz **rascunhos** 
para um atendente humano revisar e enviar mensagem.

O sistema tem **uma fase** de entrega e duas fases futuras.
A primeira é a prova de conceito usando Excel com dados fictícios (dados mascarados). 
A segunda é a integração com sistemas externos para recebimento de informações.
A terceira é a integração do Megazap aos agentes de IA para não ter que exportar conversas manualmente.

- **Fase 1 — Excel:** os dados vêm de planilhas exportadas do N1, People e Megazap, e das
  respostas de NPS. O servidor MCP roda **localmente** e é acessado pelo Claude Desktop via
  **stdio**. Sem exposição de rede.
- **Fase 2 — Integração:** os dados passam a vir de **APIs** consolidadas num banco **MySQL**
  (a visão "Cliente 360"), e o servidor MCP é publicado como serviço **HTTPS** protegido por
  firewall/gateway. Todo o código da fase 1 será reaproveitado na fase 2 na integração com o Claude.
- **Fase 3 — Conversas:** integração do **Megazap** aos agentes de IA para **repassar as conversas** ao
  agente sem exportar ou copiar manualmente. Requer **contato com o Megazap** para viabilizar o
  recebimento das mensagens.

Dois princípios atravessam todo o design: **humano sempre na revisão** (o agente sugere, a pessoa
dispara) e **governança de dados como portão** (dados fictício para a prova de conceito, 
já o dado real após formalização com a Acta Tech sobre LGPD).

---

## 2. Objetivos e escopo

**Objetivos:** aumentar retenção, fidelização e LTV por meio de contato proativo; 
descobrir problemas não reportados; padronizar o tom (empatia, escuta ativa, 
comunicação não violenta) via wiki; dar ao atendente um copiloto para conversas.

**No escopo (Fase 1 — entregável):** os dois agentes (Preventivo, Pós-NPS); a wiki; o servidor MCP; 
a leitura de Excel; **dados fictícios/mascarados** para a prova de conceito.

**Fora do escopo (por enquanto):**
- **Fase 2 (futura):** integração de dados via **API** e o Cliente 360 (MySQL).
- **Fase 3 (futura):** integração do **Megazap** aos agentes para repassar as conversas sem export manual (requer contato com o Megazap).
- Além das fases: envio automático ao cliente (mantém-se rascunho + validação humana) e dashboards/relatórios.

---

## 3. Visão de negócio

- **2 AGENTES**, cada um uma **Skill separada em texto Markdown** (editável pelo atendente), sobre uma **WIKI** em texto Markdown (guia de atendimento chamada pelo agente) e uma **camada de dados** via MCP (**Excel na Fase 1, API na Fase 2**):
  - **Preventivo** — contato proativo por **dois gatilhos**: (A) **cliente antigo sem contato recente** e (B) **manutenção vencida** (independentemente de recência). Entrega o documento de quem contatar e apoia a conversa.
  - **Pós-NPS** — responde avaliações no tom da escala (promotor/passivo/detrator).
- **Cliente antigo sem contato recente = antiguidade alta + baixa recência.** A recência conta
  **só engajamento do cliente** (resposta de NPS, visita/atualização no N1, mensagem *recebida*
  no Megazap). **Enviar ≠ conversa:** disparo que a Millenium fez nunca reduz a inatividade.
- **Manutenção vencida entra independentemente da recência** — pega inclusive cliente em contato recente; a fila **deduplica** (um contato, dois assuntos).
- **Priorização por RFM** (recência + antiguidade + monetização/valor do contrato).
- **Duas saídas do agente Preventivo:** 
    (A) um **documento** priorizado de clientes a contatar; 
    (B) um **copiloto de conversa** (perguntas de descoberta + apoio a respostas).
- **Hierarquia de fontes:** N1 e respostas de NPS são **principais**; People e Megazap, **auxiliares**.

---

## 4. Arquitetura do sistema

### 4.1 Princípio: núcleo hexagonal reutilizável

O núcleo de regras (recência, RFM - Contato recente, frequência de atualização, ticket médio -, cruzamento de registros) 
é **idêntico** nas duas fases. Ele depende de **portas** (interfaces); os **adaptadores** as implementam. 
Trocar Fase 1 → Fase 2 é trocar adaptadores de saída (Excel → API/MySQL) e o transporte MCP (stdio → HTTPS), 
**sem mexer no Core nem nas Tools**.

### 4.2 Diagrama

```
                     ┌──────────────────────────────────────────┐
                     │         Software Claude · Agente         │
                     │       (Preventivo, Pós-NPS)              │
                     └──────────────────┬───────────────────────┘
                          ┌─────────────┴─────────────┐
               FASE 1     │                           │     FASE 2
          (stdio = local) ▼                           ▼     (rede)
        ┌──────────────────────────┐     ┌──────────────────────────────┐
        │  stdio                   │     │  FIREWALL / GATEWAY MCP      │
        │  Claude Desktop lança    │     │  TLS · OAuth2 · IP allowlist │
        │  o processo no computador│     │                              │
        └────────────┬─────────────┘     └───────────────┬──────────────┘
                     │                                   ▼
                     │                   ┌──────────────────────────────┐
                     │                   │  HTTPS / SSL                 │
                     │                   │  Streamable-HTTP  /mcp       │
                     │                   └───────────────┬──────────────┘
                     └──────────────┬────────────────────┘
                                    ▼
        ╔═══════════════════════════════════════════════════════════╗
        ║               Código do servidor MCP em Java              ║
        ║   ┌─────────────────────────────────────────────────────┐ ║
        ║   │ TOOLS  buscar_cliente · clientes_inativos ·         │ ║
        ║   │        historico_relacionamento · consultar_wiki    │ ║
        ║   └───────────────────────┬─────────────────────────────┘ ║
        ║                           ▼                               ║
        ║   ┌─────────────────────────────────────────────────────┐ ║
        ║   │ LÓGICA DO CORE  recência (enviar≠contato) ·         │ ║
        ║   │                 RFM · entity resolution             │ ║
        ║   │                       (CNPJ→telefone→razão)         │ ║
        ║   └───────────────────────┬─────────────────────────────┘ ║
        ║                           ▼                               ║
        ║   ┌─────────────────────────────────────────────────────┐ ║
        ║   │ WIKI  guias de atendimento (texto Markdown)         │ ║
        ║   └─────────────────────────────────────────────────────┘ ║
        ╚═══════════════════════════════┬═══════════════════════════╝
                            ┌───────────┴─────────────────┐
            FASE 1 (EXCEL)  ▼                             ▼  FASE 2 (API)
        ┌───────────────────────────┐    ┌──────────────────────────────┐
        │  LEITOR DE EXCEL (apache) │    │  Cliente 360 em MySQL        │
        │  N1 · Megazap ·           │    │   +                          │
        │  People · NPS             │    │  de-para telefone→cliente    │
        └───────────────────────────┘    └──────────────────────────────┘
                                                          ▲
                                                          │
                                         ┌────────────────┴─────────────┐
                                         │  INGESTÃO / consolidação     │
                                         └──────────────────────────────┘
                                                          ▲
                                                          │
                                         ┌────────────────┴─────────────┐
                                         │  APIs externas               │
                                         │  N1 · People · Megazap       │
                                         └──────────────────────────────┘

        ┌───────────────────────────────────────────────────────────────┐
        │  ATENDENTE  valida e edita → (rascunho aprovado) → cliente    │
        │  (Megazap / e-mail)                                           │
        └───────────────────────────────────────────────────────────────┘
```

### 4.3 Componentes

- **Agente (Claude):** Atendente ou Claude seleciona o agente conforme a tarefa. Agente chama o MCP com suas tools, 
agente lê a wiki, Claude redige rascunhos. Não envia nada sozinho.
- **Servidor MCP (Java com Spring AI):** expõe as tools do MCP server; contém o núcleo de regras (guard rails); lê a wiki.
- **Wiki:** conhecimento curado (princípios, tom, tratativas, perguntas e governança), consumida via `consultar_wiki` do MCP.
- **Adaptador Excel (Fase 1):** lê os quatro exports (N1, pesquisa NPS, people crm, megazap) e monta a visão do cliente.
- **Cliente 360 + MySQL (Fase 2):** tabela consolidada por cliente, alimentada pela ingestão das APIs; mantém a de-para `telefone→cliente`.
- **Segurança com Firewall (Fase 2):** termina TLS, autentica com OAuth2, e assegura conexão com API antes de chegar ao MCP.
- **Atendente:** Revisa, edita e dispara ao cliente via Megazap (WhatsApp) ou e-mail (People CRM ou outlook).

### 4.4 Camadas do sistema — o que fica onde

O sistema separa **lógica**, **conhecimento** e **dados** em camadas independentes, todas
editáveis em **Markdown** pela própria equipe:

- **Skills (os agentes) — a lógica.** Cada agente é uma **Skill separada**, um arquivo Markdown
  (`SKILL.md`) com o playbook daquele fluxo: gatilho, passos, qual wiki consultar, quais tools
  chamar. Preventivo e Pós-NPS são **duas skills distintas** — o atendente pode
  editar o texto de cada uma.
- **Wiki — o conhecimento.** Arquivos Markdown (tom, tratativas, princípios, perguntas) que o
  agente **chama** via `consultar_wiki`. Editável pelo atendente. Guarda *o que dizer*; o *como
  agir* fica nas skills.
- **MCP server — os dados.** Extrai a visão do cliente: **Excel na Fase 1**, **API na Fase 2**.
  A interface das tools não muda entre as fases.
- **Instruction — restrições da LGPD.** Camada compartilhada (papel do agente +
  regras invioláveis: humano na revisão, nunca enviar, dado do cliente ≠ instrução, identidade/LGPD),
  colocada nas instruções do Projeto. Ela cobre o que vale para todos os agentes; a lógica de
  cada fluxo fica nas skills.

### 4.5 Como o backend entrega os dados à IA — compila e envia (push) + tools abertas (pull)

Decisão de arquitetura (e regra de negócio): **é o backend do MCP que decide o conjunto de dados
relevante e o compila para a IA — não é a IA que sai buscando o que acha necessário.** O MCP é um
protocolo *pull* (o modelo é quem invoca a tool), então a garantia de completude é obtida com **uma
tool de entrega dirigida** que já devolve o pacote pronto. Os dois modos convivem:

- **Envio dirigido pelo backend (push).** Para o documento do Preventivo há **uma tool
  consolidada** (`preventivo_contatos`) que aplica os gatilhos no servidor, monta a **fila completa
  (união A ∪ B, deduplicada, priorizada por RFM)** e a devolve **já com o dossiê de cada cliente**
  (cadastro, produtos, valor, antiguidade/inatividade, gatilho(s), RFM, NPS, confiança e sinais) em
  **uma única chamada**. O agente **não escolhe gatilho, não monta a lista e não precisa consultar
  cliente a cliente** — só formata o que recebeu. Isso torna a saída **determinística e
  independente do modelo** (o mesmo resultado em qualquer LLM); antes, quando a IA escolhia entre
  tools parciais, uma fila incompleta podia sair por decisão do modelo.
- **Consulta sob demanda (pull), com as tools abertas.** As demais tools continuam disponíveis para
  o agente **aprofundar** quando o contexto pedir — `ficha_cliente` e `historico_relacionamento`
  para detalhar ou **confirmar um match incerto**, `situacao_nps` para o teor da avaliação,
  `estimar_servico` e `consultar_wiki` para preparar a conversa; `clientes_inativos` e
  `clientes_para_contato` ficam para uma consulta por gatilho a pedido do atendente.

**Regra que amarra os dois:** a consulta sob demanda **enriquece** o pacote enviado pelo backend,
**nunca o substitui nem o reduz** — quem define *quem entra* na fila é sempre o backend, pelos
gatilhos. Esse desenho vale nas duas fases: muda a origem do dado (Excel → API/MySQL), não a regra
de que o servidor compila a fila e a IA a formata.

---

## 5. As fases

| Aspecto               | **Fase 1 — Excel**                                | **Fase 2 — Integração**                                        |
|-----------------------|---------------------------------------------------|----------------------------------------------------------------|
| Fonte de dados        | Planilhas exportadas (N1, People, Megazap, NPS)   | APIs → Cliente 360 no MySQL                                    |
| Conversas do cliente  | Exportadas ou copiadas manualmente pelo atendente | Exportadas ou copiadas manualmente (integração fica na Fase 3) |
| Transporte MCP        | **stdio** (local)                                 | **HTTPS** (remoto)                                             |
| Onde roda             | Máquina do operador (Claude Desktop)              | Servidor publicado, atrás de gateway                           |
| Exposição de rede     | Nenhuma                                           | Endpoint HTTPS público (da nuvem da Anthropic)                 |
| Atualização dos dados | Manual (novo export → `recarregar`)               | Periódica (jobs de ingestão)                                   |
| Banco de dados        | Não (lê Excel)                                    | **MySQL** (Cliente 360)                                        |
| Esforço/risco         | Baixo — valida valor rápido                       | Maior — infra, segurança, descoberta de API                    |

**Fase 1** valida o valor com **dados fictícios/mascarados** numa máquina só, sem infraestrutura pública.
**Fase 2** escala para a equipe e para dados ao vivo.
**Fase 3** (futura, não detalhada aqui) integra o **Megazap** para que as conversas cheguem ao
agente sem export manual — depende de **contato com o Megazap** para o recebimento das mensagens.

---

## 6. Ferramentas por fase

| Ferramenta                         | Papel                                                                                        | Fase 1 |       Fase 2       |
|------------------------------------|----------------------------------------------------------------------------------------------|:------:|:------------------:|
| **Agentes (skill)**                | Lógica de cada agente em Markdown editável (Preventivo/Pós-NPS)                              |   ✔    |         ✔          |
| **Wiki**                           | Conhecimento em Markdown (tom, tratativas, perguntas), **chamada** via `consultar_wiki`      |   ✔    |         ✔          |
| **Instructions (guardrails)**      | Regras invioláveis compartilhadas da LGPD para não duplicar regra nos agentes                |   ✔    |         ✔          |
| **MCP server**                     | Código (servidor) extrai dados do excel ou da API com tag tools para o agente receber o dado |   ✔    |         ✔          |
| **Acesso ao computador via stdio** | Claude Desktop lança o servidor local e lê os Excel                                          |   ✔    |         —          |
| **Integração com API via HTTPS**   | Código (servidor) busca dados de N1/People/Megazap                                           |   —    |         ✔          |
| **Leitor de Excel**                | Código (servidor) lê os exports e montar a visão do cliente                                  |   ✔    | ✔ (fontes sem API) |
| **Banco relacional MySQL**         | Código (servidor) Cliente 360 + de-para telefone→cliente                                     |   —    |         ✔          |
| **Testes automatizados**           | Teste do código do MCP e do servidor                                                         |   ✔    |         ✔          |
| **Conexão encriptada TLS**         | Criptografar a integração com API (endpoint e banco)                                         |   —    |         ✔          |
| **Firewall no MCP (gateway)**      | Segurança para revisar dados do MCP: Filtrar origem e proteger o endpoint                    |   —    |         ✔          |

> Na Fase 1 não há TLS nem firewall porque **não há rede** externa: o servidor roda no próprio computador e conversa com o Claude Desktop por stdio. 
> Esses controles de segurança entram na Fase 2, quando há integração com API externa.

---

## 7. Pontos de integração com APIs externas

O **N1 é a espinha dorsal** (cadastro, contrato, valor, CNPJ). As demais fontes penduram
recência e contexto nele, pelo casamento em cascata.

| Fonte                  | Dado que fornece                                                                                                   | Chave de junção                | Método (a confirmar)                                       | Fase       |
|------------------------|--------------------------------------------------------------------------------------------------------------------|--------------------------------|------------------------------------------------------------|------------|
| **N1 (ERP)**           | Cadastro PJ, contratos, valor, datas de fatura/chamado/renovação, **visita**, **atualização cadastral/contratual** | **CNPJ**                       | Export xlsx → API REST                                     | Fase 1 → 2 |
| **Megazap**            | Data da **última mensagem** por número; **recebida × enviada**                                                     | **Telefone**                   | Export → API/webhook                                       | Fase 1 → 2 |
| **People CRM**         | Última interação / posição no funil                                                                                | Telefone / CNPJ                | Export → API                                               | Fase 1 → 2 |
| **NPS (formulário)**   | Nota + comentário + data (engajamento)                                                                             | CNPJ (opcional) / razão social | Planilha do formulário                                     | Fase 1     |
| **Megazap (saída)**    | Recebe o **rascunho** aprovado para envio manual                                                                   | Telefone                       | Manual (copiar/colar) → API                                | Fase 1 → 2 |
| **Megazap (conversa)** | Mensagens do cliente **durante o atendimento** (conteúdo)                                                          | Telefone                       | Fase 1–2: export/copiar manual · Fase 3: API/webhook → MCP | Fase 1 → 3 |

**Cruzamento de registros (o ponto mais complexo):** cada fonte usa uma chave diferente, então o
cruzamento segue uma **cascata** — CNPJ (exato) → telefone (provável) → razão social normalizada
(incerto). Cada casamento carrega uma **marca de confiança**; só *exato* e *provável* entram na
fila automática — *incerto* vai para **conferência humana**. A de-para `telefone→cliente` é um
ativo versionado (um telefone pode atender mais de uma empresa; uma empresa pode ter vários
contatos).

**Chave interna do N1 × chave de identidade/busca por CNPJ (Fase 1).** Na Fase 1, o N1 vem em **três exports**
que são amarrados entre si pelo **código interno do cliente** — a **coluna `Cod`** do N1‑Contatos, que
reaparece como **prefixo `11111- `** nas colunas `Cliente` (N1‑Sistema) e `Pessoa` (N1‑NF). Esse código
é uma **chave só de ingestão** para dentro do back-end: vale para juntar os excel cadastro ↔ contrato ↔ nota fiscal. 
A partir do cliente já consolidado, a **identidade** e a **busca do agente** (`ficha_cliente`, `situacao_nps`)
passam a usar o **CNPJ** (ou a **razão social** na busca) — o código serve apenas de **reserva** quando o cliente 
não tem CNPJ, e nunca é exposto ao agente. Detalhe completo do fluxo de chaves no **3‑cruzamento (§4 e §4.1)**.
(Na Fase 2, a API do N1 tende a devolver o cliente já consolidado por CNPJ, e o código interno deixa de
ser necessário para o join.)

**Integração de saída:** o rascunho aprovado pode ser copiado manualmente no Megazap (Fase 1)
ou, se o Megazap expuser API, criado como **rascunho** para envio humano (Fase 2). Nunca envio
automático.

**Integração de entrada (a conversa do cliente):** nas **Fases 1 e 2**, as conversas do Megazap são
**exportadas ou copiadas manualmente** pelo atendente para o chat do agente — não há acesso
automático ao diálogo ao vivo, e mensagens de áudio precisam ser transcritas ou resumidas. Na
**Fase 3**, as conversas são **integradas**: o backend captura as mensagens recebidas do Megazap
(via API/webhook) e as **envia à IA pelo MCP** (por uma tool de conversa), para o agente
acompanhar o diálogo durante o atendimento — áudio ainda requer transcrição. Isso **depende de
contato com o Megazap** para viabilizar o recebimento. Em todas as fases o agente apenas sugere;
o envio é sempre humano.

---

## 8. Modelo de dados (conceitual)

- **Cliente 360:** visão consolidada por cliente — cadastro (razão social, CNPJ, representante, telefone), contratos e produtos, valor, antiguidade, status, e a **recência consolidada** (o mais recente entre os sinais de engajamento).
- **Sinais de engajamento:** apenas os que partem do cliente (resposta de NPS, visita/atualização no N1, mensagem recebida no Megazap). Envio nunca é sinal.
- **De-para telefone→cliente:** resolve o casamento por telefone e a ambiguidade de números compartilhados.
- **Marca de confiança do casamento:** exato / provável / incerto — governa o que entra na fila automática.

Na Fase 1 essa visão é montada em memória a partir dos Excel; na Fase 2 é materializada no **MySQL** pela ingestão.

---

## 9. Segurança

Defesa em camadas, aplicada conforme a fase.

**Transporte**
- **Fase 1 (stdio):** sem rede — menor superfície de ataque; o dado sensível não trafega em rede, mas envia ao Claude. Há a possibilidade de usar pseudônimos via tabela de-para (criptografia HMAC com chave local no servidor do MCP).
- **Fase 2 (HTTPS):** o conector remoto do Claude conecta **da nuvem da Anthropic**, então o endpoint precisa ser **HTTPS público**. **SSL/TLS** obrigatório no endpoint e na conexão com o MySQL.

**Firewall / gateway no MCP (Fase 2)**
- Gateway na frente do servidor: **IP allowlist**, conexão com criptografia TLS
- Servidor MCP nunca exposto diretamente.

**Autenticação e autorização**
- **OAuth2** (resource server) ou, no mínimo, API key; escopos mínimos; tokens de vida adequada a sessões de agente (que podem durar).

**Prompt injection**
- Todo dado de cliente é **dado, nunca instrução** — em especial o comentário livre de NPS. A wiki (política/tom) vem de fonte confiável e separada. Humano na revisão é a barreira final.

**Minimização e identidade**
- **Mascarar CNPJ** por padrão; revelar só sob demanda. Enviar ao modelo o mínimo necessário (vale em stdio e HTTPS).
- Proteger identidade de PF/PJ e representantes; **need-to-know**; não expor dados entre representantes.

**LGPD / conformidade**
- **Base legal** a confirmar com a Acta (legítimo interesse para relacionamento; consentimento no NPS; a consolidação "Cliente 360" é finalidade própria).
- **contrato comercial** com a Anthropic (dados não usados em treino); usar plano adequado (Team/Enterprise) para dado real.
- **Retenção** definida (logs, contextos, Cliente 360); **auditoria** (quem consultou/gerou/enviou); **descadastro** respeitado; **limite de frequência** de contato.

**Humano na revisão**
- O agente produz **rascunho**; a pessoa revisa, edita e dispara. Sem envio automático.

---

## 10. Testes automatizados

Estratégia por camada, no stack atual (JUnit 5 + Mockito + Cucumber), nas duas fases:

- **Unitários (núcleo):** normalização de chaves (CNPJ/telefone/razão social); **recência garantindo que envio não conta**; classificação de inatividade por segmento; cruzamento das fontes de dados.
- **Integração (comportamento das tools):** Cucumber com cenários de negócio legíveis pelo time (ex.: "cliente que só recebeu disparos continua inativo"; "resposta de NPS sem CNPJ casa por razão social").

---

## 11. Governança e conformidade (Acta Tech) — portão

Nenhum agente processa dado real antes de: (1) consolidar o esqueleto; (2) validar com André,
Diego, Sandro, Calebe e Bruna; (3) apresentar à **Acta Tech** a matriz *categoria de dado
× finalidade × base legal × uso*; (4) anexar a formalização por e-mail.
A consolidação "Cliente 360" entra como **finalidade própria** nessa matriz.

---

## 12. Perguntas a serem respondidas

**Fornecedores (descoberta de API)**
- **N1:** expõe API REST? Traz e **data** visitas, atualizações cadastrais/contratuais, última fatura/chamado/renovação e valor por cliente? Auth, sandbox, webhooks?
- **Megazap:** expõe a **data da última mensagem** por número? Distingue **recebida × enviada**? Tem webhook de recebimento? Permite criar **rascunho**/enviar por API?
- **People:** expõe **última interação** e **posição no funil** por contato? Distingue resposta do cliente de contato feito por nós?
- **NPS:** onde ficam as respostas (formulário/planilha)? O CNPJ está preenchido? Como exportar de forma padronizada?

**Negócio (comercial)**
- Limiar de **antiguidade** ("cliente antigo")? Limiar de **meses sem engajamento** ("sem contato recente")?
- Peso relativo na priorização (valor × recência)? **Limite de frequência** de contato por cliente?
- Confirmar os **segmentos** do Preventivo e a abordagem de cada.

**Jurídico / Acta**
- **Base legal** do contato proativo, do NPS e da **consolidação Cliente 360**?
- **Retenção** de logs, contextos e da base consolidada? Regras de **proteção de identidade** entre representantes?

**Técnico / plataforma**
- Qual **taxa de cruzamento** é aceitável antes de escalar? Rodar amostra real para medir?
- Quem mantém a de-para **telefone→cliente**?
- Plano do Claude (**Team/Enterprise**) e **DPA** (contrato) para dado real? Quem é *Owner* para adicionar o conector?
- Onde será **hospedado** o endpoint HTTPS e quem opera **gateway/firewall** e o **MySQL**?

**Produto**
- **Dashboards / acompanhamento longitudinal** — escopo de fase futura?

---

## 13. Roadmap de implementação

| Ordem | Entrega                                                                      | Fase      |
|-------|------------------------------------------------------------------------------|-----------|
| 1     | Governança: esqueleto → validação → Acta (matriz) → formalização             | Início    |
| 2     | Wiki: princípios, tom, tratativas, perguntas                                 | Todas     |
| 3     | Núcleo de regras + testes (recência, RFM, casamento)                         | Todas     |
| 4     | Servidor MCP + tools + leitor de Excel (stdio)                               | Fase 1    |
| 5     | Conectar no Claude Desktop e validar com dados fictícios/mascarados          | Fase 1    |
| 6     | Integração com APIs externas (N1, People, Megazap)                           | Transição |
| 7     | Ingestão + Cliente 360 no MySQL                                              | Fase 2    |
| 8     | MCP remoto (HTTPS) + gateway/firewall + OAuth/TLS                            | Fase 2    |
| 9     | Rascunho de saída ao Megazap (humano na revisão)                             | Fase 2    |
| 10    | Integração das conversas do Megazap ao agente (requer contato com o Megazap) | Fase 3    |

---

## 14. Riscos e decisões

| Risco / decisão                                             | Encaminhamento                                                                                                    |
|-------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| **Qualidade do cruzamento de registros** (maior risco)      | Cascata CNPJ→telefone→razão social + marca de confiança + revisão humana dos incertos; medir taxa em amostra real |
| N1/Megazap/People podem não expor "última interação" datada | Confirmar na descoberta; usar o que houver; fontes mistas (Excel + API)                                           |
| Endpoint remoto exige exposição pública                     | Gateway/firewall + TLS + OAuth desde o primeiro dia da Fase 2                                                     |
| Telefone compartilhado entre empresas                       | De-para `telefone→cliente` versionada                                                                             |
| Base legal (contato proativo, Cliente 360)                  | Confirmar com a Acta antes de dado real (portão)                                                                  |
| Confundir disparo com contato                               | Regra na modelagem: **enviar ≠ contato**                                                                          |
| **IA montar fila incompleta** (escolher uma tool parcial)   | **Backend compila e envia a fila** por uma tool consolidada (`preventivo_contatos`, push); tools abertas só para **enriquecer** sob demanda (pull), nunca para reduzir — §4.5 |
| Envio automático ao cliente                                 | Fora de escopo: sempre rascunho + validação humana                                                                |


