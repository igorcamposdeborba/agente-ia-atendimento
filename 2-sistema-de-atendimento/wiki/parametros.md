# Parâmetros

Valores que regem as regras dos agentes. **São decisões de negócio** (comercial/técnico) —
os números abaixo são sugestões iniciais a **calibrar**.

## Limiares de inatividade (agente de Preventivo)

| Parâmetro                               | Sugestão inicial | Decide    |
|-----------------------------------------|------------------|-----------|
| Antiguidade mínima ("cliente antigo")   | ≥ 2 anos de casa | Comercial |
| Sem engajamento ("sem contato recente") | ≥ 8–12 meses     | Comercial |

**Recência conta só engajamento do cliente:** resposta de NPS, visita registrada no N1,
atualização cadastral ou de contrato no N1, mensagem **recebida** no Megazap. **Disparo enviado
nunca conta.**

## Faixas de NPS

| Faixa | Classificação |
|-------|---------------|
| 9–10  | Promotor      |
| 7–8   | Passivo       |
| 0–6   | Detrator      |

## Tempos-padrão (agente Preventivo / estimativas)

⚠️ **Valores ilustrativos — calibrar com o histórico real da Millenium.**

| Serviço            | Horas/unid. | Setup (h) | Fator interior (>100 km) | Janela preventiva |
|--------------------|-------------|-----------|--------------------------|-------------------|
| Catraca de acesso  | ⟨X⟩         | ⟨X⟩       | ⟨X⟩                      | ⟨X⟩ meses         |
| CFTV (câmera)      | ⟨X⟩         | ⟨X⟩       | ⟨X⟩                      | ⟨X⟩ meses         |
| Controle de acesso | ⟨X⟩         | ⟨X⟩       | ⟨X⟩                      | ⟨X⟩ meses         |
| Cancela veicular   | ⟨X⟩         | ⟨X⟩       | ⟨X⟩                      | ⟨X⟩ meses         |
| Controle de ponto  | ⟨X⟩         | ⟨X⟩       | ⟨X⟩                      | ⟨X⟩ meses         |

> Estes números alimentam a tool `estimar_servico`; sem calibração real, as estimativas são só
> aproximações.
