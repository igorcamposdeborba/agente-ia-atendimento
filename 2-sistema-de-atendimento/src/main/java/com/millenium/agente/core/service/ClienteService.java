package com.millenium.agente.core.service;

import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.linkage.EntityResolver;
import com.millenium.agente.core.dto.Cliente360;
import com.millenium.agente.core.dto.ClienteInativo;
import com.millenium.agente.core.dto.MatchConfianca;
import com.millenium.agente.core.dto.Rfm;
import com.millenium.agente.core.dto.SinalEngajamento;
import com.millenium.agente.core.normalizacao.Normalizador;
import com.millenium.agente.core.port.FonteDadosPort;
import com.millenium.agente.core.recencia.CalculadoraRecencia;
import com.millenium.agente.core.rfm.CalculadoraRfm;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Nucleo de aplicacao: monta o Cliente 360 a partir das fontes, aplica recencia + RFM + os dois
 * gatilhos do Preventivo, e serve as consultas das tools do MCP.
 * <p>
 * Gatilho A (inatividade): cliente antigo E sem engajamento recente.
 * Gatilho B (preventiva vencida): sem visita ha >= janela (proxy, pois nao ha coluna de preventiva),
 * independente da recencia.
 */
@Service
public class ClienteService {

    private final FonteDadosPort fonte;
    private final MilleniumProperties props;
    private final EntityResolver resolver = new EntityResolver();
    private final CalculadoraRfm rfm;

    private volatile List<Cliente360> cache;

    public ClienteService(FonteDadosPort fonte, MilleniumProperties props) {
        this.fonte = fonte;
        this.props = props;
        this.rfm = new CalculadoraRfm(props.getPesoRecencia(), props.getPesoAntiguidade(), props.getPesoMonetizacao());
    }

