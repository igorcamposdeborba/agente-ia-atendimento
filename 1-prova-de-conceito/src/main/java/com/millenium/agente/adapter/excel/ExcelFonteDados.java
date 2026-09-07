package com.millenium.agente.adapter.excel;

import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.model.RegistroMegazap;
import com.millenium.agente.core.model.RegistroN1;
import com.millenium.agente.core.model.RegistroNps;
import com.millenium.agente.core.model.RegistroPeople;
import com.millenium.agente.core.port.FonteDadosPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.millenium.agente.adapter.excel.PlanilhaLeitor.data;
import static com.millenium.agente.adapter.excel.PlanilhaLeitor.decimal;
import static com.millenium.agente.adapter.excel.PlanilhaLeitor.inteiro;
import static com.millenium.agente.adapter.excel.PlanilhaLeitor.texto;

/**
 * Adaptador de saida: le os quatro exports .xlsx (N1, Megazap, People, NPS) da pasta configurada em
 * {@code millenium.fonte-dir}. Os arquivos sao localizados por prefixo do nome (n1*, megazap*,
 * people*, nps*), entao variacoes como "megazap_mensagens_1.xlsx" tambem funcionam.
 * <p>
 * O MCP apenas <b>le</b> os Excel - nao gera nada.
 */
@Component
public class ExcelFonteDados implements FonteDadosPort {

    private static final Logger log = LoggerFactory.getLogger(ExcelFonteDados.class);

    private final MilleniumProperties props;
    private final PlanilhaLeitor leitor = new PlanilhaLeitor();

    public ExcelFonteDados(MilleniumProperties props) {
        this.props = props;
    }

    private Path arquivoPorPrefixo(String prefixo) {
        Path dir = Path.of(props.getFonteDir());
        if (!Files.isDirectory(dir)) {
            log.warn("Pasta de dados nao encontrada: {}", dir.toAbsolutePath());
            return null;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".xlsx"))
                    .filter(p -> PlanilhaLeitor.norm(p.getFileName().toString()).startsWith(prefixo))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Falha ao listar {}: {}", dir, e.getMessage());
            return null;
        }
    }

    @Override
    public List<RegistroN1> lerN1() {
        Path arq = arquivoPorPrefixo("n1");
        List<Map<String, String>> linhas = leitor.ler(arq);
        log.info("N1: {} linha(s) de {}", linhas.size(), arq);
        return linhas.stream().map(l -> new RegistroN1(
                texto(l, "cnpj"),
                texto(l, "razao social"),
                texto(l, "produto"),
                inteiro(l, "qtd"),
                decimal(l, "valor mensal"),
                data(l, "primeiro contrato"),
                texto(l, "status contrato"),
                data(l, "ultima visita"),
                data(l, "ult atualiz cadastral"),
                data(l, "ult atualiz contrato"),
                texto(l, "representante"),
                texto(l, "telefone")
        )).toList();
    }

    @Override
    public List<RegistroMegazap> lerMegazap() {
        Path arq = arquivoPorPrefixo("megazap");
        return leitor.ler(arq).stream().map(l -> new RegistroMegazap(
                texto(l, "cnpj"),
                texto(l, "razao social"),
                data(l, "data"),
                texto(l, "direcao")
        )).toList();
    }

    @Override
    public List<RegistroPeople> lerPeople() {
        Path arq = arquivoPorPrefixo("people");
        return leitor.ler(arq).stream().map(l -> new RegistroPeople(
                texto(l, "cnpj"),
                texto(l, "razao social"),
                texto(l, "etapa funil"),
                data(l, "ultima interacao")
        )).toList();
    }

    @Override
    public List<RegistroNps> lerNps() {
        Path arq = arquivoPorPrefixo("nps");
        return leitor.ler(arq).stream().map(l -> new RegistroNps(
                texto(l, "cnpj"),
                texto(l, "razao social"),
                inteiro(l, "nota"),
                texto(l, "comentario"),
                data(l, "data")
        )).toList();
    }

    @Override
    public void recarregar() {
        log.info("Recarregando fontes Excel da pasta {}", props.getFonteDir());
    }
}
