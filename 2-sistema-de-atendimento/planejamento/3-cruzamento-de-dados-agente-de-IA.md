# Planejamento de Cruzamento de Dados — Agente de Atendimento com MCP
### Millenium Tecnologia · Fase 1 (PoC Excel)

> Documento-irmão do **1‑design‑document** (tecnologia) e do **2‑business** (negócio). Aqui está
> o **como os dados de cinco planilhas viram uma visão única por cliente (Cliente 360)** — a base
> sobre a qual os gatilhos (inatividade / preventiva), o RFM e os dois agentes trabalham.
> A fonte de verdade deste documento é o **field mapping** (`0- Field mapping excel millenium.xlsx`)
> cruzado com os **exports reais** em `fonte/` e com a implementação do MCP em Java.

---

## 1. Objetivo

Transformar exports não normalizados (cada um com nomes de coluna, chaves e granularidade próprios)
numa **linha por cliente** que responda: *quem é, o que contratou, quanto vale, quando foi o
último engajamento dele, e com que confiança conseguimos casar essas fontes.* Esse consolidado
alimenta:

- **Gatilho A (inatividade):** antiguidade alta + recência baixa;
- **Gatilho B (preventiva vencida):** proxy pela última visita/compra;
- **Priorização RFM:** recência · antiguidade · monetização;
- **Ficha do cliente** e **histórico de engajamento** para o copiloto de conversa.

O cruzamento roda **em memória** na Fase 1 (Java + Apache POI lendo `.xlsx`); na Fase 2 o **mesmo
núcleo de regras** consome API/MySQL (Cliente 360 materializado). A interface das tools não muda.

---

## 2. As cinco fontes (o que o field mapping declara)

Cada planilha recebe uma **categoria de fonte** e uma **prioridade de fonte** (1 = mais confiável
para resolver conflito de valor entre fontes). O **N1 é a espinha dorsal** e aparece em **três
exports distintos**.

| # | Arquivo (`fonte/`)                                  | CategoriaDaFonte       | PrioridadeFonte  | Papel no cruzamento                                                                                                                                               | Granularidade                |
|---|-----------------------------------------------------|------------------------|:----------------:|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------|
| 1 | PLANILHA N1 EX 2 – CONTATOS CADASTRADOS POR CLIENTE | `CADASTRO_ORGANIZACAO` |      **1**       | **Identidade-mestre**: razão social, nome fantasia, CNPJ/CPF, telefone, e‑mail, **código do cliente**                                                             | 1 linha por cliente          |
| 2 | PLANILHA N1 EX 3 – SAÍDAS DE PRODUTOS POR NF        | `NOTA_FISCAL`          |      **2**       | Vendas/faturas: produto vendido, **valor da NF**, **data de lançamento**                                                                                          | 1 linha por item de NF       |
| 3 | PLANILHA N1 EX 1 – SISTEMA CONTRATADO POR CLIENTE   | `PRODUTO`              |      **3**       | Contratos vigentes: serviço/plano, **início e fim de vigência**, telefone principal                                                                               | 1 linha por contrato‑serviço |
| 4 | PLANILHA MEGAZAP EXEMPLO                            | `WHATSAPP`             |      **4**       | **Auxiliar**: **registro de atendimento/conversa** (data de abertura), problema relatado, CNPJ quando houver — cada linha **conta como conversa** para a recência | 1 linha por atendimento      |
| 5 | NPS MILLENIUM EX                                    | `NPS`                  |      **5**       | Avaliação: nota (0–10), comentário, **carimbo de data/hora** (engajamento)                                                                                        | 1 linha por resposta         |

> **People CRM** aparece nos planos de negócio/tecnologia como fonte auxiliar, mas **não está no
> field mapping atual nem na pasta `fonte/`** (existe só na `prova-de-conceito/`). Tratamos People
> como fonte **opcional/futura**: o núcleo aceita, mas a Fase 1 corrente roda com as cinco acima.

---

