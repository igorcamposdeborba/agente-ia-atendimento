# Agentes de Atendimento — Millenium (pacote para o software Claude)

Pacote pronto para configurar os agentes no **software do Claude** (plano **Pro** ou **Team**).
Dois agentes (skills), uma instrução de guardrails e a wiki que eles consultam.

## Estrutura de pastas

```
millenium-agentes/            ← pasta principal (o AGENTE fica aqui)
├── preventivo/
│   └── SKILL.md              ← agente Preventivo (gatilhos: inatividade + manutenção vencida)
├── pos-nps/
│   └── SKILL.md              ← agente Pós-NPS
├── instrucao-guardrails.md   ← instrução do Projeto (guardrails; NÃO é skill)
├── wiki/                     ← CONHECIMENTO (consultado via consultar_wiki)
│   ├── principios/
│   │   ├── empatia.md
│   │   ├── escuta-ativa.md
│   │   └── comunicacao-nao-violenta.md
│   ├── tom.md
│   ├── tratativas.md
│   ├── perguntas-descoberta.md
│   ├── parametros.md
│   └── governanca.md
└── fonte/                    ← ARQUIVOS DE IMPORTAÇÃO (lidos pelo MCP)
    ├── n1_contratos.xlsx
    ├── megazap_mensagens.xlsx
    ├── nps_respostas.xlsx
    └── people_crm.xlsx
```

## As três camadas

- **Skills (agentes) = a lógica** — `preventivo/SKILL.md`, `pos-nps/SKILL.md`.
- **Instrução = os guardrails** — `instrucao-guardrails.md` (vai nas instruções do Projeto).
- **Wiki = o conhecimento** — pasta `wiki/`, consultada pela tool `consultar_wiki`.
- **MCP = os dados** — lê os Excel de `fonte/` e serve as tools (`clientes_inativos`, `clientes_para_contato`, `situacao_nps`, `consultar_wiki`, etc.).

## Configurar no plano **Team** (operação com atendentes)

Feito **uma vez pelo Owner**, compartilhado com o time:

1. **Skills:** *Configurações da organização → Skills* → habilitar "Code execution and file creation" e "Skills". Provisionar `preventivo` e `pos-nps` para a organização.
2. **Instrução:** criar o **Projeto** "Atendimento Millenium" e colar `instrucao-guardrails.md` nas instruções do Projeto.
3. **Conector MCP:** adicionar o conector (endpoint **HTTPS** — no app web a Anthropic conecta da nuvem dela). O servidor MCP lê `fonte/` e serve `consultar_wiki` sobre `wiki/`.
4. **Wiki:** servida pelo conector (`consultar_wiki`) **ou** subida como conhecimento do Projeto.
5. **Compartilhar** o Projeto com os atendentes. Eles só usam — não configuram nada.

## Configurar no plano **Pro** (piloto de uma pessoa)

Cada passo é feito pelo próprio usuário:

1. **Skills:** *Settings → Capabilities* (ligar "Code execution and file creation") + *Customize → Skills* → adicionar `preventivo` e `pos-nps`.
2. **Instrução:** criar um Projeto e colar `instrucao-guardrails.md`.
3. **Conector MCP:** adicionar o conector. No Pro (usuário único) dá para rodar o servidor **local via Claude Desktop (stdio)**, lendo `fonte/` na própria máquina — sem endpoint público.
4. **Wiki:** via `consultar_wiki` ou como conhecimento do Projeto.
5. **Dados fictícios/mascarados** e **treino desligado** (*Privacy Settings*) para o piloto — dado real só depois do portão da Acta.

## Observações

- A `description` de cada `SKILL.md` é o **gatilho**: é o que faz o Claude escolher a skill certa. Ajuste as palavras se quiser mudar quando cada uma dispara.
- O texto das **skills** e da **wiki** é Markdown — o atendente/admin edita direto.
- O passo exato de "subir a skill" no app pode mudar; confirme na tela de Skills da sua versão.
- Os Excel em `fonte/` são de exemplo (fictícios). Troque pelos exports reais só após a Acta.