    private List<Cliente360> clientes() {
        List<Cliente360> local = cache;
        if (local == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = resolver.cruzar(fonte.lerN1(), fonte.lerMegazap(), fonte.lerPeople(), fonte.lerNps());
                }
                local = cache;
            }
        }
        return local;
    }

    public synchronized void recarregar() {
        fonte.recarregar();
        cache = null;
        clientes();
    }

    public int totalClientes() {
        return clientes().size();
    }

    /** Visao consolidada (Cliente 360) de todos os clientes — usada pela exportacao em .xlsx. */
    public List<Cliente360> clientesConsolidados() {
        return clientes();
    }

    /**
     * Substitui os CNPJs mascarados (ex.: 12.***.***-90) pelos CNPJs completos e formatados.
     * Usado ao gerar o .docx (documento interno): o mascaramento vale so no canal MCP<->IA;
     * no arquivo de planejamento o CNPJ aparece completo. O CNPJ completo nunca vai ao modelo.
     */
    public String revelarCnpjs(String texto) {
        if (texto == null) return null;
        String r = texto;
        for (Cliente360 c : clientes()) {
            if (c.cnpj() != null) {
                r = r.replace(c.cnpjMascarado(), formatarCnpj(c.cnpj()));
            }
        }
        return r;
    }

    /** 14 digitos -> 12.345.678/0001-90; caso contrario devolve como esta. */
    public static String formatarCnpj(String digitos) {
        if (digitos == null) return null;
        String d = digitos.replaceAll("\\D", "");
        if (d.length() != 14) return digitos;
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8)
                + "/" + d.substring(8, 12) + "-" + d.substring(12);
    }

    /** Busca por CNPJ (so digitos) ou razao social normalizada (contains). */
    public Optional<Cliente360> buscar(String termo) {
        if (termo == null || termo.isBlank()) return Optional.empty();
        String cnpj = Normalizador.cnpj(termo);
        String razao = Normalizador.razaoSocial(termo);
        List<Cliente360> lista = clientes();
        if (cnpj != null && cnpj.length() >= 8) {
            Optional<Cliente360> porCnpj = lista.stream().filter(c -> cnpj.equals(c.cnpj())).findFirst();
            if (porCnpj.isPresent()) return porCnpj;
        }
        if (razao != null) {
            return lista.stream()
                    .filter(c -> {
                        String r = Normalizador.razaoSocial(c.razaoSocial());
                        return r != null && r.contains(razao);
                    })
                    .findFirst();
        }
        return Optional.empty();
    }

    public List<SinalEngajamento> historico(Cliente360 c) {
        List<SinalEngajamento> ordenado = new ArrayList<>(c.sinais());
        ordenado.sort(Comparator.comparing(SinalEngajamento::data).reversed());
        return ordenado;
    }

    /** Avalia um cliente: inatividade, antiguidade, gatilhos A/B e RFM. */
    public ClienteInativo avaliar(Cliente360 c) {
        LocalDate hoje = LocalDate.now();
        Optional<LocalDate> ultima = CalculadoraRecencia.ultimaInteracao(c.sinais());
        Long inatividade = CalculadoraRecencia.inatividadeMeses(c.sinais(), hoje).orElse(null);
        long antiguidade = c.primeiroContrato() == null ? 0
                : ChronoUnit.MONTHS.between(c.primeiroContrato(), hoje);

        boolean antigo = antiguidade >= props.getAntiguidadeLimiarMeses();
        boolean semContatoRecente = inatividade == null || inatividade >= props.getInatividadeLimiarMeses();
        boolean gatilhoA = antigo && semContatoRecente;

        boolean preventivaVencida = c.ultimaVisita() == null
                || ChronoUnit.MONTHS.between(c.ultimaVisita(), hoje) >= props.getPreventivaJanelaMeses();

        List<String> gatilhos = new ArrayList<>();
        if (gatilhoA) gatilhos.add("cliente antigo sem contato recente (inatividade)");
        if (preventivaVencida) {
            gatilhos.add("preventiva vencida [proxy: última visita "
                    + (c.ultimaVisita() == null ? "nunca registrada" : c.ultimaVisita()) + "]");
        }

        Rfm score = rfm.calcular(inatividade, antiguidade, c.valorMensalTotal());

        return new ClienteInativo(
                c.cnpjMascarado(),
                c.razaoSocial(),
                c.representante(),
                c.telefone(),
                c.valorMensalTotal(),
                c.produtos(),
                ultima.orElse(null),
                inatividade,
                antiguidade,
                gatilhoA,
                preventivaVencida,
                score,
                c.confianca(),
                gatilhos,
                montarEvidencias(c),
                c.confianca() == MatchConfianca.INCERTO
        );
    }

    /** Gatilho A: clientes antigos sem contato recente, priorizados por RFM. */
    public List<ClienteInativo> clientesInativos() {
        return ordenar(avaliarTodos().stream().filter(ClienteInativo::inatividadeGatilho).toList());
    }

    /**
     * Fila de contato do Preventivo. Se {@code preventivaVencida} = true, so o gatilho B; caso
     * contrario, a UNIAO A ou B (deduplicada por cliente), priorizada por RFM.
     */
    public List<ClienteInativo> clientesParaContato(Boolean preventivaVencida) {
        List<ClienteInativo> todos = avaliarTodos();
        List<ClienteInativo> filtrada = Boolean.TRUE.equals(preventivaVencida)
                ? todos.stream().filter(ClienteInativo::preventivaVencida).toList()
                : todos.stream().filter(ci -> ci.inatividadeGatilho() || ci.preventivaVencida()).toList();
        return ordenar(filtrada);
    }

    private List<ClienteInativo> avaliarTodos() {
        return clientes().stream().map(this::avaliar).toList();
    }

    private List<ClienteInativo> ordenar(List<ClienteInativo> lista) {
        return lista.stream()
                .sorted(Comparator
                        .comparingDouble((ClienteInativo ci) -> ci.rfm().score()).reversed()
                        .thenComparing(ci -> ci.valorMensalTotal() == null ? 0.0 : ci.valorMensalTotal().doubleValue(),
                                Comparator.reverseOrder()))
                .toList();
    }

    private List<String> montarEvidencias(Cliente360 c) {
        List<String> ev = new ArrayList<>();
        historico(c).forEach(s -> ev.add(s.data() + " - " + s.descricao() + " [" + s.fonte() + "]"));
        if (c.sinais().isEmpty()) {
            ev.add("nenhum sinal de engajamento do cliente registrado nas fontes");
        }
        if (!c.produtos().isEmpty()) ev.add("produtos: " + String.join(", ", c.produtos()));
        if (c.npsNota() != null) {
            ev.add("NPS nota " + c.npsNota() + (c.npsComentario() != null ? " - \"" + c.npsComentario() + "\"" : ""));
        }
        // observacao: os textos acima ja usam acentuacao correta (portugues)
        ev.addAll(c.fontesCasadas());
        return ev;
    }
}