## 3. Dicionário de campos canônicos (o coração do field mapping)

O field mapping projeta as colunas reais de cada planilha em **12 campos canônicos**, agrupados em
três **categorias de item** que também definem a ordem de exibição na ficha/documento.

| #  | Campo canônico          | Categoria do item  | CADASTRO (N1‑2)  | NOTA_FISCAL (N1‑3)  | PRODUTO (N1‑1)      | WHATSAPP (Megazap)  | NPS                       |
|----|-------------------------|:------------------:|------------------|---------------------|---------------------|---------------------|---------------------------|
| 1  | **CNPJ**                |      CONTATO       | `CnpjCpf`        | —                   | —                   | `CNPJ`              | —                         |
| 2  | **Telefone**            |      CONTATO       | `telefone`       | —                   | `TelefonePrincipal` | `TELEFONE`          | —                         |
| 3  | **RazaoSocial**         |      CONTATO       | `Nome`           | `Pessoa`            | `Cliente`           | `EMPRESA`           | —                         |
| 4  | **Email**               |      CONTATO       | `email`          | —                   | `Email`             | `E‑MAIL`            | —                         |
| 5  | **NomeFantasia**        |      CONTATO       | `Fantasia`       | —                   | —                   | —                   | `Empresa`                 |
| 6  | **InicioVigencia**      |      PRODUTO       | —                | `Data Lançamento`   | `Início Vigência`   | `DATA DE CRIAÇÃO`   | `Carimbo de data/hora`    |
| 7  | **FimDaUltimaVigencia** |      PRODUTO       | —                | —                   | `Fim Vigência`      | —                   | —                         |
| 8  | **ValorTotalNF**        |      PRODUTO       | —                | `Valor Total NF`    | —                   | —                   | —                         |
| 9  | **Produto**             |      PRODUTO       | —                | `Descrição`         | `Servico`           | —                   | —                         |
| 10 | **NPSRecomendacao**     |     AVALIAÇÃO      | —                | —                   | —                   | —                   | **Coluna D** (nota 0–10)  |
| 11 | **NPSExplicacao**       |     AVALIAÇÃO      | —                | —                   | —                   | —                   | **Coluna E** (comentário) |
| 12 | **MegazapProblema**     |     AVALIAÇÃO      | —                | —                   | —                   | `PROBLEMA`          | —                         |

Três leituras importantes desse dicionário:

1. **`InicioVigencia` é um campo de data “sobrecarregado”.** Ele significa coisas diferentes por
   fonte — início de contrato (N1‑1), data da NF (N1‑3), abertura do atendimento (Megazap), carimbo
   da resposta de NPS. Para as fontes com **vários registros por cliente** (NF, Megazap e NPS), o
   valor é a **data do último registro** (a mais recente). O núcleo **interpreta a data conforme a
   categoria da fonte** ao montar a recência (ver §6). Não é “a mesma data” em todas.
2. **NPS identifica o cliente por `NomeFantasia`, não por razão social.** A coluna `Empresa` do NPS
   casa melhor com a `Fantasia` do N1 do que com o `Nome` — confirmado nos dados reais (§7).
3. **A categoria do item ordena a saída**: CONTATO (quem contatar) → PRODUTO (contexto do
   contrato/venda) → AVALIAÇÃO (voz do cliente: NPS + problema do Megazap).

### 3.1 Regras de derivação por campo (comentários do field mapping)

Além do de‑para de colunas, o field mapping traz, **nos comentários das células**, como cada campo
é **calculado/extraído** pelo backend. Estas regras são normativas:

