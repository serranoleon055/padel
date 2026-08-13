package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.MovimientoStockRequest;
import com.padel.rankpadel.dto.request.ProductoRequest;
import com.padel.rankpadel.dto.response.ProductoResponse;
import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.entity.MovimientoStock;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.enums.CategoriaGasto;
import com.padel.rankpadel.enums.CategoriaProducto;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.MotivoMovimientoStock;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.GastoRepository;
import com.padel.rankpadel.repository.MovimientoStockRepository;
import com.padel.rankpadel.repository.ProductoRepository;
import com.padel.rankpadel.repository.ProveedorRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductoService - stock del mostrador")
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private ProveedorRepository proveedorRepository;
    @Mock
    private MovimientoStockRepository movimientoStockRepository;
    @Mock
    private GastoRepository gastoRepository;

    @InjectMocks
    private ProductoService productoService;

    private Producto pelotas(int stock) {
        return Producto.builder()
                .id(1L).nombre("Pelotas Head").categoria(CategoriaProducto.PELOTAS)
                .precioVenta(new BigDecimal("18000")).costo(new BigDecimal("12000"))
                .controlaStock(true).stock(stock).stockMinimo(3).activo(true)
                .build();
    }

    private MovimientoStockRequest compra(int cantidad, String costo, MedioPago medio) {
        MovimientoStockRequest request = new MovimientoStockRequest();
        request.setCantidad(cantidad);
        if (costo != null) request.setCostoUnitario(new BigDecimal(costo));
        request.setMedioPago(medio);
        request.setFecha(LocalDate.now());
        return request;
    }

    @Nested
    @DisplayName("Compra de mercadería")
    class Compras {

        @Test
        @DisplayName("Suma las unidades y actualiza el costo con el de la última compra")
        void comprar_sumaStockYActualizaCosto() {
            Producto producto = pelotas(2);
            when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

            ProductoResponse respuesta = productoService.comprar(1L, compra(10, "13500", null));

            assertThat(producto.getStock()).isEqualTo(12);
            assertThat(producto.getCosto()).isEqualByComparingTo("13500");
            assertThat(respuesta.getMargenUnitario()).isEqualByComparingTo("4500");
        }

        @Test
        @DisplayName("Con medio de pago, la compra también queda como egreso del club")
        void comprar_conMedioDePago_registraGasto() {
            when(productoRepository.findById(1L)).thenReturn(Optional.of(pelotas(0)));

            productoService.comprar(1L, compra(10, "13500", MedioPago.EFECTIVO));

            ArgumentCaptor<Gasto> gasto = ArgumentCaptor.forClass(Gasto.class);
            verify(gastoRepository).save(gasto.capture());
            assertThat(gasto.getValue().getMonto()).isEqualByComparingTo("135000");
            assertThat(gasto.getValue().getCategoria()).isEqualTo(CategoriaGasto.INSUMOS);
            assertThat(gasto.getValue().getMedio()).isEqualTo(MedioPago.EFECTIVO);
        }

        @Test
        @DisplayName("Sin medio de pago no se duplica el egreso")
        void comprar_sinMedioDePago_noRegistraGasto() {
            when(productoRepository.findById(1L)).thenReturn(Optional.of(pelotas(0)));

            productoService.comprar(1L, compra(10, "13500", null));

            verify(gastoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Un producto sin control de stock no se compra")
        void comprar_sinControlDeStock_rechaza() {
            Producto alquiler = pelotas(0);
            alquiler.setControlaStock(false);
            alquiler.setNombre("Alquiler de paleta");
            when(productoRepository.findById(1L)).thenReturn(Optional.of(alquiler));

            assertThatThrownBy(() -> productoService.comprar(1L, compra(5, "0", null)))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("no lleva control de stock");
        }
    }

    @Nested
    @DisplayName("Ajuste y merma")
    class AjusteYMerma {

        @Test
        @DisplayName("El ajuste guarda la diferencia entre lo contado y lo que decía el sistema")
        void ajustar_guardaLaDiferencia() {
            Producto producto = pelotas(10);
            when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

            productoService.ajustar(1L, 7, "Conteo de fin de mes");

            assertThat(producto.getStock()).isEqualTo(7);
            ArgumentCaptor<MovimientoStock> movimiento = ArgumentCaptor.forClass(MovimientoStock.class);
            verify(movimientoStockRepository).save(movimiento.capture());
            assertThat(movimiento.getValue().getCantidad()).isEqualTo(-3);
            assertThat(movimiento.getValue().getMotivo()).isEqualTo(MotivoMovimientoStock.AJUSTE);
        }

        @Test
        @DisplayName("Si lo contado coincide con el sistema no se registra nada")
        void ajustar_sinDiferencia_noRegistra() {
            when(productoRepository.findById(1L)).thenReturn(Optional.of(pelotas(10)));

            productoService.ajustar(1L, 10, null);

            verify(movimientoStockRepository, never()).save(any());
        }

        @Test
        @DisplayName("Un stock negativo se rechaza")
        void ajustar_negativo_rechaza() {
            when(productoRepository.findById(1L)).thenReturn(Optional.of(pelotas(10)));

            assertThatThrownBy(() -> productoService.ajustar(1L, -1, null))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("no puede ser negativo");
        }

        @Test
        @DisplayName("La merma descuenta unidades y queda registrada como tal")
        void merma_descuenta() {
            Producto producto = pelotas(10);
            when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
            MovimientoStockRequest request = new MovimientoStockRequest();
            request.setCantidad(2);
            request.setNotas("Se rompieron");

            productoService.registrarMerma(1L, request);

            assertThat(producto.getStock()).isEqualTo(8);
            ArgumentCaptor<MovimientoStock> movimiento = ArgumentCaptor.forClass(MovimientoStock.class);
            verify(movimientoStockRepository).save(movimiento.capture());
            assertThat(movimiento.getValue().getCantidad()).isEqualTo(-2);
            assertThat(movimiento.getValue().getMotivo()).isEqualTo(MotivoMovimientoStock.MERMA);
        }

        @Test
        @DisplayName("Una merma mayor al stock se rechaza en vez de dejarlo en negativo")
        void merma_mayorAlStock_rechaza() {
            when(productoRepository.findById(1L)).thenReturn(Optional.of(pelotas(3)));
            MovimientoStockRequest request = new MovimientoStockRequest();
            request.setCantidad(5);

            assertThatThrownBy(() -> productoService.registrarMerma(1L, request))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("quedan 3");
            verify(movimientoStockRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Nombre único")
    class NombreUnico {

        private ProductoRequest request(String nombre) {
            ProductoRequest request = new ProductoRequest();
            request.setNombre(nombre);
            request.setCategoria(CategoriaProducto.PELOTAS);
            request.setPrecioVenta(new BigDecimal("10000"));
            return request;
        }

        @Test
        @DisplayName("No se puede crear un producto con un nombre que ya existe, sin importar mayúsculas ni espacios")
        void crear_nombreRepetido_rechaza() {
            when(productoRepository.findByNombreIgnoreCase("Tubo de pelotas"))
                    .thenReturn(Optional.of(pelotas(5)));

            assertThatThrownBy(() -> productoService.crear(request(" Tubo de pelotas ")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("Ya hay un producto llamado");
        }

        @Test
        @DisplayName("Un nombre nuevo se puede crear sin problema")
        void crear_nombreLibre_permite() {
            when(productoRepository.findByNombreIgnoreCase("Grip")).thenReturn(Optional.empty());

            productoService.crear(request("Grip"));

            verify(productoRepository).save(any());
        }

        @Test
        @DisplayName("Al editar, el producto no choca contra su propio nombre")
        void actualizar_mismoNombre_noChoca() {
            Producto producto = pelotas(5);
            when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
            when(productoRepository.findByNombreIgnoreCase("Pelotas Head")).thenReturn(Optional.of(producto));

            productoService.actualizar(1L, request("Pelotas Head"));

            assertThat(producto.getNombre()).isEqualTo("Pelotas Head");
        }

        @Test
        @DisplayName("Al editar, sí choca contra el nombre de OTRO producto")
        void actualizar_nombreDeOtroProducto_rechaza() {
            Producto propio = pelotas(5);
            Producto otro = Producto.builder().id(2L).nombre("Grip").build();
            when(productoRepository.findById(1L)).thenReturn(Optional.of(propio));
            when(productoRepository.findByNombreIgnoreCase("Grip")).thenReturn(Optional.of(otro));

            assertThatThrownBy(() -> productoService.actualizar(1L, request("Grip")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("Ya hay un producto llamado");
        }
    }

    @Nested
    @DisplayName("Aviso de reposición")
    class Reposicion {

        @Test
        @DisplayName("Avisa cuando el stock llega al mínimo que fijó el club")
        void necesitaReposicion_enElMinimo() {
            assertThat(pelotas(3).necesitaReposicion()).isTrue();
            assertThat(pelotas(2).necesitaReposicion()).isTrue();
            assertThat(pelotas(4).necesitaReposicion()).isFalse();
        }

        @Test
        @DisplayName("Sin mínimo definido no avisa nunca")
        void sinMinimo_noAvisa() {
            Producto sinMinimo = pelotas(0);
            sinMinimo.setStockMinimo(0);
            assertThat(sinMinimo.necesitaReposicion()).isFalse();
        }

        @Test
        @DisplayName("Lo que no lleva stock no puede necesitar reposición")
        void sinControlDeStock_noAvisa() {
            Producto alquiler = pelotas(0);
            alquiler.setControlaStock(false);
            assertThat(alquiler.necesitaReposicion()).isFalse();
        }
    }
}
