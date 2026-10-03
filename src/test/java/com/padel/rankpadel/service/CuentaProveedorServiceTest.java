package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.PagoProveedorRequest;
import com.padel.rankpadel.dto.response.CuentaProveedorResponse;
import com.padel.rankpadel.entity.DocumentoCompra;
import com.padel.rankpadel.entity.PagoProveedor;
import com.padel.rankpadel.entity.Proveedor;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.TipoComprobanteCompra;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.DocumentoCompraRepository;
import com.padel.rankpadel.repository.PagoProveedorRepository;
import com.padel.rankpadel.repository.ProveedorRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CuentaProveedorService - qué le debe el club al proveedor")
class CuentaProveedorServiceTest {

    @Mock
    private DocumentoCompraRepository documentoCompraRepository;
    @Mock
    private PagoProveedorRepository pagoProveedorRepository;
    @Mock
    private ProveedorRepository proveedorRepository;
    @Mock
    private CajaCerradaGuard cajaCerradaGuard;
    @Mock
    private DisponibilidadCanchaService disponibilidadCanchaService;

    @InjectMocks
    private CuentaProveedorService cuentaProveedorService;

    private static final LocalDate JORNADA = LocalDate.of(2026, 8, 15);
    private final Proveedor distribuidora = Proveedor.builder().id(1L).nombre("Distribuidora Sur").build();