| Campo canônico      | Fonte(s)            | Regra de derivação (comentário do field mapping)                                                                                                                                                                                       |
|---------------------|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **ValorTotalNF**    | N1‑NF               | **Soma de todas as NF do cliente** = **valor do contrato**. O backend soma o `Valor Total NF` de todas as notas no campo `ValorTotalNF`.                                                                                               |
| **Email**           | todas               | **Extrair o domínio**: o texto **após o `@` e antes do `.`** (ex.: `teste1@teste.com.br` → `teste`). Em **CADASTRO_ORGANIZACAO** e **PRODUTO** a célula traz uma **lista de e‑mails separada por `;`** — extrair o domínio de cada um. |
| **RazaoSocial**     | N1‑NF e N1‑Sistema  | **Extrair somente o texto, ignorando o `Número-`** do prefixo (ex.: `11111- PADARIA SILVA` → `PADARIA SILVA`). No N1‑Contatos a razão social (`Nome`) já vem sem prefixo.                                                              |
| **InicioVigencia**  | N1‑NF, Megazap, NPS | **Calcular a data do último registro** (a mais recente entre as linhas do cliente). No N1‑Sistema é o `Início Vigência` do contrato, direto.                                                                                           |
| **NPSRecomendacao** | NPS                 | Ler pela **Coluna D** do Excel (a nota 0–10), não pelo texto da pergunta.                                                                                                                                                              |
| **NPSExplicacao**   | NPS                 | Ler pela **Coluna E** do Excel (o comentário), não pelo texto da pergunta.                                                                                                                                                             |

> O **domínio do e‑mail** é útil como **chave auxiliar de organização** (clientes com o mesmo
> domínio tendem a ser a mesma empresa) e como reforço na desambiguação — sem substituir CNPJ ou
> razão social.

---

## 4. Chaves de junção e a cascata de cruzamento

Cada fonte usa uma chave diferente; o cruzamento segue uma **cascata em ordem decrescente de
certeza**, e cada Cruzamento carrega uma **marca de confiança**.

```
        (1) CNPJ  ─────────────►  match EXATO       ┐
             │ (quando ambos têm e batem)           │ entram na fila
        (2) Telefone ──────────►  match PROVÁVEL    ┘ automática
             │ (normalizado, quando bate)
        (3) Razão social / Nome fantasia normalizados ──► match INCERTO
             │                                            → CONFERÊNCIA HUMANA
        (—) nada casa  ─────────►  cliente fica só na fonte de origem
```

**Junção interna do N1 (as três planilhas N1):** no N1‑NF e no N1‑Sistema o nome do cliente vem com
um **prefixo numérico** (`11111- PADARIA SILVA`, `11111 - PADARIA SILVA`). Pelo field mapping, a
**`RazaoSocial` guarda só o texto** — o número é **removido** (regra §3.1) e usado para casar. Esse
número corresponde ao `Cod` da planilha de Contatos (`11111`), então serve de **chave interna
confiável do N1** para amarrar cadastro ↔ contrato ↔ nota fiscal; a normalização deve **tolerar
variações de espaçamento** (`11111-` vs `11111 -`). Onde o número não estiver disponível, o
Cruzamento recai sobre a **razão social / nome fantasia normalizados**.

**Ordem-mestre:** o **N1‑Contatos (prioridade 1)** é a base sobre a qual as demais penduram
recência e contexto. NPS e Megazap **enriquecem**; nunca substituem a identidade do cadastro.

---

## 5. Resolução de conflito por PrioridadeFonte

Quando o **mesmo campo canônico** vem de mais de uma fonte com valores divergentes, vence a fonte
de **menor número de prioridade**:

| Campo                                            | Vence               | Por quê                         |
|--------------------------------------------------|---------------------|---------------------------------|
| CNPJ, Telefone, RazaoSocial, Email, NomeFantasia | **N1‑Contatos (1)** | É o cadastro oficial            |
| ValorTotalNF, Produto (venda)                    | **N1‑NF (2)**       | Fonte fiscal do que foi vendido |
| Produto (contratado), Início/Fim Vigência        | **N1‑Sistema (3)**  | Fonte do contrato vigente       |
| Problema relatado, chamado                       | **Megazap (4)**     | Único que traz o chamado        |
| Nota + comentário de NPS                         | **NPS (5)**         | Único que traz a avaliação      |

