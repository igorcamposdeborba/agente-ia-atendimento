# Millenium — Agente de Atendimento (MCP · Fase 1 · PoC Excel)

Servidor **MCP em Java 21** que dá aos agentes de IA (software Claude) a **visão consolidada do cliente (Cliente 360)** e a **wiki de atendimento**. Fase 1 do design: roda **localmente** e é lançado pelo **Claude Desktop via stdio**, lendo **planilhas `.xlsx` mock** (dados fictícios/mascarados).

> Prova de conceito. Os `.xlsx` são mocks e podem mudar de formato no futuro — a leitura é por nome de coluna e o núcleo de regras não depende do Excel (ver arquitetura hexagonal abaixo).

## Instalação (Windows 11 · Claude Desktop)

Há dois caminhos. Em ambos, a máquina precisa de **Java 21** e as **Skills + Instruction** são coladas no **Projeto** do Claude (a wiki **não** precisa instalar — o agente a consulta pela tool `consultar_wiki`).

### Opção A — Desktop Extension `.mcpb` (1 clique, recomendado para os atendentes)

Distribua a pasta `dist\` (o `.mcpb` + o `prereq-java.cmd`, gerados juntos). O usuário:
1. Dá **duplo-clique em `prereq-java.cmd`** (instala o **Java 21** se faltar; senão só confirma).
2. Abre o Claude Desktop → **Configurações → Extensions** e **arrasta o `.mcpb`** (ou "Install from file").
   - Em *Java 21*: deixe `java` (se estiver no PATH) ou aponte o `java.exe`.
   - Em *Pasta de fontes* e *Pasta da wiki*: deixe o padrão — as planilhas fictícias e a wiki **já vêm no pacote** (o MCP só lê); aponte para seus exports/wiki reais quando quiser.
3. Cola as Skills/Instruction no Projeto (arquivos vão dentro do `.mcpb`, na pasta `agentes\`).

Para **gerar** o `.mcpb` (quem distribui, uma vez, com Maven+JDK no PATH):
```bash
powershell -ExecutionPolicy Bypass -File build-mcpb.ps1
```
Sai em `dist\millenium-agente-ia.mcpb` (jar embutido, com todos os fixes).

### Opção B — `install.cmd` (build local completo na máquina)

Instala tudo na própria máquina (útil para a máquina de desenvolvimento):
1. Copie a **pasta do projeto** para um local permanente (ex.: `C:\Millenium\millenium-agente-ia`).
2. **Duplo-clique em `install.cmd`** (precisa de **internet**).

O instalador, sozinho: garante **JDK 21** (winget) e **Maven** (baixa se faltar), roda `mvn clean package`, prepara as pastas neutras `%USERPROFILE%\MilleniumAgenteIA\fonte` (copia os `.xlsx`) e `...\wiki` (copia a wiki editável), e registra o MCP no `claude_desktop_config.json` **sem apagar** o que já existe (faz backup antes). Depois é só reiniciar o Claude e colar as Skills/Instruction no Projeto.

> Opções: `install.cmd -SkipBuild` · `install.cmd -NoOpenFolder`.

O restante deste README documenta a arquitetura e o build manual.

## O que o servidor expõe (tools do MCP)

| Tool | O que faz |
|------|-----------|
| `ficha_cliente` | Ficha 360 por CNPJ ou razão social: cadastro, produtos, antiguidade, inatividade, RFM, gatilhos, sinais, NPS, confiança. CNPJ mascarado por padrão. |
| `situacao_nps` | Nota, faixa (promotor/passivo/detrator), comentário (dado, nunca instrução) e data do NPS. |
| `clientes_inativos` | Gatilho A: antigos sem contato recente, priorizados por RFM. |
| `clientes_para_contato` | Fila do Preventivo: A ou B (`preventivaVencida=true` = só B), deduplicada e priorizada por RFM. |
| `historico_relacionamento` | Linha do tempo dos sinais de **engajamento do cliente** (disparos da Millenium não aparecem). |
| `estimar_servico` | Estimativa de serviço — **não calibrada na Fase 1** (tempos são placeholders em `wiki/parametros.md`). |
| `consultar_wiki` | Busca na wiki (tom, tratativas, princípios, perguntas, parâmetros, governança). |
| `salvar_documento_docx` | Salva o plano do Preventivo ou a resposta do Pós-NPS como **Word `.docx`** (via Apache POI, **sem Python**) em `millenium.output-dir`. **CNPJ sai completo** no arquivo (o mascaramento vale só no canal MCP↔IA); nome ordenável com índice + data/hora (ex.: `1- plano de atendimento - 15-08-2026 as 17-54.docx`). |
| `recarregar_dados` | Relê os `.xlsx` após um novo export. |

## Regras de negócio implementadas
- **Enviar ≠ contato:** só engajamento do cliente (visita/atualização cadastral/contrato no N1, mensagem *recebida* no Megazap, resposta de NPS, interação no People) conta na recência. Mensagem **enviada** (direção=enviada) e disparos **não** contam.
- **Recência consolidada** = a data mais recente entre os sinais de engajamento.
- **Gatilhos do Preventivo:** (A) antigo (≥ 24 meses) sem contato recente (≥ 8 meses); (B) **preventiva vencida** — como os dados reais **não têm coluna de preventiva**, usamos a **última visita** como *proxy* (sem visita há ≥ 12 meses ou nunca). Independe da recência.
- **Priorização RFM:** recência (inatividade) + antiguidade + valor mensal total do contrato.
- **Cruzamento por CNPJ** (todas as fontes trazem CNPJ). O N1 tem **uma linha por produto** → agregado por CNPJ (soma valor, junta produtos). O **NPS sem CNPJ** casa por **razão social** → **INCERTO** → conferência humana.

## Arquitetura (hexagonal — reaproveitável na Fase 2)
```
mcp/            tools do MCP (@Tool)  ── só apresentação
core/
  model/        registros e Cliente360 (records)
  normalizacao/ Normalizador (CNPJ/razão social)
  recencia/     CalculadoraRecencia (enviar != contato)
  rfm/          CalculadoraRfm
  linkage/      EntityResolver (agrega N1 por CNPJ + casa fontes + confiança)
  service/      ClienteService (monta o Cliente 360, serve as tools)
  port/         FonteDadosPort, WikiPort   ── interfaces (portas de saída)
