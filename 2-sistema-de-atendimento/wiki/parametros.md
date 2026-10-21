# Parâmetros — FONTE ÚNICA dos limiares

> ✅ **Edite os valores SOMENTE neste arquivo**, no bloco `properties` abaixo. Ele é a origem
> única dos limiares do Preventivo e dos pesos do RFM. O `application.properties` é **gerado** a
> partir daqui pelo `sync-parametros` (rodado pelo `install.ps1` antes do `mvn package`). Não
> edite esses números no `application.properties` — eles são sobrescritos pelo sync.

## Limiares e pesos (bloco lido pelo sync)

```properties
# Antiguidade minima para "cliente antigo" (meses de casa).
millenium.antiguidade-limiar-meses=13
# "Sem contato recente" (gatilho A): meses sem engajamento do cliente.
millenium.inatividade-limiar-meses=4
# "Preventiva vencida" (gatilho B): meses sem visita (proxy; nao ha coluna de preventiva no N1).
millenium.preventiva-janela-meses=4
# Pesos da priorizacao RFM.
millenium.peso-recencia=0.4
millenium.peso-antiguidade=0.2
millenium.peso-monetizacao=0.4
```

## O que cada um significa

- **Antiguidade** (`antiguidade-limiar-meses`): a partir de quantos meses de casa o cliente conta como "antigo".
- **Inatividade / gatilho A** (`inatividade-limiar-meses`): meses sem **engajamento do cliente** — resposta de NPS, movimento no N1 (venda/contrato/visita/OS) ou mensagem **recebida** no Megazap. **Disparo enviado nunca conta.**
- **Preventiva vencida / gatilho B** (`preventiva-janela-meses`): meses sem visita (proxy). **Independe da recência.**
- **Pesos RFM** (`peso-*`): ponderação da priorização (recência, antiguidade, monetização/valor).

> Para mudar um limiar: edite o bloco acima → rode o **sync** (ou o `install.ps1`, que já chama o sync) → `mvn package` → **reinicie o Claude**. O backend lê do `application.properties` gerado.

## Faixas de NPS (referência)

| Faixa | Classificação |
|-------|---------------|
| 9–10  | Promotor      |
| 7–8   | Passivo       |
| 0–6   | Detrator      |