Ou seja: a **prioridade resolve identidade e cadastro** (N1 manda); os campos “de assunto”
(produto, valor, problema, NPS) vêm de quem é dono daquele assunto. Um telefone divergente entre
Contatos e Sistema, por exemplo, é resolvido a favor do Contatos, mas o telefone do Sistema pode
virar chave **secundária** de Cruzamento.

---

## 6. Datas, recência e o princípio “enviar ≠ conversa”

Não existe uma coluna única “última interação”. A recência é **derivada** dos sinais de cada fonte,
e contam os sinais que partem do cliente **ou que registram uma interação/conversa real**:

| Sinal de engajamento / conversa       | Fonte             | Coluna de data                     |          Conta como recência?           |
|---------------------------------------|-------------------|------------------------------------|:---------------------------------------:|
| Resposta ao NPS                       | NPS               | `Carimbo de data/hora`             |      **Sim** — o cliente respondeu      |
| Compra / saída de produto             | N1‑NF             | `Data Lançamento`                  |        **Sim** — houve transação        |
| Início/renovação de contrato          | N1‑Sistema        | `Início Vigência` / `Fim Vigência` |     **Sim** — movimento contratual      |
| **Atendimento registrado no Megazap** | Megazap           | `DATA DE CRIAÇÃO`                  | **Sim** — o registro **é uma conversa** |
| Disparo **de marketing / automático** | (fora do Megazap) | —                                  |     **Não** — enviar não é conversa     |


**Valor do contrato (regra do field mapping):** o **valor do contrato** é a **soma de todas as
notas fiscais** do cliente — o backend acumula o `Valor Total NF` de cada NF no campo
`ValorTotalNF` (regra §3.1). É esse total que entra no eixo **valor** do RFM.

**Lacuna da Fase 1 (dedução de última visita ou preventiva vencida com base na existência de registros auxiliares):**

- **Não há coluna de “visita” nem de “preventiva”.** A **última visita** e a **preventiva vencida
  (Gatilho B)** são **proxies** derivados das datas disponíveis (data da última NF / vigência), não
  de um log de visitas real. Parâmetro atual: preventiva vencida = sem sinal há **≥ 12 meses**
  (`preventiva-janela-meses`).

---

## 7. O que os dados reais mostram (evidência do cruzamento)

Rodando a cascata sobre os exports de exemplo, três padrões aparecem — e justificam a marca de
confiança:

**a) CNPJ quase não casa entre fontes na Fase 1.** O N1‑Contatos traz `CnpjCpf` como número de 13
dígitos fictício (`1111111111111`); o Megazap traz CNPJ formatado real (`29.455.188/0001‑44`), mas
do próprio grupo (MTEC); o NPS **não tem CNPJ**. Resultado: na PoC o Cruzamento **recai sobre nome
normalizado**, com o **código interno** amarrando o N1. Isso é esperado e reforça por que a
**padronização de razão social/fantasia** é o ponto mais crítico.

**b) NPS casa melhor por Nome Fantasia — e vários ficam “incertos”.** Comparando `Empresa` (NPS) com
`Nome`/`Fantasia` (N1):

| NPS `Empresa`               | N1 `Nome`                           | N1 `Fantasia`               | Cruzamento                                               |
|-----------------------------|-------------------------------------|-----------------------------|----------------------------------------------------------|
| Padaria Silva               | PADARIA SILVA                       | PADARIA SILVA               | exato (nome)                                             |
| Mecânica Costa              | COSTA MECANICA                      | **MECANICA COSTA**          | provável **via fantasia** (ordem invertida no nome)      |
| Berwanger Assessoria        | BERWANGER ASSESSORIA E CONSULTORIA… | **BERWANGER ASSESSORIA**    | exato **via fantasia**                                   |
| Martins Gastrobar           | JOÃO CARLOS FERREIRA MARTINS        | **MARTINS GASTROBAR**       | exato **via fantasia**                                   |
| Pontes Autocenter           | AUTOCENTER PONTES E SANTOS          | AUTOCENTER PONTES E SANTOS  | **incerto** (ordem/partes) → conferir                    |
| Alves Estúdio de Fotografia | LUCIANA ALVES FOTOGRAFIA…           | LUCIANA ALVES FOTOGRAFIA…   | **incerto** → conferir                                   |
| Vasconcelos Advogados       | VASCONCELOS ADVOCACIA               | VASCONCELOS ADVOCACIA       | **incerto** (“Advogados” × “Advocacia”)                  |
| **Contabilidade Paim**      | SPARRENBERGER ASSESSORIA CONTÁBIL   | SPARRENBERGER CONTABILIDADE | **sem Cruzamento seguro** → não entra na fila automática |

