package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.AporteSponsorRequest;
import com.padel.rankpadel.dto.request.PremioTorneoRequest;
import com.padel.rankpadel.dto.response.CuentaInscripcionesResponse;
import com.padel.rankpadel.dto.response.FinanzasTorneoResponse;
import com.padel.rankpadel.entity.AporteSponsor;
import com.padel.rankpadel.entity.CampeonTorneo;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.GastoTorneo;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.PagoSede;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.PremioTorneo;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.entity.TorneoSede;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.AporteSponsorRepository;
import com.padel.rankpadel.repository.CampeonTorneoRepository;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.GastoTorneoRepository;
import com.padel.rankpadel.repository.PremioTorneoRepository;
import com.padel.rankpadel.repository.SponsorRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.repository.TorneoSedeRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("FinanzasTorneoService - el balance del torneo")
class FinanzasTorneoServiceTest {

    @Mock private TorneoRepository torneoRepository;
    @Mock private TorneoSedeRepository torneoSedeRepository;
    @Mock private PremioTorneoRepository premioTorneoRepository;
    @Mock private GastoTorneoRepository gastoTorneoRepository;
    @Mock private AporteSponsorRepository aporteSponsorRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private SponsorRepository sponsorRepository;
    @Mock private CampeonTorneoRepository campeonTorneoRepository;
    @Mock private CobroInscripcionService cobroInscripcionService;

    @InjectMocks
    private FinanzasTorneoService service;

    private static BigDecimal pesos(String monto) {
        return new BigDecimal(monto);
    }

    @Test
    @DisplayName("Proyectado con todo lo comprometido; real con lo que ya entró y salió")
    void balance() {
        // Inscripciones: 600.000 a cobrar, 450.000 cobrados.
        when(cobroInscripcionService.cuenta(1L)).thenReturn(CuentaInscripcionesResponse.builder()
                .totalEsperado(pesos("600000")).totalCobrado(pesos("450000")).totalPendiente(pesos("150000")).build());
        // Sponsors: 100.000 comprometidos, 60.000 cobrados, más un canje sin plata.
        when(aporteSponsorRepository.findByTorneoIdOrderByIdAsc(1L)).thenReturn(List.of(
                AporteSponsor.builder().monto(pesos("60000")).cobrado(true).build(),
                AporteSponsor.builder().monto(pesos("40000")).build(),
                AporteSponsor.builder().descripcion("Pelotas").build()));
        // Clubes: 200.000 acordados, 150.000 pagados (más un pago anulado que no cuenta).
        TorneoSede club = TorneoSede.builder().montoAcordado(pesos("200000")).build();
        club.getPagos().add(PagoSede.builder().monto(pesos("150000")).build());
        club.getPagos().add(PagoSede.builder().monto(pesos("50000")).anuladoEn(LocalDateTime.now()).build());
        when(torneoSedeRepository.findByTorneoIdOrderByIdAsc(1L)).thenReturn(List.of(club, TorneoSede.builder().build()));
        // Premios: 120.000 en plata, 80.000 entregados; uno solo en productos.
        when(premioTorneoRepository.findByTorneoIdOrderByIdAsc(1L)).thenReturn(List.of(
                PremioTorneo.builder().monto(pesos("80000")).entregado(true).build(),
                PremioTorneo.builder().monto(pesos("40000")).build(),
                PremioTorneo.builder().descripcion("Paletas").build()));
        // Gastos: 30.000 (uno anulado no cuenta).
        when(gastoTorneoRepository.findByTorneoIdOrderByFechaAscIdAsc(1L)).thenReturn(List.of(
                GastoTorneo.builder().monto(pesos("30000")).build(),
                GastoTorneo.builder().monto(pesos("9999")).anuladoEn(LocalDateTime.now()).build()));

        FinanzasTorneoResponse.Balance b = service.balance(1L);

        // 700.000 de ingresos - (200.000 + 120.000 + 30.000) = 350.000
        assertThat(b.getResultadoProyectado()).isEqualByComparingTo("350000");
        // 510.000 entrados - (150.000 + 80.000 + 30.000) = 250.000
        assertThat(b.getResultadoReal()).isEqualByComparingTo("250000");
        assertThat(b.getPorCobrar()).isEqualByComparingTo("190000");
        assertThat(b.getPorPagar()).isEqualByComparingTo("90000");
        assertThat(b.getClubesPagado()).isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("El premio de campeón y subcampeón muestra a la pareja que lo ganó")
    void ganador() {
        Categoria quinta = Categoria.builder().id(5L).nombre("Quinta").build();
        Pareja campeona = Pareja.builder().jugador1(Jugador.builder().nombre("Ana").apellido("Paz").build())
                .jugador2(Jugador.builder().nombre("Bea").apellido("Sol").build()).build();
        Pareja subcampeona = Pareja.builder().jugador1(Jugador.builder().nombre("Caro").apellido("Gil").build())
                .jugador2(Jugador.builder().nombre("Dani").apellido("Luz").build()).build();
        List<CampeonTorneo> campeones = List.of(CampeonTorneo.builder().categoria(quinta)
                .parejaCampeona(campeona).parejaSubcampeona(subcampeona).build());

        assertThat(FinanzasTorneoService.ganador(PremioTorneo.builder().categoria(quinta).puesto("Campeón").build(), campeones))
                .isEqualTo("Ana Paz / Bea Sol");
        assertThat(FinanzasTorneoService.ganador(PremioTorneo.builder().categoria(quinta).puesto("Subcampeones").build(), campeones))
                .isEqualTo("Caro Gil / Dani Luz");
        assertThat(FinanzasTorneoService.ganador(PremioTorneo.builder().categoria(quinta).puesto("Finalista").build(), campeones))
                .isEqualTo("Caro Gil / Dani Luz");
        assertThat(FinanzasTorneoService.ganador(PremioTorneo.builder().categoria(quinta).puesto("Semifinalistas").build(), campeones))
                .isNull();
    }

    @Test
    @DisplayName("Un premio necesita plata o qué se entrega; un aporte, quién lo pone")
    void validaciones() {
        when(torneoRepository.findById(1L)).thenReturn(Optional.of(Torneo.builder().id(1L).build()));
        PremioTorneoRequest premio = new PremioTorneoRequest();
        premio.setPuesto("Campeón");
        assertThatThrownBy(() -> service.agregarPremio(1L, premio)).isInstanceOf(EstadoInvalidoException.class);

        AporteSponsorRequest aporte = new AporteSponsorRequest();
        aporte.setMonto(pesos("1000"));
        assertThatThrownBy(() -> service.agregarAporte(1L, aporte))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("sponsor");
    }
}
