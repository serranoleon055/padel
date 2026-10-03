package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.ConteoRequest;
import com.padel.rankpadel.dto.request.RecuentoRequest;
import com.padel.rankpadel.dto.response.RecuentoResponse;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.entity.Recuento;
import com.padel.rankpadel.entity.RecuentoItem;
import com.padel.rankpadel.enums.CategoriaProducto;
import com.padel.rankpadel.enums.EstadoRecuento;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.ProductoRepository;
import com.padel.rankpadel.repository.RecuentoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecuentoService - contar el depósito")
class RecuentoServiceTest {

    @Mock
    private RecuentoRepository recuentoRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private ProductoService productoService;

    @InjectMocks
    private RecuentoService recuentoService;

    private final Producto pelotas = producto(1L, "Pelotas Head", 10, "12000");
    private final Producto agua = producto(2L, "Agua mineral", 24, "800");

    private static Producto producto(Long id, String nombre, int stock, String costo) {
        return Producto.builder()
                .id(id).nombre(nombre).categoria(CategoriaProducto.KIOSCO)
                .stock(stock).controlaStock(true).activo(true)
                .costo(new BigDecimal(costo)).costoPromedio(new BigDecimal(costo))
                .build();
    }

    @BeforeEach
    void setUp() {
        lenient().when(recuentoRepository.save(any(Recuento.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(recuentoRepository.findFirstByEstadoOrderByIdDesc(EstadoRecuento.BORRADOR))
                .thenReturn(Optional.empty());
    }

    private RecuentoRequest pedido() {
        RecuentoRequest request = new RecuentoRequest();
        request.setFecha(LocalDate.now());
        return request;
    }

    /** Una planilla ya abierta, con los renglones identificados para poder contarlos. */
    private Recuento abierto(int stockPelotas, int stockAgua) {
        Recuento recuento = Recuento.builder()
                .id(5L).fecha(LocalDate.now()).estado(EstadoRecuento.BORRADOR)
                .items(new ArrayList<>())
                .build();
        recuento.getItems().add(RecuentoItem.builder()
                .id(11L).recuento(recuento).producto(pelotas)
                .stockSistema(stockPelotas).costoUnitario(new BigDecimal("12000")).build());
        recuento.getItems().add(RecuentoItem.builder()
                .id(12L).recuento(recuento).producto(agua)
                .stockSistema(stockAgua).costoUnitario(new BigDecimal("800")).build());
        return recuento;
    }

    private ConteoRequest conteo(Long itemId, Integer contado) {
        ConteoRequest.Item item = new ConteoRequest.Item();
        item.setItemId(itemId);
        item.setStockContado(contado);
        ConteoRequest request = new ConteoRequest();
        request.setItems(List.of(item));
        return request;
    }

    @Nested
    @DisplayName("Abrir")
    class Abrir {

        @Test
        @DisplayName("Congela el stock de cada producto al armar la planilla")
        void abrir_congelaElStock() {
            when(productoRepository.findAll()).thenReturn(List.of(pelotas, agua));

            RecuentoResponse response = recuentoService.abrir(pedido());

            assertThat(response.getEstado()).isEqualTo("BORRADOR");
            assertThat(response.getTotal()).isEqualTo(2);
            assertThat(response.getContados()).isZero();
            assertThat(response.getItems()).extracting(RecuentoResponse.Item::getStockSistema)
                    .containsExactly(24, 10); // ordenados por nombre: Agua, Pelotas
        }

        @Test
        @DisplayName("Con un conteo ya abierto no se abre otro")
        void abrir_conUnoAbierto_rechaza() {
            // Dos planillas abiertas congelan cada una su propio stock y al aplicarlas la
            // segunda vuelve a descontar lo que la primera ya corrigió.
            when(recuentoRepository.findFirstByEstadoOrderByIdDesc(EstadoRecuento.BORRADOR))
                    .thenReturn(Optional.of(abierto(10, 24)));

            assertThatThrownBy(() -> recuentoService.abrir(pedido()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("Ya hay un conteo abierto");
        }

        @Test
        @DisplayName("Los productos sin control de stock no entran a la planilla")
        void abrir_salteaLoQueNoLlevaStock() {
            Producto alquiler = Producto.builder()
                    .id(3L).nombre("Alquiler de paleta").categoria(CategoriaProducto.ALQUILER)
                    .controlaStock(false).activo(true).build();
            when(productoRepository.findAll()).thenReturn(List.of(pelotas, alquiler));

            assertThat(recuentoService.abrir(pedido()).getTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("No se cuenta en una fecha futura")
        void abrir_fechaFutura_rechaza() {
            RecuentoRequest request = new RecuentoRequest();
            request.setFecha(LocalDate.now().plusDays(1));

            assertThatThrownBy(() -> recuentoService.abrir(request))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("futura");
        }
    }

    @Nested
    @DisplayName("Contar")
    class Contar {

        @Test
        @DisplayName("Anotar lo contado no toca el stock")
        void contar_noMueveElStock() {
            // Contar y corregir son dos actos distintos: entre uno y otro suele pasar que
            // alguien recuenta una caja.
            when(recuentoRepository.findById(5L)).thenReturn(Optional.of(abierto(10, 24)));

            RecuentoResponse response = recuentoService.contar(5L, conteo(11L, 7));

            assertThat(response.getContados()).isEqualTo(1);
            verify(productoService, never()).aplicarDeRecuento(any(), anyInt(), any(), any(), anyString());
        }

        @Test
        @DisplayName("El faltante se va viendo mientras se cuenta")
        void contar_valorizaLoQueVaFaltando() {
            when(recuentoRepository.findById(5L)).thenReturn(Optional.of(abierto(10, 24)));

            // Faltan 3 pelotas a $12.000.
            RecuentoResponse response = recuentoService.contar(5L, conteo(11L, 7));

            assertThat(response.getFaltanteValorizado()).isEqualByComparingTo("36000");
            assertThat(response.getSobranteValorizado()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Un renglón de otro conteo se rechaza")
        void contar_renglonAjeno_rechaza() {
            when(recuentoRepository.findById(5L)).thenReturn(Optional.of(abierto(10, 24)));

            assertThatThrownBy(() -> recuentoService.contar(5L, conteo(99L, 3)))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("no es de este conteo");
        }
    }

    @Nested
    @DisplayName("Aplicar")
    class Aplicar {

        @Test
        @DisplayName("Aplica la DIFERENCIA, no el total contado")
        void aplicar_mandaLaDiferencia() {
            // Es la decisión de fondo: entre que se empieza a contar y se aplica siguen
            // entrando ventas, y escribir el total contado como valor absoluto las borraría
            // del inventario. Lo que el conteo dice es "faltan tres".
            Recuento recuento = abierto(10, 24);
            recuento.getItems().get(0).setStockContado(7);
            when(recuentoRepository.findById(5L)).thenReturn(Optional.of(recuento));

            recuentoService.aplicar(5L);

            verify(productoService).aplicarDeRecuento(eq(pelotas), eq(-3), eq(recuento),
                    eq(new BigDecimal("12000")), anyString());
        }

        @Test
        @DisplayName("Un renglón sin contar no genera ajuste")
        void aplicar_salteaLoNoContado() {
            // No contar algo no es contar cero: asumirlo daría de baja todo lo que el club
            // no llegó a mirar.
            Recuento recuento = abierto(10, 24);
            recuento.getItems().get(0).setStockContado(10);
            when(recuentoRepository.findById(5L)).thenReturn(Optional.of(recuento));

            recuentoService.aplicar(5L);

            verify(productoService, never()).aplicarDeRecuento(any(), anyInt(), any(), any(), anyString());
        }

        @Test
        @DisplayName("Congela lo que faltó y lo que sobró, por separado")
        void aplicar_congelaFaltanteYSobrante() {
            Recuento recuento = abierto(10, 24);
            recuento.getItems().get(0).setStockContado(7);  // faltan 3 a 12.000
            recuento.getItems().get(1).setStockContado(26); // sobran 2 a 800
            when(recuentoRepository.findById(5L)).thenReturn(Optional.of(recuento));

            RecuentoResponse response = recuentoService.aplicar(5L);

            assertThat(response.getEstado()).isEqualTo("APLICADO");
            assertThat(response.getFaltanteValorizado()).isEqualByComparingTo("36000");
            assertThat(response.getSobranteValorizado()).isEqualByComparingTo("1600");
        }

        @Test
        @DisplayName("Un conteo aplicado no se vuelve a aplicar ni se edita")
        void aplicar_dosVeces_rechaza() {
            Recuento recuento = abierto(10, 24);
            recuento.setEstado(EstadoRecuento.APLICADO);
            when(recuentoRepository.findById(5L)).thenReturn(Optional.of(recuento));

            assertThatThrownBy(() -> recuentoService.aplicar(5L))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("ya se aplicó");
            assertThatThrownBy(() -> recuentoService.contar(5L, conteo(11L, 3)))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        @DisplayName("Un producto sin costo cargado no rompe la valorización")
        void aplicar_sinCosto_noValoriza() {
            // El costo es opcional: el club puede no saber todavía cuánto le sale algo.
            // Mismo criterio que `rankingProductos` en estadísticas.
            Recuento recuento = abierto(10, 24);
            recuento.getItems().get(0).setCostoUnitario(null);
            recuento.getItems().get(0).setStockContado(7);
            when(recuentoRepository.findById(5L)).thenReturn(Optional.of(recuento));

            RecuentoResponse response = recuentoService.aplicar(5L);

            // El ajuste se aplica igual: las unidades faltan aunque no se sepa qué valían.
            verify(productoService).aplicarDeRecuento(eq(pelotas), eq(-3), any(), eq(null), anyString());
            assertThat(response.getFaltanteValorizado()).isEqualByComparingTo("0");
        }
    }

    @Test
    @DisplayName("Descartar solo vale en borrador")
    void descartar_aplicado_rechaza() {
        Recuento recuento = abierto(10, 24);
        recuento.setEstado(EstadoRecuento.APLICADO);
        when(recuentoRepository.findById(5L)).thenReturn(Optional.of(recuento));

        assertThatThrownBy(() -> recuentoService.descartar(5L))
                .isInstanceOf(EstadoInvalidoException.class);
        verify(recuentoRepository, never()).delete(any());
    }
}