O caso “Contabilidade Paim” (nenhuma parte bate com “Sparrenberger”) é o exemplo de por que
**match incerto vai para conferência humana** e **nunca** para abordagem automática.

**c) Megazap muitas vezes não casa e frequentemente é chamado interno.** No exemplo, o chamado é da
própria “MTEC SOLUÇÕES”, com telefone que não bate com nenhum cliente do N1 — logo o Megazap
funciona como **enriquecimento oportunista** (quando casa por CNPJ/telefone), não como fonte de
cobertura. **Quando casa**, porém, cada atendimento **conta como conversa** e atualiza a recência
do cliente (ver §6).

---

## 8. Normalização (o pré‑processamento antes do match)

Antes de qualquer Cruzamento, cada chave passa por normalização determinística:

- **Razão social:** no N1‑NF e N1‑Sistema, **extrair só o texto, removendo o prefixo `Número-`**
  (`11111- PADARIA SILVA` → `PADARIA SILVA`); depois maiúsculas, sem acento, sem pontuação, colapso
  de espaços, remoção de sufixos societários (`LTDA`, `S.A.`, `ME`, `EIRELI`) e tratamento de
  **ordem de palavras** (“COSTA MECANICA” ≈ “MECANICA COSTA”). É a normalização que mais decide a
  qualidade da fila.
- **Código do cliente (N1):** o prefixo numérico removido da razão social (`11111`) é guardado como
  chave interna de junção do N1, tolerando hífen com/sem espaço (`11111-` vs `11111 -`).
- **E‑mail → domínio:** extrair o **domínio** (texto após `@` e antes do `.`; `teste1@teste.com.br`
  → `teste`); em **CADASTRO_ORGANIZACAO** e **PRODUTO** a célula é uma **lista separada por `;`** —
  processar cada e‑mail. Minúsculas.
- **Telefone:** só dígitos; tratar múltiplos números na mesma célula (`51999999995;51999999955`) e
  variações com/sem DDI.
- **CNPJ/CPF:** só dígitos para comparação; **mascarado por padrão** na saída (revelado só sob
  demanda). O documento `.docx` interno pode gravar o CNPJ completo.
- **Datas:** ISO. Para **NF, Megazap e NPS** (vários registros por cliente), o valor é a **data do
  último registro**; no N1‑Sistema é o `Início Vigência` do contrato.
- **NPS por posição:** ler a **nota pela Coluna D** e o **comentário pela Coluna E**, não pelo texto
  (longo) da pergunta.
- **Valor:** somar o `Valor Total NF` de **todas as NF** do cliente → `ValorTotalNF` (valor do contrato).

---

## 9. Saída do cruzamento — o modelo Cliente 360

O cruzamento produz, por cliente, o objeto consolidado que as tools do MCP entregam ao agente:

