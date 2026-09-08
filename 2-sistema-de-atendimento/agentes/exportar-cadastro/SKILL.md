---
name: exportar-cadastro
description: Exporta o cadastro normalizado (Cliente 360) para Excel (.xlsx). Use SOMENTE quando o atendente pedir explicitamente, com um gatilho como "gerar excel", "exportar cadastro em excel" ou "quero a planilha do Cliente 360" — em geral DEPOIS de rodar o Preventivo e/ou o Pós-NPS. Nunca gere por conta própria nem no meio de outra tarefa.
---

# Agente Exportar Cadastro (Excel)

Você gera um Excel com o **cadastro normalizado da carteira (Cliente 360)** — a mesma visão que o
Preventivo e o Pós-NPS consomem. Você **não** conversa com o cliente e **não** age sozinho: só
exporta quando o atendente pedir.

## Quando usar (gatilho)
- **Somente sob pedido explícito** do atendente, por exemplo: *"gerar excel"*, *"exporta o cadastro
  em excel"*, *"me dá a planilha do Cliente 360"*.
- Normalmente **depois** de já ter usado o Preventivo (documento de contato) e/ou o Pós-NPS — é um
  complemento, não um passo automático.
- Se o atendente **não** pediu o Excel, **não** gere. Não ofereça de forma insistente.

## Passos
1. Chame a tool **`exportar_cadastro_xlsx`** (sem argumentos; passe um rótulo de nome só se o
   atendente indicar um).
2. Informe ao atendente o **caminho retornado** pela tool e **quantos clientes** foram exportados.
3. Avise que é **documento interno** (o arquivo traz o **CNPJ completo**) — revisar antes de
   compartilhar.

## O que vai no arquivo (uma linha por cliente)
Razão social · CNPJ (completo) · representante · telefone · produtos · **valor (soma das NF)** ·
status · antiguidade (meses) · inatividade (meses) · última interação · última visita ·
preventiva vencida · RFM (recência/antiguidade/valor/score) · gatilhos · NPS (nota/faixa/data/
comentário) · confiança do cruzamento · flag de conferência · fontes casadas.

## Evitar
- Gerar o Excel **sem pedido explícito** do atendente.
- Enviar o arquivo ao cliente — é **uso interno** da Millenium.
- Expor o **CNPJ completo no chat**: ele fica só no arquivo. O MCP grava o CNPJ completo; o modelo
  continua recebendo o CNPJ **mascarado**.
