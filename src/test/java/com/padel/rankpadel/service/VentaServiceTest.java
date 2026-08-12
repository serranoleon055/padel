package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.VentaRequest;
import com.padel.rankpadel.dto.response.VentaResponse;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.Venta;
import com.padel.rankpadel.entity.VentaItem;
import com.padel.rankpadel.enums.CategoriaProducto;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.MotivoMovimientoStock;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.ClienteRepository;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("VentaService - ventas del mostrador")
class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private CobroRepository cobroRepository;
    @Mock
    private ProductoService productoService;
    @Mock
    private CajaCerradaGuard cajaCerradaGuard;

    @InjectMocks
    private VentaService ventaService;

    private Producto producto(Long id, String nombre, String precio, String costo, int stock) {
        return Producto.builder()
                .id(id).nombre(nombre).categoria(CategoriaProducto.PELOTAS)
                .precioVenta(new BigDecimal(precio))
                .costo(costo != null ? new BigDecimal(costo) : null)
                .controlaStock(true).stock(stock).activo(true)
                .build();
    }

    private VentaRequest pedido(MedioPago medio, Long productoId, int cantidad) {
        VentaRequest.Item item = new VentaRequest.Item();
        item.setProductoId(productoId);
        item.setCantidad(cantidad);
        VentaRequest request = new VentaRequest();
        request.setMedio(medio);
        request.setItems(List.of(item));
        return request;
    }

    /** El mismo producto cargado en dos renglones distintos, como lo hace el mostrador. */
    private VentaRequest pedidoRepetido(Long productoId, int primera, int segunda) {
        VentaRequest.Item uno = new VentaRequest.Item();
        uno.setProductoId(productoId);
        uno.setCantidad(primera);
        VentaRequest.Item dos = new VentaRequest.Item();
        dos.setProductoId(productoId);
        dos.setCantidad(segunda);
        VentaRequest request = new VentaRequest();
        request.setMedio(MedioPago.EFECTIVO);
        request.setItems(List.of(uno, dos));
        return request;
    }

    @Nested
    @DisplayName("Cuenta abierta del turno")
    class CuentaAbierta {

        @Test
        @DisplayName("Sin medio de pago y sin turno, la venta se rechaza")
        void sinMedioNiTurno_lanza() {
            // Sería plata que se pierde de vista: ni entró a la caja ni quedó como deuda.
            assertThatThrownBy(() -> ventaService.registrar(pedido(null, 1L, 1)))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("cargá el consumo a un turno");
        }

        @Test
        @DisplayName("Sin medio de pago, el consumo queda anotado en la cuenta del turno")
        void sinMedio_conTurno_seAnota() {
            Reserva turno = Reserva.builder().id(7L).estado(EstadoReserva.CONFIRMADA).build();
            when(reservaRepository.findById(7L)).thenReturn(Optional.of(turno));
            when(productoService.buscar(1L)).thenReturn(producto(1L, "Coca 500", "3000", "1800", 20));

            VentaRequest request = pedido(null, 1L, 2);
            request.setReservaId(7L);
            VentaResponse respuesta = ventaService.registrar(request);

            assertThat(respuesta.getMedio()).isNull();
            assertThat(respuesta.getReservaId()).isEqualTo(7L);
            assertThat(respuesta.getTotal()).isEqualByComparingTo("6000");
        }

        @Test
        @DisplayName("No se puede anotar consumo en un turno cancelado")
        void sinMedio_turnoCancelado_lanza() {
            Reserva cancelado = Reserva.builder().id(7L).estado(EstadoReserva.CANCELADA).build();
            when(reservaRepository.findById(7L)).thenReturn(Optional.of(cancelado));

            VentaRequest request = pedido(null, 1L, 1);
            request.setReservaId(7L);

            assertThatThrownBy(() -> ventaService.registrar(request))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("cobrá el consumo en el momento");
        }
    }

    @Nested
    @DisplayName("Registrar una venta")
    class Registrar {

        @Test
        @DisplayName("Suma los renglones y descuenta el stock de cada producto")
        void registrar_calculaTotalYDescuentaStock() {
            Producto pelotas = producto(1L, "Pelotas Head", "18000", "12000", 10);
            when(productoService.buscar(1L)).thenReturn(pelotas);

            VentaResponse respuesta = ventaService.registrar(pedido(MedioPago.EFECTIVO, 1L, 2));

            assertThat(respuesta.getTotal()).isEqualByComparingTo("36000");
            assertThat(respuesta.getDetalle()).isEqualTo("2 x Pelotas Head");
            verify(productoService).aplicarMovimiento(eq(pelotas), eq(-2),
                    eq(MotivoMovimientoStock.VENTA), any(Venta.class), isNull(), isNull());
        }

        @Test
        @DisplayName("Congela el precio y el costo del momento de la venta")
        void registrar_congelaPrecioYCosto() {
            when(productoService.buscar(1L)).thenReturn(producto(1L, "Pelotas Head", "18000", "12000", 10));

            ventaService.registrar(pedido(MedioPago.EFECTIVO, 1L, 1));

            ArgumentCaptor<Venta> guardada = ArgumentCaptor.forClass(Venta.class);
            verify(ventaRepository).save(guardada.capture());
            VentaItem item = guardada.getValue().getItems().get(0);
            assertThat(item.getPrecioUnitario()).isEqualByComparingTo("18000");
            assertThat(item.getCostoUnitario()).isEqualByComparingTo("12000");
        }

        @Test
        @DisplayName("No deja vender más unidades de las que hay")
        void registrar_sinStock_rechaza() {
            when(productoService.buscar(1L)).thenReturn(producto(1L, "Pelotas Head", "18000", null, 1));

            assertThatThrownBy(() -> ventaService.registrar(pedido(MedioPago.EFECTIVO, 1L, 3)))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("quedan 1");
            verify(ventaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Un producto sin control de stock se vende siempre")
        void registrar_sinControlDeStock_seVendeIgual() {
            Producto alquiler = Producto.builder()
                    .id(2L).nombre("Alquiler de paleta").categoria(CategoriaProducto.ALQUILER)
                    .precioVenta(new BigDecimal("3000")).controlaStock(false).stock(0).activo(true)
                    .build();
            when(productoService.buscar(2L)).thenReturn(alquiler);

            VentaResponse respuesta = ventaService.registrar(pedido(MedioPago.EFECTIVO, 2L, 4));

            assertThat(respuesta.getTotal()).isEqualByComparingTo("12000");
        }

        @Test
        @DisplayName("El mismo producto en varios renglones se suma antes de mirar el stock")
        void registrar_productoRepetido_sumaAntesDeValidar() {
            // El mostrador lo agrega de a uno. Si cada renglón se valida por separado,
            // entre todos se vende más de lo que hay y el stock queda en negativo.
            when(productoService.buscar(1L)).thenReturn(producto(1L, "Gatorade", "3000", "1800", 3));

            assertThatThrownBy(() -> ventaService.registrar(pedidoRepetido(1L, 2, 2)))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("quedan 3");
            verify(ventaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Los renglones repetidos se unifican en uno solo")
        void registrar_productoRepetido_unificaElRenglon() {
            Producto gatorade = producto(1L, "Gatorade", "3000", "1800", 10);
            when(productoService.buscar(1L)).thenReturn(gatorade);

            VentaResponse respuesta = ventaService.registrar(pedidoRepetido(1L, 2, 1));

            assertThat(respuesta.getTotal()).isEqualByComparingTo("9000");
            assertThat(respuesta.getItems()).hasSize(1);
            verify(productoService).aplicarMovimiento(eq(gatorade), eq(-3),
                    eq(MotivoMovimientoStock.VENTA), any(Venta.class), isNull(), isNull());
        }

        @Test
        @DisplayName("No se puede vender un producto dado de baja")
        void registrar_productoDadoDeBaja_rechaza() {
            Producto viejo = producto(1L, "Pelotas viejas", "18000", null, 10);
            viejo.setActivo(false);
            when(productoService.buscar(1L)).thenReturn(viejo);

            assertThatThrownBy(() -> ventaService.registrar(pedido(MedioPago.EFECTIVO, 1L, 1)))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("dado de baja");
        }
    }

    @Nested
    @DisplayName("Anular una venta")
    class Anular {

        private Venta ventaDeDosPelotas(Producto pelotas, MedioPago medio) {
            Venta venta = Venta.builder()
                    .id(7L).fecha(LocalDateTime.now()).total(new BigDecimal("36000"))
                    .medio(medio).items(new java.util.ArrayList<>())
                    .build();
            venta.getItems().add(VentaItem.builder()
                    .venta(venta).producto(pelotas).cantidad(2)
                    .precioUnitario(new BigDecimal("18000")).build());
            return venta;
        }

        @Test
        @DisplayName("Devuelve la mercadería al stock y deja la venta anulada, no borrada")
        void anular_devuelveStockYMarcaAnulada() {
            Producto pelotas = producto(1L, "Pelotas Head", "18000", "12000", 8);
            Venta venta = ventaDeDosPelotas(pelotas, MedioPago.EFECTIVO);
            when(ventaRepository.findById(7L)).thenReturn(Optional.of(venta));

            ventaService.anular(7L, "Se cargó al turno equivocado", false);

            verify(productoService).aplicarMovimiento(eq(pelotas), eq(2),
                    eq(MotivoMovimientoStock.ANULACION), eq(venta), isNull(), any());
            // La fila queda: un movimiento de plata que desaparece sin rastro hace que el
            // cierre de un día pasado cambie solo.
            verify(ventaRepository, never()).delete(any());
            assertThat(venta.estaAnulada()).isTrue();
            assertThat(venta.getMotivoAnulacion()).isEqualTo("Se cargó al turno equivocado");
        }

        @Test
        @DisplayName("No se anula dos veces la misma venta")
        void anular_yaAnulada_rechaza() {
            Venta venta = ventaDeDosPelotas(producto(1L, "Pelotas Head", "18000", null, 8), MedioPago.EFECTIVO);
            venta.setAnuladoEn(LocalDateTime.now().minusHours(1));
            when(ventaRepository.findById(7L)).thenReturn(Optional.of(venta));

            assertThatThrownBy(() -> ventaService.anular(7L, null, false))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("ya está anulada");
        }

        @Test
        @DisplayName("Un consumo a cuenta ya cobrado pide confirmación antes de anularse")
        void anular_consumoYaCobrado_pideConfirmacion() {
            // Sin medio propio, la venta iba a la cuenta del turno; si el turno ya registra
            // cobros, esa plata entró y anular dejaría el cobro sin renglón que lo explique.
            Producto pelotas = producto(1L, "Pelotas Head", "18000", "12000", 8);
            Venta venta = ventaDeDosPelotas(pelotas, null);
            venta.setReserva(Reserva.builder().id(3L).estado(EstadoReserva.FINALIZADA).build());
            when(ventaRepository.findById(7L)).thenReturn(Optional.of(venta));
            when(cobroRepository.totalCobradoDe(3L)).thenReturn(new BigDecimal("36000"));

            assertThatThrownBy(() -> ventaService.anular(7L, null, false))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("ya se cobró");
            verify(productoService, never()).aplicarMovimiento(any(), anyInt(), any(), any(), any(), any());

            ventaService.anular(7L, "Lo pidieron y no lo llevaron", true);
            assertThat(venta.estaAnulada()).isTrue();
        }

        @Test
        @DisplayName("Anular una venta que no existe no rompe nada")
        void anular_inexistente_falla() {
            when(ventaRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ventaService.anular(99L, null, false))
                    .isInstanceOf(com.padel.rankpadel.exception.ResourceNotFoundException.class);
            verify(productoService, never()).aplicarMovimiento(any(), anyInt(), any(), any(), any(), any());
        }
    }
}
