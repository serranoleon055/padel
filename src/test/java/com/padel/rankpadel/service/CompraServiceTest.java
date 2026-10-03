package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.CompraRequest;
import com.padel.rankpadel.dto.request.GastoRequest;
import com.padel.rankpadel.entity.DocumentoCompra;
import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.entity.MovimientoStock;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.entity.Proveedor;
import com.padel.rankpadel.enums.CategoriaProducto;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.TipoComprobanteCompra;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.DocumentoCompraRepository;
import com.padel.rankpadel.repository.MovimientoStockRepository;
import com.padel.rankpadel.repository.ProductoRepository;
import com.padel.rankpadel.repository.ProveedorRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompraService - un comprobante con varios productos")
class CompraServiceTest {

    @Mock
    private DocumentoCompraRepository documentoCompraRepository;
    @Mock
    private MovimientoStockRepository movimientoStockRepository;
    @Mock
    private ProveedorRepository proveedorRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private ProductoService productoService;
    @Mock
    private GastoService gastoService;
    @Mock
    private CajaCerradaGuard cajaCerradaGuard;
    @Mock
    private DisponibilidadCanchaService disponibilidadCanchaService;

    @InjectMocks
    private CompraService compraService;

    private static final LocalDate JORNADA = LocalDate.of(2026, 8, 15);

    private final Proveedor distribuidora = Proveedor.builder().id(1L).nombre("Distribuidora Sur").build();