| Grupo                    | Campos consolidados                                                                                | Origem          |
|--------------------------|----------------------------------------------------------------------------------------------------|-----------------|
| **Identidade**           | razão social, nome fantasia, CNPJ (mascarado), representante, telefone, e‑mail                     | N1‑Contatos (1) |
| **Contrato/produto**     | produtos, status (ativo/encerrado), início/fim de vigência                                         | N1‑Sistema (3)  |
| **Valor**                | soma de todas as NF = valor do contrato (`ValorTotalNF`)                                           | N1‑NF (2)       |
| **Recência**             | última interação/conversa (máx. dos sinais — inclui atendimento no Megazap), última visita (proxy) | derivada (§6)   |
| **Antiguidade**          | meses de casa (1º contrato/compra)                                                                 | N1‑Sistema/NF   |
| **Avaliação**            | nota NPS, faixa, comentário, data                                                                  | NPS (5)         |
| **Atendimento/conversa** | último problema relatado; data do último atendimento (conversa)                                    | Megazap (4)     |
| **RFM**                  | recência · antiguidade · valor (soma das NF) · score                                               | núcleo          |
| **Gatilhos**             | inatividade (A) / preventiva (B) / ambos                                                           | núcleo          |
| **Confiança**            | exato / provável / incerto + flag de conferência humana + fontes casadas                           | cascata (§4)    |

Esse objeto é o que aparece em `ficha_cliente`, alimenta `clientes_inativos` / `clientes_para_contato`
e é renderizado no documento `.docx` do Preventivo e no rascunho do Pós‑NPS.

---

## 10. Parâmetros que regem o cruzamento (Fase 1)

Valores atuais no MCP (calibráveis pelo comercial — `wiki/parametros.md`):

| Parâmetro                                  | Valor atual  | Papel             |
|--------------------------------------------|:------------:|-------------------|
| Antiguidade mínima (“cliente antigo”)      | **24 meses** | Recorta Gatilho A |
| Sem engajamento (“sem contato recente”)    | **8 meses**  | Dispara Gatilho A |
| Janela de preventiva (proxy última visita) | **12 meses** | Dispara Gatilho B |
| Peso recência (RFM)                        |   **0,4**    | Prioridade        |
| Peso antiguidade (RFM)                     |   **0,3**    | Prioridade        |
| Peso valor / monetização (RFM)             |   **0,3**    | Prioridade        |

---

## 11. Riscos e pontos a validar (específicos do cruzamento)

| Risco / lacuna                                                                      | Encaminhamento                                                                                            |
|-------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| **Padronização de razão social/fantasia** é o gargalo (CNPJ não casa, NPS sem CNPJ) | Normalizar antes do match; NPS por fantasia; medir taxa de Cruzamento em amostra real                     |
| **Match incerto** abordado sem conferência                                          | Marca de confiança + fila só com exato/provável; incerto → revisão humana                                 |
| **Sem coluna de visita/preventiva**                                                 | Assumir proxy por data de NF/vigência; documentar; buscar campo real na descoberta de API                 |
| **Valor do contrato** = soma das NF (venda pontual, não mensalidade recorrente)     | Regra do field mapping: somar todas as NF em `ValorTotalNF`; validar com o comercial se reflete bem o LTV |
| **Código do cliente com formatação inconsistente**                                  | Normalizar prefixo (`11111-` / `11111 -`)                                                                 |
| **Telefone compartilhado / múltiplos por célula**                                   | Split por `;`; de‑para telefone→cliente versionada (Fase 2)                                               |
| **People CRM ausente** do field mapping atual                                       | Tratar como fonte opcional/futura; núcleo já preparado                                                    |
| **Megazap majoritariamente sem Cruzamento**                                         | Usar como enriquecimento; não contar como cobertura                                                       |

---

## 12. Como isto conversa com os outros documentos

- O **1‑design‑document** descreve *a arquitetura* que executa este cruzamento (núcleo hexagonal,
  tools, transporte). As categorias de fonte, a PrioridadeFonte e os proxies aqui documentados
  devem estar refletidos lá (§7 e §8).
- O **2‑business** descreve *por que* cruzamos (gatilhos, RFM, segmentação). A hierarquia de fontes
  e os campos por cliente daquele documento seguem o dicionário canônico da §3.
- As **skills** (`preventivo`, `pos-nps`) e a **wiki** consomem a saída da §9 sem conhecer o
  cruzamento — a normalização fica encapsulada no MCP.
