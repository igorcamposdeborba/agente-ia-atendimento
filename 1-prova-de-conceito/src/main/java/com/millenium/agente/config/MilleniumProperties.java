package com.millenium.agente.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de negocio e localizacao dos dados. Editaveis em application.properties ou por
 * variavel de ambiente (ex.: MILLENIUM_DATADIR, MILLENIUM_ANTIGUIDADELIMIARMESES).
 * Os limiares comerciais ("cliente antigo", "sem contato recente") ainda serao confirmados
 * com o time (ver secao 12 do design).
 */
@ConfigurationProperties(prefix = "millenium")
public class MilleniumProperties {

    /**
     * Pasta onde ficam os .xlsx (N1, Megazap, People, NPS) que o MCP LE. Padrao neutro e previsivel,
     * independente do caminho do projeto. Sobrescrita por --millenium.fonte-dir=... (config do
     * Claude / .mcpb) ou por millenium.fonte-dir no application.properties.
     */
    private String fonteDir = System.getProperty("user.home") + "/MilleniumAgenteIA/fonte";

    /**
     * Pasta da wiki (Markdown editavel) que o MCP LE. Se existir e tiver .md, tem prioridade; caso
     * contrario o MCP usa a wiki empacotada no jar (classpath:wiki/). Assim a wiki e editavel sem rebuild.
     */
    private String wikiDir = System.getProperty("user.home") + "/MilleniumAgenteIA/wiki";

    /** Pasta onde o MCP salva os documentos .docx gerados (planos, respostas). Deve ser gravavel. */
    private String outputDir = System.getProperty("user.home") + "/MilleniumAgenteIA/saidas";

    /** A partir de quantos meses de casa o cliente e considerado "antigo". */
    private int antiguidadeLimiarMeses = 24;

    /** A partir de quantos meses sem engajamento o cliente e considerado "sem contato recente" (parametros.md: 8-12). */
    private int inatividadeLimiarMeses = 8;

    /**
     * Gatilho B (preventiva vencida). Nao ha coluna de "proxima preventiva" nos dados reais, entao
     * usamos a "ultima visita" como PROXY: preventiva vencida = sem visita ha >= este numero de meses
     * (ou nunca). A calibrar por produto (ver wiki/parametros.md, coluna "Janela preventiva").
     */
    private int preventivaJanelaMeses = 12;

    private double pesoRecencia = 0.4;
    private double pesoAntiguidade = 0.3;
    private double pesoMonetizacao = 0.3;

    public String getFonteDir() {
        return fonteDir;
    }

    public void setFonteDir(String fonteDir) {
        this.fonteDir = fonteDir;
    }

    public String getWikiDir() {
        return wikiDir;
    }

    public void setWikiDir(String wikiDir) {
        this.wikiDir = wikiDir;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public int getAntiguidadeLimiarMeses() {
        return antiguidadeLimiarMeses;
    }

    public void setAntiguidadeLimiarMeses(int antiguidadeLimiarMeses) {
        this.antiguidadeLimiarMeses = antiguidadeLimiarMeses;
    }

    public int getInatividadeLimiarMeses() {
        return inatividadeLimiarMeses;
    }

    public void setInatividadeLimiarMeses(int inatividadeLimiarMeses) {
        this.inatividadeLimiarMeses = inatividadeLimiarMeses;
    }

    public int getPreventivaJanelaMeses() {
        return preventivaJanelaMeses;
    }

    public void setPreventivaJanelaMeses(int preventivaJanelaMeses) {
        this.preventivaJanelaMeses = preventivaJanelaMeses;
    }

    public double getPesoRecencia() {
        return pesoRecencia;
    }

    public void setPesoRecencia(double pesoRecencia) {
        this.pesoRecencia = pesoRecencia;
    }

    public double getPesoAntiguidade() {
        return pesoAntiguidade;
    }

    public void setPesoAntiguidade(double pesoAntiguidade) {
        this.pesoAntiguidade = pesoAntiguidade;
    }

    public double getPesoMonetizacao() {
        return pesoMonetizacao;
    }

    public void setPesoMonetizacao(double pesoMonetizacao) {
        this.pesoMonetizacao = pesoMonetizacao;
    }
}