    @BeforeEach
    void setUp() {
        lenient().when(disponibilidadCanchaService.fechaDeJornadaActual()).thenReturn(JORNADA);
        lenient().when(proveedorRepository.findById(1L)).thenReturn(Optional.of(distribuidora));
        lenient().when(documentoCompraRepository.save(any(DocumentoCompra.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(gastoService.registrarEntidad(any(), any(), anyBoolean()))
                .thenAnswer(i -> Gasto.builder().id(7L).monto(((GastoRequest) i.getArgument(0)).getMonto()).build());
        lenient().when(movimientoStockRepository.findDeLaCompra(any())).thenReturn(List.of());
    }

    private Producto producto(Long id, String nombre) {
        return Producto.builder().id(id).nombre(nombre).categoria(CategoriaProducto.BEBIDAS)
                .controlaStock(true).stock(0).activo(true).build();
    }

    private CompraRequest.Item item(Long productoId, int cantidad, String costo) {
        CompraRequest.Item item = new CompraRequest.Item();
        item.setProductoId(productoId);
        item.setCantidad(cantidad);
        item.setCostoUnitario(new BigDecimal(costo));
        return item;
    }

    private CompraRequest pedido(MedioPago medio, CompraRequest.Item... items) {
        CompraRequest request = new CompraRequest();
        request.setProveedorId(1L);
        request.setTipoComprobante(TipoComprobanteCompra.REMITO);
        request.setNumero("  0001-00012345  ");
        request.setFecha(JORNADA);
        request.setMedio(medio);
        request.setItems(List.of(items));
        return request;
    }

    @Nested
    @DisplayName("Carga")
    class Carga {

        @Test
        @DisplayName("Un comprobante con tres productos deja UN gasto y tres entradas de stock")
        void registrar_unGastoYNEntradas() {
            // Era el problema de fondo: un remito de doce productos eran doce gastos
            // sueltos, imposibles de reconciliar contra el papel.
            Producto gatorade = producto(10L, "Gatorade");
            Producto agua = producto(11L, "Agua");
            Producto pelotas = producto(12L, "Pelotas");
            when(productoRepository.findById(10L)).thenReturn(Optional.of(gatorade));
            when(productoRepository.findById(11L)).thenReturn(Optional.of(agua));
            when(productoRepository.findById(12L)).thenReturn(Optional.of(pelotas));

            compraService.registrar(pedido(MedioPago.EFECTIVO,
                    item(10L, 12, "1500"), item(11L, 24, "800"), item(12L, 6, "4000")));

            // 18.000 + 19.200 + 24.000
            ArgumentCaptor<GastoRequest> gasto = ArgumentCaptor.forClass(GastoRequest.class);
            verify(gastoService).registrarEntidad(gasto.capture(), eq(null), eq(true));
            assertThat(gasto.getValue().getMonto()).isEqualByComparingTo("61200");
            assertThat(gasto.getValue().getDescripcion()).contains("0001-00012345", "Distribuidora Sur");

            verify(productoService).ingresarMercaderia(eq(gatorade), eq(12), any(), any(), any(), any());
            verify(productoService).ingresarMercaderia(eq(agua), eq(24), any(), any(), any(), any());
            verify(productoService).ingresarMercaderia(eq(pelotas), eq(6), any(), any(), any(), any());
        }

        @Test
        @DisplayName("El mismo producto en dos renglones a precios distintos suma bien")
        void registrar_productoRepetido_ponderaElCosto() {
            // El proveedor factura dos lotes del mismo producto a precios distintos.
            // Quedarse con uno de los dos precios y multiplicarlo por la cantidad total
            // daría un total que no es el del comprobante.
            Producto gatorade = producto(10L, "Gatorade");
            when(productoRepository.findById(10L)).thenReturn(Optional.of(gatorade));

            compraService.registrar(pedido(MedioPago.EFECTIVO,
                    item(10L, 10, "1000"), item(10L, 10, "2000")));

            ArgumentCaptor<GastoRequest> gasto = ArgumentCaptor.forClass(GastoRequest.class);
            verify(gastoService).registrarEntidad(gasto.capture(), any(), anyBoolean());
            assertThat(gasto.getValue().getMonto()).isEqualByComparingTo("30000");
            // Una sola entrada de 20 unidades a $1.500 promedio, no dos entradas sueltas.
            verify(productoService).ingresarMercaderia(eq(gatorade), eq(20),
                    eq(new BigDecimal("1500.00")), any(), any(), any());
        }

        @Test
        @DisplayName("Sin medio de pago queda a cuenta corriente del proveedor")
        void registrar_sinMedio_quedaACredito() {
            Producto gatorade = producto(10L, "Gatorade");
            when(productoRepository.findById(10L)).thenReturn(Optional.of(gatorade));

            compraService.registrar(pedido(null, item(10L, 12, "1500")));

            ArgumentCaptor<DocumentoCompra> compra = ArgumentCaptor.forClass(DocumentoCompra.class);
            verify(documentoCompraRepository).save(compra.capture());
            assertThat(compra.getValue().esACredito()).isTrue();
            assertThat(compra.getValue().getJornada()).isEqualTo(JORNADA);
            // El egreso se registra igual: la mercadería entró y pesa en la rentabilidad.
            // Lo que no hace es salir del cajón, y de eso se encarga la consulta de caja.
            verify(gastoService).registrarEntidad(any(), any(), eq(true));
        }

        @Test
        @DisplayName("El mismo comprobante del mismo proveedor no se carga dos veces")
        void registrar_duplicado_rechaza() {
            when(documentoCompraRepository.existsByProveedorIdAndTipoComprobanteAndNumeroAndAnuladoEnIsNull(
                    1L, TipoComprobanteCompra.REMITO, "0001-00012345")).thenReturn(true);

            assertThatThrownBy(() -> compraService.registrar(
                    pedido(MedioPago.EFECTIVO, item(10L, 12, "1500"))))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("Ya cargaste");

            verify(documentoCompraRepository, never()).save(any());
        }

        @Test
        @DisplayName("Con la caja de la jornada arqueada, la compra no entra ni al stock")
        void registrar_jornadaCerrada_rechaza() {
            doThrow(new EstadoInvalidoException("La caja del 15/08/2026 ya está cerrada"))
                    .when(cajaCerradaGuard).exigirDiaAbierto(JORNADA);

            assertThatThrownBy(() -> compraService.registrar(
                    pedido(MedioPago.EFECTIVO, item(10L, 12, "1500"))))
                    .isInstanceOf(EstadoInvalidoException.class);

            verify(productoService, never()).ingresarMercaderia(any(), anyInt(), any(), any(), any(), any());
            verify(documentoCompraRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Anulación")
    class Anulacion {

        private DocumentoCompra existente() {
            return DocumentoCompra.builder()
                    .id(5L).proveedor(distribuidora)
                    .tipoComprobante(TipoComprobanteCompra.REMITO).numero("0001-00012345")
                    .fecha(JORNADA).jornada(JORNADA).total(new BigDecimal("61200"))
                    .medio(MedioPago.EFECTIVO)
                    .gasto(Gasto.builder().id(7L).build())
                    .build();
        }

        @Test
        @DisplayName("Revierte el stock con movimientos compensatorios, sin borrar los originales")
        void anular_revierteConCompensatorios() {
            // El stock de un producto tiene que seguir siendo la suma de sus movimientos:
            // un renglón que desaparece deja un faltante que nadie puede explicar.
            DocumentoCompra compra = existente();
            Producto gatorade = producto(10L, "Gatorade");
            when(documentoCompraRepository.findById(5L)).thenReturn(Optional.of(compra));
            when(movimientoStockRepository.findDeLaCompra(5L)).thenReturn(List.of(
                    MovimientoStock.builder().id(1L).producto(gatorade).cantidad(12).build()));

            compraService.anular(5L, "vino mal el pedido");

            verify(productoService).revertirIngreso(eq(gatorade), eq(12), eq(compra), anyString());
            verify(movimientoStockRepository, never()).delete(any());
            verify(gastoService).anular(eq(7L), anyString());
            assertThat(compra.estaAnulado()).isTrue();
            assertThat(compra.getMotivoAnulacion()).isEqualTo("vino mal el pedido");
        }

        @Test
        @DisplayName("Una compra ya anulada no se vuelve a anular")
        void anular_dosVeces_rechaza() {
            DocumentoCompra compra = existente();
            compra.setAnuladoEn(java.time.LocalDateTime.now());
            when(documentoCompraRepository.findById(5L)).thenReturn(Optional.of(compra));

            assertThatThrownBy(() -> compraService.anular(5L, "otra vez"))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("ya está anulada");
        }
    }
}
