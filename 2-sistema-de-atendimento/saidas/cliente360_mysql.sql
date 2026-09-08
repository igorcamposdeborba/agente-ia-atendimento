-- Cliente 360 normalizado (Fase 1 PoC) — gerado das 5 planilhas do field mapping
-- Regras: valor=soma das NF; email=domínio; razão social sem número; datas=último registro; NPS col D/E.
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS millenium CHARACTER SET utf8mb4;
USE millenium;

DROP TABLE IF EXISTS cliente360;
CREATE TABLE cliente360 (
  codigo_cliente VARCHAR(20) PRIMARY KEY,
  RazaoSocial VARCHAR(255),
  NomeFantasia VARCHAR(255),
  CNPJ VARCHAR(20),
  CNPJ_mascarado VARCHAR(30),
  Telefone VARCHAR(120),
  EmailDominio VARCHAR(255),
  Produtos TEXT,
  InicioVigencia DATE,
  FimUltimaVigencia DATE,
  ValorTotalNF DECIMAL(14,2),
  NF_Produtos TEXT,
  DataUltimaNF DATE,
  NPS_Nota INT,
  NPS_Faixa VARCHAR(12),
  NPS_Comentario TEXT,
  NPS_Data DATE,
  NPS_Match VARCHAR(12),
  Megazap_Problema TEXT,
  Megazap_Data DATE,
  UltimaInteracao DATE,
  AntiguidadeMeses INT,
  InatividadeMeses INT,
  UltimaVisita DATE,
  PreventivaVencida VARCHAR(3),
  RFM_Recencia DECIMAL(4,2),
  RFM_Antiguidade DECIMAL(4,2),
  RFM_Valor DECIMAL(4,2),
  RFM_Score DECIMAL(5,2),
  Gatilhos VARCHAR(40),
  Confianca VARCHAR(40),
  PrecisaConferencia VARCHAR(3),
  FontesCasadas VARCHAR(120)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO cliente360 (codigo_cliente, RazaoSocial, NomeFantasia, CNPJ, CNPJ_mascarado, Telefone, EmailDominio, Produtos, InicioVigencia, FimUltimaVigencia, ValorTotalNF, NF_Produtos, DataUltimaNF, NPS_Nota, NPS_Faixa, NPS_Comentario, NPS_Data, NPS_Match, Megazap_Problema, Megazap_Data, UltimaInteracao, AntiguidadeMeses, InatividadeMeses, UltimaVisita, PreventivaVencida, RFM_Recencia, RFM_Antiguidade, RFM_Valor, RFM_Score, Gatilhos, Confianca, PrecisaConferencia, FontesCasadas) VALUES ('11120', 'JOÃO CARLOS FERREIRA MARTINS', 'MARTINS GASTROBAR', '1111111111110', '11.***.***/****-10', '51999999990', 'teste', 'PONTO WEB PLANO ULTIMATE', '2025-01-05', NULL, 5000.0, 'CATRACA XYZ', '2026-09-02', 9, 'promotor', 'Comercial demora muito para responder...', '2026-08-28', 'exato', NULL, NULL, '2026-09-02', 20, 0, '2026-09-02', 'NAO', 5.0, 1.67, 5.0, 4.0, '-', 'exato', 'NAO', 'CADASTRO;PRODUTO;NOTA_FISCAL;NPS');
INSERT INTO cliente360 (codigo_cliente, RazaoSocial, NomeFantasia, CNPJ, CNPJ_mascarado, Telefone, EmailDominio, Produtos, InicioVigencia, FimUltimaVigencia, ValorTotalNF, NF_Produtos, DataUltimaNF, NPS_Nota, NPS_Faixa, NPS_Comentario, NPS_Data, NPS_Match, Megazap_Problema, Megazap_Data, UltimaInteracao, AntiguidadeMeses, InatividadeMeses, UltimaVisita, PreventivaVencida, RFM_Recencia, RFM_Antiguidade, RFM_Valor, RFM_Score, Gatilhos, Confianca, PrecisaConferencia, FontesCasadas) VALUES ('11114', 'AUTOCENTER PONTES E SANTOS', 'AUTOCENTER PONTES E SANTOS', '1111111111114', '11.***.***/****-14', '51999999994', 'teste', 'PONTO WEB PLANO BASIC', '2023-02-01', NULL, 0.0, NULL, NULL, 10, 'promotor', 'Tudo excelente!', '2026-08-28', 'exato', NULL, NULL, '2026-08-28', 43, 0, '2023-02-01', 'SIM', 5.0, 3.58, 0.0, 3.08, 'preventiva', 'exato', 'NAO', 'CADASTRO;PRODUTO;NPS');
INSERT INTO cliente360 (codigo_cliente, RazaoSocial, NomeFantasia, CNPJ, CNPJ_mascarado, Telefone, EmailDominio, Produtos, InicioVigencia, FimUltimaVigencia, ValorTotalNF, NF_Produtos, DataUltimaNF, NPS_Nota, NPS_Faixa, NPS_Comentario, NPS_Data, NPS_Match, Megazap_Problema, Megazap_Data, UltimaInteracao, AntiguidadeMeses, InatividadeMeses, UltimaVisita, PreventivaVencida, RFM_Recencia, RFM_Antiguidade, RFM_Valor, RFM_Score, Gatilhos, Confianca, PrecisaConferencia, FontesCasadas) VALUES ('11113', 'SOUZA CONFECÇÕES', 'SOUZA CONFECÇÕES', '1111111111113', '11.***.***/****-13', '51999999993', 'teste', 'PONTO WEB PLANO ULTIMATE', '2024-09-02', NULL, 500.0, 'CRACHÁ XYZ', '2026-09-02', 8, 'passivo', 'Atendimento bom, mas valores muito altos.', '2026-08-28', 'exato', NULL, NULL, '2026-09-02', 24, 0, '2026-09-02', 'NAO', 5.0, 2.0, 0.5, 2.75, '-', 'exato', 'NAO', 'CADASTRO;PRODUTO;NOTA_FISCAL;NPS');
INSERT INTO cliente360 (codigo_cliente, RazaoSocial, NomeFantasia, CNPJ, CNPJ_mascarado, Telefone, EmailDominio, Produtos, InicioVigencia, FimUltimaVigencia, ValorTotalNF, NF_Produtos, DataUltimaNF, NPS_Nota, NPS_Faixa, NPS_Comentario, NPS_Data, NPS_Match, Megazap_Problema, Megazap_Data, UltimaInteracao, AntiguidadeMeses, InatividadeMeses, UltimaVisita, PreventivaVencida, RFM_Recencia, RFM_Antiguidade, RFM_Valor, RFM_Score, Gatilhos, Confianca, PrecisaConferencia, FontesCasadas) VALUES ('11118', 'VASCONCELOS ADVOCACIA', 'VASCONCELOS ADVOCACIA', '1111111111118', '11.***.***/****-18', '51999999998', 'teste', 'PONTO WEB PLANO ULTIMATE', '2024-10-06', NULL, 0.0, NULL, NULL, 9, 'promotor', 'Sistema muito burocrático...', '2026-08-28', 'incerto', NULL, NULL, '2026-08-28', 23, 0, '2024-10-06', 'SIM', 5.0, 1.92, 0.0, 2.58, 'preventiva', 'exato', 'SIM', 'CADASTRO;PRODUTO;NPS');
INSERT INTO cliente360 (codigo_cliente, RazaoSocial, NomeFantasia, CNPJ, CNPJ_mascarado, Telefone, EmailDominio, Produtos, InicioVigencia, FimUltimaVigencia, ValorTotalNF, NF_Produtos, DataUltimaNF, NPS_Nota, NPS_Faixa, NPS_Comentario, NPS_Data, NPS_Match, Megazap_Problema, Megazap_Data, UltimaInteracao, AntiguidadeMeses, InatividadeMeses, UltimaVisita, PreventivaVencida, RFM_Recencia, RFM_Antiguidade, RFM_Valor, RFM_Score, Gatilhos, Confianca, PrecisaConferencia, FontesCasadas) VALUES ('11111', 'PADARIA SILVA', 'PADARIA SILVA', '1111111111111', '11.***.***/****-11', '51999999991', 'teste', 'PONTO WEB PLANO BASIC', '2026-04-01', NULL, 1000.0, 'RELÓGIO PONTO XYZ', '2026-09-01', 10, 'promotor', 'Ótimo atendimento de Suporte', '2026-08-28', 'exato', NULL, NULL, '2026-09-01', 5, 0, '2026-09-01', 'NAO', 5.0, 0.42, 1.0, 2.42, '-', 'exato', 'NAO', 'CADASTRO;PRODUTO;NOTA_FISCAL;NPS');
INSERT INTO cliente360 (codigo_cliente, RazaoSocial, NomeFantasia, CNPJ, CNPJ_mascarado, Telefone, EmailDominio, Produtos, InicioVigencia, FimUltimaVigencia, ValorTotalNF, NF_Produtos, DataUltimaNF, NPS_Nota, NPS_Faixa, NPS_Comentario, NPS_Data, NPS_Match, Megazap_Problema, Megazap_Data, UltimaInteracao, AntiguidadeMeses, InatividadeMeses, UltimaVisita, PreventivaVencida, RFM_Recencia, RFM_Antiguidade, RFM_Valor, RFM_Score, Gatilhos, Confianca, PrecisaConferencia, FontesCasadas) VALUES ('11112', 'COSTA MECANICA', 'MECANICA COSTA', '1111111111112', '11.***.***/****-12', '51999999992', 'teste', 'PONTO WEB PLANO PRO', '2025-05-10', NULL, 0.0, NULL, NULL, 5, 'detrator', 'Demora para atendimento no Suporte.', '2026-08-28', 'exato', NULL, NULL, '2026-08-28', 15, 0, '2025-05-10', 'SIM', 5.0, 1.25, 0.0, 2.38, 'preventiva', 'exato', 'NAO', 'CADASTRO;PRODUTO;NPS');
INSERT INTO cliente360 (codigo_cliente, RazaoSocial, NomeFantasia, CNPJ, CNPJ_mascarado, Telefone, EmailDominio, Produtos, InicioVigencia, FimUltimaVigencia, ValorTotalNF, NF_Produtos, DataUltimaNF, NPS_Nota, NPS_Faixa, NPS_Comentario, NPS_Data, NPS_Match, Megazap_Problema, Megazap_Data, UltimaInteracao, AntiguidadeMeses, InatividadeMeses, UltimaVisita, PreventivaVencida, RFM_Recencia, RFM_Antiguidade, RFM_Valor, RFM_Score, Gatilhos, Confianca, PrecisaConferencia, FontesCasadas) VALUES ('11117', 'LUCIANA ALVES FOTOGRAFIA E PRODUÇÃO AUDIOVISUAL', 'LUCIANA ALVES FOTOGRAFIA E PRODUÇÃO AUDIOVISUAL', '1111111111117', '11.***.***/****-17', '51999999997;51999999977', 'teste', 'PONTO WEB PLANO BASIC', '2025-08-03', NULL, 0.0, NULL, NULL, 2, 'detrator', 'Péssima qualidade nos crachás e qualquer suporte gera cobrança!', '2026-08-28', 'incerto', NULL, NULL, '2026-08-28', 13, 0, '2025-08-03', 'SIM', 5.0, 1.08, 0.0, 2.33, 'preventiva', 'exato', 'SIM', 'CADASTRO;PRODUTO;NPS');
INSERT INTO cliente360 (codigo_cliente, RazaoSocial, NomeFantasia, CNPJ, CNPJ_mascarado, Telefone, EmailDominio, Produtos, InicioVigencia, FimUltimaVigencia, ValorTotalNF, NF_Produtos, DataUltimaNF, NPS_Nota, NPS_Faixa, NPS_Comentario, NPS_Data, NPS_Match, Megazap_Problema, Megazap_Data, UltimaInteracao, AntiguidadeMeses, InatividadeMeses, UltimaVisita, PreventivaVencida, RFM_Recencia, RFM_Antiguidade, RFM_Valor, RFM_Score, Gatilhos, Confianca, PrecisaConferencia, FontesCasadas) VALUES ('11115', 'RESTAURANTE CAMARGO', 'RESTAURANTE CAMARGO', '1111111111115', '11.***.***/****-15', '51999999995;51999999955', 'teste', 'PONTO OFFLINE', '2026-01-01', NULL, 0.0, NULL, NULL, 9, 'promotor', 'Atendimento bom, mas meu equipamento estraga com frequência.', '2026-08-28', 'exato', NULL, NULL, '2026-08-28', 8, 0, '2026-01-01', 'NAO', 5.0, 0.67, 0.0, 2.2, '-', 'exato', 'NAO', 'CADASTRO;PRODUTO;NPS');
INSERT INTO cliente360 (codigo_cliente, RazaoSocial, NomeFantasia, CNPJ, CNPJ_mascarado, Telefone, EmailDominio, Produtos, InicioVigencia, FimUltimaVigencia, ValorTotalNF, NF_Produtos, DataUltimaNF, NPS_Nota, NPS_Faixa, NPS_Comentario, NPS_Data, NPS_Match, Megazap_Problema, Megazap_Data, UltimaInteracao, AntiguidadeMeses, InatividadeMeses, UltimaVisita, PreventivaVencida, RFM_Recencia, RFM_Antiguidade, RFM_Valor, RFM_Score, Gatilhos, Confianca, PrecisaConferencia, FontesCasadas) VALUES ('11116', 'BERWANGER ASSESSORIA E CONSULTORIA EMPRESARIAL', 'BERWANGER ASSESSORIA', '1111111111116', '11.***.***/****-16', '51999999996', 'teste', 'PONTO 4', '2026-06-10', NULL, 0.0, NULL, NULL, 10, 'promotor', 'Técnicos prestativos e atenciosos', '2026-08-28', 'exato', NULL, NULL, '2026-08-28', 2, 0, '2026-06-10', 'NAO', 5.0, 0.17, 0.0, 2.05, '-', 'exato', 'NAO', 'CADASTRO;PRODUTO;NPS');
INSERT INTO cliente360 (codigo_cliente, RazaoSocial, NomeFantasia, CNPJ, CNPJ_mascarado, Telefone, EmailDominio, Produtos, InicioVigencia, FimUltimaVigencia, ValorTotalNF, NF_Produtos, DataUltimaNF, NPS_Nota, NPS_Faixa, NPS_Comentario, NPS_Data, NPS_Match, Megazap_Problema, Megazap_Data, UltimaInteracao, AntiguidadeMeses, InatividadeMeses, UltimaVisita, PreventivaVencida, RFM_Recencia, RFM_Antiguidade, RFM_Valor, RFM_Score, Gatilhos, Confianca, PrecisaConferencia, FontesCasadas) VALUES ('11119', 'SPARRENBERGER ASSESSORIA CONTÁBIL', 'SPARRENBERGER CONTABILIDADE', '1111111111119', '11.***.***/****-19', '51999999999', 'teste', 'PONTO WEB PLANO PRO', '2023-03-04', NULL, 0.0, NULL, NULL, NULL, NULL, NULL, NULL, 'sem', NULL, NULL, '2023-03-04', 42, 42, '2023-03-04', 'SIM', 1.5, 3.5, 0.0, 1.65, 'inatividade+preventiva', 'exato', 'NAO', 'CADASTRO;PRODUTO');
