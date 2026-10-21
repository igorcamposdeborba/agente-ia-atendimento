package com.millenium.agente.core.port;

import com.millenium.agente.core.dto.MegazapRecord;
import com.millenium.agente.core.dto.N1Record;
import com.millenium.agente.core.dto.NpsRecord;
import com.millenium.agente.core.dto.PeopleRecord;

import java.util.List;

/**
 * Porta de saida do nucleo para as fontes de dados. Na Fase 1 o adaptador le Excel;
 * na Fase 2 outro adaptador le a tabela Cliente 360 (MySQL) - o nucleo nao muda.
 */
public interface DataSourcePort {

    List<N1Record> readN1();

    List<MegazapRecord> readMegazap();

    List<PeopleRecord> readPeople();

    List<NpsRecord> readNps();

    /** NPS da Secullum (coluna Atendimento), em campo SEPARADO do NPS da Millenium. */
    List<NpsRecord> readNpsSecullum();

    /** Recarrega a partir da fonte (ex.: novo export). */
    void reload();
}
