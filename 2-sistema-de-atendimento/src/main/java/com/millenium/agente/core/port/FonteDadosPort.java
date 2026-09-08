package com.millenium.agente.core.port;

import com.millenium.agente.core.model.RegistroMegazap;
import com.millenium.agente.core.model.RegistroN1;
import com.millenium.agente.core.model.RegistroNps;
import com.millenium.agente.core.model.RegistroPeople;

import java.util.List;

/**
 * Porta de saida do nucleo para as fontes de dados. Na Fase 1 o adaptador le Excel;
 * na Fase 2 outro adaptador le a tabela Cliente 360 (MySQL) - o nucleo nao muda.
 */
public interface FonteDadosPort {

    List<RegistroN1> lerN1();

    List<RegistroMegazap> lerMegazap();

    List<RegistroPeople> lerPeople();

    List<RegistroNps> lerNps();

    /** Recarrega a partir da fonte (ex.: novo export). */
    void recarregar();
}