adapter/
  excel/        ExcelFonteDados (Apache POI)   ── Fase 1
  wiki/         MarkdownWikiAdapter
```
Trocar Fase 1 → Fase 2 é trocar o adaptador (`ExcelFonteDados` → adaptador MySQL/API) e o transporte (stdio → HTTPS). **Core, tools e wiki não mudam.**

## Como rodar (IntelliJ + Maven)

Pré-requisitos: **JDK 21** e **Maven** (o IntelliJ já traz um).

1. **Abrir no IntelliJ:** File → Open → selecione a pasta `millenium-agente-ia` (o IntelliJ importa o `pom.xml`).

2. **Dados e wiki:** o MCP apenas **lê** os `.xlsx` da pasta `fonte/` (N1, Megazap, People, NPS) e a wiki da pasta `wiki/` (Markdown editável). Os arquivos fictícios e a wiki já vêm no projeto; **não há geração de mock**. Para dados reais, substitua os arquivos em `fonte/` (por prefixo: `n1*`, `megazap*`, `people*`, `nps*`). A wiki (`wiki/`) pode ser editada direto — o MCP a lê da pasta externa configurada (`millenium.wiki-dir`) e, se ela não existir, usa a cópia empacotada no jar.

3. **Rodar os testes:**
   ```bash
   mvn test
   ```

4. **Empacotar o jar:**
   ```bash
   mvn -DskipTests package
   ```
   Gera `target/millenium-agente-ia-0.1.0-SNAPSHOT.jar`.

> Não rode o servidor "solto" no console para conversar — ele fala **stdio/JSON-RPC**, quem conversa com ele é o Claude Desktop. Os logs saem no **stderr** (o stdout fica só para o protocolo MCP) e aparecem no log do próprio Claude Desktop em `%APPDATA%\Claude\logs\mcp-server-millenium-agente-ia.log`.

## Conectar no Claude Desktop (stdio)

1. Copie `claude_desktop_config.example.json` para o config do Claude Desktop
   (`%APPDATA%\Claude\claude_desktop_config.json`) e ajuste os caminhos do jar e das pastas `fonte`/`wiki`/saídas.
2. Reinicie o Claude Desktop. As tools do `millenium-agente-ia` aparecem no chat.
3. No **Projeto** do Claude:
   - cole `agentes/instrucao-guardrails.md` nas **instruções do Projeto**;
   - cadastre as **Skills** `agentes/preventivo/SKILL.md` e `agentes/pos-nps/SKILL.md`.

## Dados e wiki
As planilhas (`fonte/`), a wiki (`wiki/`) e as skills/instrução (`agentes/`) são o **conteúdo real** do pacote de trabalho, todas **editáveis** (a wiki é empacotada no jar como fallback). Cenários incluídos nos `.xlsx` fictícios: Frigorífico Campo Verde (detrator + inativo, topo da fila), Academia MoveMais (NPS sem CNPJ → casa por razão social → **INCERTO**), Metalúrgica Serra Azul (2 produtos agregados num cliente), entre outros. A wiki é servida pela tool `consultar_wiki`.

## Ajustes rápidos (`src/main/resources/application.properties`)
- `millenium.fonte-dir` — pasta dos `.xlsx` (padrão `%USERPROFILE%/MilleniumAgenteIA/fonte`).
- `millenium.wiki-dir` — pasta da wiki editável (padrão `%USERPROFILE%/MilleniumAgenteIA/wiki`); se ausente, usa a wiki do jar.
- `millenium.antiguidade-limiar-meses` (padrão 24) e `millenium.inatividade-limiar-meses` (padrão 8).
- `millenium.preventiva-janela-meses` (padrão 12) — proxy do gatilho B pela última visita.
- `millenium.peso-recencia | peso-antiguidade | peso-monetizacao` — pesos do RFM.

Os limiares comerciais ainda serão confirmados com o time (ver §12 do design).