    @BeforeEach
    void setUp() {
        lenient().when(proveedorRepository.findById(1L)).thenReturn(Optional.of(distribuidora));
        lenient().when(disponibilidadCanchaService.fechaDeJornadaActual()).thenReturn(JORNADA);
        lenient().when(pagoProveedorRepository.save(any(PagoProveedor.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private DocumentoCompra compra(String numero, LocalDate fecha, String total) {
        return DocumentoCompra.builder()
                .id(1L).proveedor(distribuidora)
                .tipoComprobante(TipoComprobanteCompra.REMITO).numero(numero)
                .fecha(fecha).jornada(fecha).total(new BigDecimal(total))
                .build();
    }

    private PagoProveedor pago(LocalDate fecha, String monto) {
        return PagoProveedor.builder()
                .id(1L).proveedor(distribuidora).fecha(fecha).jornada(fecha)
                .monto(new BigDecimal(monto)).medio(MedioPago.EFECTIVO)
                .build();
    }

    @Test
    @DisplayName("El saldo son las compras a cuenta menos lo pagado")
    void cuenta_calculaElSaldo() {
        when(documentoCompraRepository.findACreditoDe(1L)).thenReturn(List.of(
                compra("0001", LocalDate.of(2026, 8, 1), "50000"),
                compra("0002", LocalDate.of(2026, 8, 10), "30000")));
        when(pagoProveedorRepository.findDe(1L)).thenReturn(List.of(
                pago(LocalDate.of(2026, 8, 5), "20000")));

        CuentaProveedorResponse cuenta = cuentaProveedorService.cuenta(1L);

        assertThat(cuenta.getComprasACredito()).isEqualByComparingTo("80000");
        assertThat(cuenta.getPagado()).isEqualByComparingTo("20000");
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("Los movimientos van en orden de fecha con el saldo que quedó en cada uno")
    void cuenta_llevaElSaldoFilaPorFila() {
        // Es como el club lo lee en el cuaderno: cada línea con lo que quedó debiendo.
        when(documentoCompraRepository.findACreditoDe(1L)).thenReturn(List.of(
                compra("0001", LocalDate.of(2026, 8, 1), "50000"),
                compra("0002", LocalDate.of(2026, 8, 10), "30000")));
        when(pagoProveedorRepository.findDe(1L)).thenReturn(List.of(
                pago(LocalDate.of(2026, 8, 5), "20000")));

        List<CuentaProveedorResponse.Movimiento> movimientos =
                cuentaProveedorService.cuenta(1L).getMovimientos();

        assertThat(movimientos).hasSize(3);
        assertThat(movimientos.get(0).getTipo()).isEqualTo("COMPRA");
        assertThat(movimientos.get(0).getSaldoAcumulado()).isEqualByComparingTo("50000");
        assertThat(movimientos.get(1).getTipo()).isEqualTo("PAGO");
        // El pago entra en negativo: baja la deuda.
        assertThat(movimientos.get(1).getMonto()).isEqualByComparingTo("-20000");
        assertThat(movimientos.get(1).getSaldoAcumulado()).isEqualByComparingTo("30000");
        assertThat(movimientos.get(2).getSaldoAcumulado()).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("Una compra pagada en el momento no entra en la cuenta corriente")
    void cuenta_laCompraPagadaNoSuma() {
        // Esa plata ya salió por la caja: contarla acá sería cobrarla dos veces. Por eso
        // la consulta trae solo las que tienen medio nulo.
        when(documentoCompraRepository.findACreditoDe(1L)).thenReturn(List.of());
        when(pagoProveedorRepository.findDe(1L)).thenReturn(List.of());

        assertThat(cuentaProveedorService.cuenta(1L).getSaldo()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("No se le puede pagar más de lo que se le debe")
    void pagar_deMas_rechaza() {
        when(documentoCompraRepository.totalACreditoDe(1L)).thenReturn(new BigDecimal("50000"));
        when(pagoProveedorRepository.totalPagadoA(1L)).thenReturn(new BigDecimal("20000"));

        PagoProveedorRequest request = new PagoProveedorRequest();
        request.setFecha(LocalDate.now());
        request.setMonto(new BigDecimal("40000"));
        request.setMedio(MedioPago.EFECTIVO);

        assertThatThrownBy(() -> cuentaProveedorService.pagar(1L, request))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("no se puede pagar más");

        verify(pagoProveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("El pago se estampa en la jornada en curso")
    void pagar_estampaLaJornada() {
        when(documentoCompraRepository.totalACreditoDe(1L)).thenReturn(new BigDecimal("50000"));
        when(pagoProveedorRepository.totalPagadoA(1L)).thenReturn(BigDecimal.ZERO);
        when(documentoCompraRepository.findACreditoDe(1L)).thenReturn(List.of());
        when(pagoProveedorRepository.findDe(1L)).thenReturn(List.of());

        PagoProveedorRequest request = new PagoProveedorRequest();
        request.setFecha(LocalDate.now());
        request.setMonto(new BigDecimal("30000"));
        request.setMedio(MedioPago.EFECTIVO);

        cuentaProveedorService.pagar(1L, request);

        org.mockito.ArgumentCaptor<PagoProveedor> guardado =
                org.mockito.ArgumentCaptor.forClass(PagoProveedor.class);
        verify(pagoProveedorRepository).save(guardado.capture());
        assertThat(guardado.getValue().getJornada()).isEqualTo(JORNADA);
    }

    @Test
    @DisplayName("Con la caja arqueada no se le puede pagar al proveedor")
    void pagar_jornadaCerrada_rechaza() {
        when(documentoCompraRepository.totalACreditoDe(1L)).thenReturn(new BigDecimal("50000"));
        when(pagoProveedorRepository.totalPagadoA(1L)).thenReturn(BigDecimal.ZERO);
        doThrow(new EstadoInvalidoException("cerrada")).when(cajaCerradaGuard).exigirDiaAbierto(JORNADA);

        PagoProveedorRequest request = new PagoProveedorRequest();
        request.setFecha(LocalDate.now());
        request.setMonto(new BigDecimal("10000"));
        request.setMedio(MedioPago.EFECTIVO);

        assertThatThrownBy(() -> cuentaProveedorService.pagar(1L, request))
                .isInstanceOf(EstadoInvalidoException.class);

        verify(pagoProveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un pago con fecha futura no entra")
    void pagar_fechaFutura_rechaza() {
        PagoProveedorRequest request = new PagoProveedorRequest();
        request.setFecha(LocalDate.now().plusDays(1));
        request.setMonto(new BigDecimal("10000"));
        request.setMedio(MedioPago.EFECTIVO);

        assertThatThrownBy(() -> cuentaProveedorService.pagar(1L, request))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("futura");
    }

    @Test
    @DisplayName("Anular un pago es baja lógica")
    void anularPago_esBajaLogica() {
        PagoProveedor existente = pago(LocalDate.of(2026, 8, 15), "20000");
        when(pagoProveedorRepository.findById(1L)).thenReturn(Optional.of(existente));

        cuentaProveedorService.anularPago(1L, "lo cargué dos veces");

        assertThat(existente.estaAnulado()).isTrue();
        assertThat(existente.getMotivoAnulacion()).isEqualTo("lo cargué dos veces");
        verify(pagoProveedorRepository, never()).delete(any());
    }
}
