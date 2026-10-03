package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.GastoRequest;
import com.padel.rankpadel.dto.request.MovimientoStockRequest;
import com.padel.rankpadel.dto.request.ProductoRequest;
import com.padel.rankpadel.dto.response.MovimientoStockResponse;
import com.padel.rankpadel.dto.response.PresentacionResponse;
import com.padel.rankpadel.dto.response.ProductoResponse;
import com.padel.rankpadel.entity.DocumentoCompra;
import com.padel.rankpadel.entity.MovimientoStock;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.entity.Proveedor;
import com.padel.rankpadel.enums.CategoriaGasto;
import com.padel.rankpadel.enums.MotivoMovimientoStock;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.MovimientoStockRepository;
import com.padel.rankpadel.repository.PresentacionProductoRepository;
import com.padel.rankpadel.repository.ProductoRepository;
import com.padel.rankpadel.repository.ProveedorRepository;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;

/**
 * Catálogo del mostrador y control de stock. Toda variación de unidades pasa por
 * {@link #registrarMovimiento}, así el stock del producto siempre coincide con la suma
 * de sus movimientos y un faltante se puede explicar.
 */
@Service
@RequiredArgsConstructor
public class ProductoService {

    private static final int MOVIMIENTOS_EN_LA_FICHA = 40;
    private static final int COMPRAS_EN_EL_HISTORIAL = 100;

    private final ProductoRepository productoRepository;
    private final ProveedorRepository proveedorRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final GastoService gastoService;
    private final PresentacionProductoRepository presentacionProductoRepository;
    private final PresentacionProductoService presentacionProductoService;

    /**
     * Dos productos con el mismo nombre confunden la venta (¿cuál de los dos?) y
     * ensucian "lo que más deja" del informe, que agrupa por producto y termina
     * mostrando la misma fila dos veces. {@code idAExcluir} es el propio producto al
     * editar: no puede chocar contra su propio nombre.
     */
    private void exigirNombreLibre(String nombre, Long idAExcluir) {
        productoRepository.findByNombreIgnoreCase(nombre)
                .filter(existente -> !existente.getId().equals(idAExcluir))
                .ifPresent(existente -> {
                    throw new EstadoInvalidoException("Ya hay un producto llamado \"" + existente.getNombre() + "\".");
                });
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(String busqueda, boolean soloActivos) {
        String texto = busqueda != null && !busqueda.isBlank() ? busqueda.trim() : null;
        List<Producto> productos = productoRepository.buscar(texto, soloActivos);
        if (productos.isEmpty()) {
            return List.of();
        }
        // Las presentaciones de todos los productos en una consulta. Pedirlas producto por
        // producto sería un N+1 en la pantalla que el mostrador abre en cada venta.
        Map<Long, List<PresentacionResponse>> porProducto = presentacionProductoRepository
                .findByProductoIdInAndActivoTrueOrderByOrdenAscIdAsc(
                        productos.stream().map(Producto::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(presentacion -> presentacion.getProducto().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(presentacionProductoService::aResponse, Collectors.toList())));

        return productos.stream()
                .map(producto -> {
                    ProductoResponse respuesta = aResponse(producto);
                    respuesta.setPresentaciones(porProducto.getOrDefault(producto.getId(), List.of()));
                    return respuesta;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> conStockBajo() {
        return productoRepository.conStockBajo().stream().map(this::aResponse).toList();
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        String nombre = request.getNombre().trim();
        exigirNombreLibre(nombre, null);
        Producto producto = Producto.builder()
                .nombre(nombre)
                .categoria(request.getCategoria())
                .precioVenta(request.getPrecioVenta())
                .costo(request.getCosto())
                // Con un solo costo conocido, el promedio ES ese costo. Sin esto un
                // producto recién creado mostraba "costo promedio: —" hasta la primera
                // compra, que para el club se lee como un dato que falta.
                .costoPromedio(request.getCosto())
                .controlaStock(request.isControlaStock())
                .stock(0)
                .stockMinimo(request.getStockMinimo() != null ? request.getStockMinimo() : 0)
                .proveedor(proveedor(request.getProveedorId()))
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build();
        productoRepository.save(producto);

        // Las unidades que ya estaban en la vitrina entran como un movimiento más: si no,
        // el stock arrancaría con un número que no tiene ningún respaldo.
        int inicial = request.getStockInicial() != null ? request.getStockInicial() : 0;
        if (producto.isControlaStock() && inicial > 0) {
            aplicarMovimiento(producto, inicial, MotivoMovimientoStock.AJUSTE, null, null,
                    "Stock inicial al dar de alta el producto");
        }
        return aResponse(producto);
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = buscar(id);
        String nombre = request.getNombre().trim();
        exigirNombreLibre(nombre, id);
        producto.setNombre(nombre);
        producto.setCategoria(request.getCategoria());
        producto.setPrecioVenta(request.getPrecioVenta());
        producto.setCosto(request.getCosto());
        // Si todavía no hay promedio (el producto nunca se compró por el sistema), el
        // costo que carga el club a mano ES el promedio. Si ya hay uno calculado con
        // compras reales, corregir el último costo NO lo pisa: el promedio sale de lo que
        // efectivamente se pagó.
        if (producto.getCostoPromedio() == null) {
            producto.setCostoPromedio(request.getCosto());
        }
        producto.setControlaStock(request.isControlaStock());
        producto.setStockMinimo(request.getStockMinimo() != null ? request.getStockMinimo() : 0);
        producto.setProveedor(proveedor(request.getProveedorId()));
        if (request.getActivo() != null) {
            producto.setActivo(request.getActivo());
        }
        productoRepository.save(producto);
        return aResponse(producto);
    }

    /** Baja lógica: el producto sigue apareciendo en las ventas ya hechas. */
    @Transactional
    public void darDeBaja(Long id) {
        Producto producto = buscar(id);
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    /**
     * Entrada de mercadería. Si viene el medio de pago, además registra el egreso: la
     * compra es plata que salió y tiene que pesar en la rentabilidad del mes.
     */
    @Transactional
    public ProductoResponse comprar(Long id, MovimientoStockRequest request) {
        Producto producto = buscar(id);
        exigirControlDeStock(producto, "comprar mercadería");
        // Sin el costo no se puede armar el egreso, y dejarlo pasar en silencio hacía que
        // la compra no apareciera nunca en los gastos del mes.
        if (request.getMedioPago() != null && request.getCostoUnitario() == null) {
            throw new EstadoInvalidoException(
                    "Para registrar el pago de la compra hace falta el costo por unidad.");
        }

        // El movimiento se fecha cuando entró la mercadería, no cuando se cargó: una
        // compra cargada con fecha vieja quedaba en el kardex como entrada de hoy y en la
        // rentabilidad de otro mes, así que el stock y la plata contaban historias
        // distintas de la misma compra.
        LocalDateTime cuando = instanteDeLaCompra(request.getFecha());

        // El egreso primero: si la caja de la jornada está cerrada o la fecha es futura,
        // la compra no entra. Antes el Gasto se armaba acá a mano y por eso se salteaba
        // los dos controles que /api/gastos sí aplica.
        if (request.getMedioPago() != null && request.getCostoUnitario() != null) {
            registrarGastoDeCompra(producto, request);
        }

        ingresarMercaderia(producto, request.getCantidad(), request.getCostoUnitario(),
                cuando, null, request.getNotas());

        return aResponse(producto);
    }

    /**
     * Lo que sale en promedio cada unidad después de esta compra, ponderando por las que
     * había y las que entran.
     *
     * <p>Con el último costo, una compra chica a precio raro movía de golpe el capital en
     * stock y el margen de todo lo que ya estaba en la heladera.
     *
     * <p>Si no había stock (o quedó en negativo por un ajuste) o si nunca hubo promedio,
     * el promedio ES el costo de esta compra: no hay nada viejo que ponderar.
     */
    private BigDecimal promedioPonderado(Producto producto, int cantidadQueEntra,
            BigDecimal costoDeLaCompra) {
        int stockPrevio = producto.getStock();
        BigDecimal promedioPrevio = producto.getCostoPromedio();
        if (stockPrevio <= 0 || promedioPrevio == null || cantidadQueEntra <= 0) {
            return costoDeLaCompra;
        }
        BigDecimal valorPrevio = promedioPrevio.multiply(BigDecimal.valueOf(stockPrevio));
        BigDecimal valorQueEntra = costoDeLaCompra.multiply(BigDecimal.valueOf(cantidadQueEntra));
        return valorPrevio.add(valorQueEntra)
                .divide(BigDecimal.valueOf(stockPrevio + (long) cantidadQueEntra), 2, RoundingMode.HALF_UP);
    }

    /**
     * Si la compra es de hoy vale el reloj; si se carga con fecha vieja, el movimiento se
     * asienta al arranque de ese día, que es lo que el kardex tiene que mostrar.
     */
    private LocalDateTime instanteDeLaCompra(LocalDate fecha) {
        return fecha == null || fecha.isEqual(LocalDate.now())
                ? LocalDateTime.now()
                : fecha.atStartOfDay();
    }

    /**
     * Corrección tras contar la vitrina: el club dice cuántas unidades hay de verdad y el
     * sistema guarda la diferencia. Contar es más confiable que sumar y restar a mano.
     */
    @Transactional
    public ProductoResponse ajustar(Long id, int stockReal, String notas) {
        Producto producto = buscar(id);
        exigirControlDeStock(producto, "ajustar el stock");
        if (stockReal < 0) {
            throw new EstadoInvalidoException("El stock no puede ser negativo");
        }
        int diferencia = stockReal - producto.getStock();
        if (diferencia != 0) {
            aplicarMovimiento(producto, diferencia, MotivoMovimientoStock.AJUSTE, null, null, notas);
        }
        return aResponse(producto);
    }

    /** Mercadería que se rompió, se venció o se perdió. */
    @Transactional
    public ProductoResponse registrarMerma(Long id, MovimientoStockRequest request) {
        Producto producto = buscar(id);
        exigirControlDeStock(producto, "registrar una merma");
        // Dar de baja más unidades de las que hay dejaría el stock en negativo, y a partir
        // de ahí el número no se puede explicar con ningún movimiento.
        if (producto.getStock() < request.getCantidad()) {
            throw new EstadoInvalidoException("No podés dar de baja " + request.getCantidad()
                    + " unidades de \"" + producto.getNombre() + "\": quedan "
                    + producto.getStock() + ". Si el conteo no coincide, usá el ajuste de stock.");
        }
        aplicarMovimiento(producto, -request.getCantidad(), MotivoMovimientoStock.MERMA,
                null, null, request.getNotas());
        return aResponse(producto);
    }

    /**
     * Entrada de mercadería: recalcula el costo promedio, actualiza el último costo y
     * asienta el movimiento.
     *
     * <p>Es el único camino por donde entra stock comprado, lo use la compra de un
     * producto suelto o una compra formal con varios renglones. Si cada uno hiciera su
     * parte, el promedio ponderado terminaría calculado de dos maneras.
     *
     * @param compra la compra formal que lo trajo, o null si es una compra suelta
     */
    @Transactional
    public void ingresarMercaderia(Producto producto, int cantidad, BigDecimal costoUnitario,
            LocalDateTime cuando, DocumentoCompra compra, String notas) {
        exigirControlDeStock(producto, "comprar mercadería");
        if (costoUnitario != null) {
            // El promedio se recalcula ANTES de sumar las unidades: necesita saber cuántas
            // había.
            producto.setCostoPromedio(promedioPonderado(producto, cantidad, costoUnitario));
            producto.setCosto(costoUnitario);
            productoRepository.save(producto);
        }
        aplicarMovimiento(producto, cantidad, MotivoMovimientoStock.COMPRA, null,
                costoUnitario, notas, cuando, compra);
    }

    /**
     * Deshace la entrada de una compra anulada. No borra el movimiento original: emite
     * uno compensatorio, porque el stock tiene que seguir siendo la suma de sus
     * movimientos y un renglón que desaparece deja un faltante sin explicación.
     *
     * <p>El costo promedio NO se recalcula hacia atrás: ya se usó para congelar el costo
     * de lo que se vendió en el medio, y rehacerlo cambiaría márgenes ya cerrados. El
     * promedio se corrige solo con la próxima compra.
     */
    @Transactional
    public void revertirIngreso(Producto producto, int cantidad, DocumentoCompra compra, String notas) {
        aplicarMovimiento(producto, -cantidad, MotivoMovimientoStock.ANULACION_COMPRA, null,
                null, notas, LocalDateTime.now(), compra);
    }

    @Transactional(readOnly = true)
    public List<MovimientoStockResponse> movimientos(Long productoId) {
        return movimientoStockRepository
                .findDelProducto(productoId, PageRequest.of(0, MOVIMIENTOS_EN_LA_FICHA)).stream()
                .map(this::aResponse)
                .toList();
    }

    /** Historial de compras del club: qué entró, cuándo, de quién y a cuánto. */
    @Transactional(readOnly = true)
    public List<MovimientoStockResponse> compras() {
        return movimientoStockRepository.findCompras(PageRequest.of(0, COMPRAS_EN_EL_HISTORIAL)).stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * Único punto por donde cambia el stock. Lo usan también las ventas y sus anulaciones.
     *
     * @param cantidad positiva si entra mercadería, negativa si sale
     */
    @Transactional
    public void aplicarMovimiento(Producto producto, int cantidad, MotivoMovimientoStock motivo,
            com.padel.rankpadel.entity.Venta venta, BigDecimal costoUnitario, String notas) {
        aplicarMovimiento(producto, cantidad, motivo, venta, costoUnitario, notas, LocalDateTime.now());
    }

    /**
     * @param cuando instante con el que se asienta el movimiento. Lo normal es el reloj;
     *               una compra cargada con fecha vieja lo asienta en esa fecha, para que
     *               el kardex y el egreso cuenten la misma historia.
     */
    @Transactional
    public void aplicarMovimiento(Producto producto, int cantidad, MotivoMovimientoStock motivo,
            com.padel.rankpadel.entity.Venta venta, BigDecimal costoUnitario, String notas,
            LocalDateTime cuando) {
        aplicarMovimiento(producto, cantidad, motivo, venta, costoUnitario, notas, cuando, null);
    }

    /** @param compra la compra formal que lo trajo, para poder revertirla entera */
    @Transactional
    public void aplicarMovimiento(Producto producto, int cantidad, MotivoMovimientoStock motivo,
            com.padel.rankpadel.entity.Venta venta, BigDecimal costoUnitario, String notas,
            LocalDateTime cuando, DocumentoCompra compra) {
        if (!producto.isControlaStock()) {
            return;
        }
        producto.setStock(producto.getStock() + cantidad);
        productoRepository.save(producto);

        movimientoStockRepository.save(MovimientoStock.builder()
                .producto(producto)
                .cantidad(cantidad)
                .motivo(motivo)
                .fecha(cuando)
                .venta(venta)
                .documentoCompra(compra)
                .costoUnitario(costoUnitario)
                .registradoPor(UsuarioActual.nombre())
                .notas(notas)
                .build());
    }

    @Transactional(readOnly = true)
    public Producto buscar(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    /**
     * El egreso de una compra de mercadería. Pasa por {@code GastoService} y no se arma
     * acá: así hereda los controles de fecha no futura y de caja cerrada, que es lo que
     * se salteaba cuando esta clase insertaba el {@code Gasto} por su cuenta y permitía
     * meter plata en una jornada con el arqueo ya firmado.
     */
    private void registrarGastoDeCompra(Producto producto, MovimientoStockRequest request) {
        BigDecimal total = request.getCostoUnitario().multiply(BigDecimal.valueOf(request.getCantidad()));
        GastoRequest pedido = new GastoRequest();
        pedido.setFecha(request.getFecha() != null ? request.getFecha() : LocalDate.now());
        pedido.setCategoria(CategoriaGasto.INSUMOS);
        pedido.setDescripcion("Compra de " + request.getCantidad() + " x " + producto.getNombre());
        pedido.setMonto(total);
        pedido.setMedio(request.getMedioPago());
        pedido.setProveedor(producto.getProveedor() != null ? producto.getProveedor().getNombre() : null);
        pedido.setNotas(request.getNotas());
        gastoService.registrarEntidad(pedido, producto, true);
    }

    private void exigirControlDeStock(Producto producto, String accion) {
        if (!producto.isControlaStock()) {
            throw new EstadoInvalidoException(
                    "\"" + producto.getNombre() + "\" no lleva control de stock, así que no hace falta " + accion + ".");
        }
    }

    private Proveedor proveedor(Long proveedorId) {
        if (proveedorId == null) {
            return null;
        }
        return proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", proveedorId));
    }

    ProductoResponse aResponse(Producto producto) {
        Proveedor proveedor = producto.getProveedor();
        return ProductoResponse.builder()
                .id(producto.getId())
                .nombre(producto.getNombre())
                .categoria(producto.getCategoria() != null ? producto.getCategoria().name() : null)
                .precioVenta(producto.getPrecioVenta())
                .costo(producto.getCosto())
                .costoPromedio(producto.getCostoPromedio())
                .margenUnitario(producto.margenUnitario())
                .controlaStock(producto.isControlaStock())
                .stock(producto.getStock())
                .stockMinimo(producto.getStockMinimo())
                .necesitaReposicion(producto.necesitaReposicion())
                .proveedorId(proveedor != null ? proveedor.getId() : null)
                .proveedorNombre(proveedor != null ? proveedor.getNombre() : null)
                .activo(producto.isActivo())
                .build();
    }

    private MovimientoStockResponse aResponse(MovimientoStock movimiento) {
        Producto producto = movimiento.getProducto();
        return MovimientoStockResponse.builder()
                .id(movimiento.getId())
                .productoId(producto != null ? producto.getId() : null)
                .productoNombre(producto != null ? producto.getNombre() : null)
                .cantidad(movimiento.getCantidad())
                .motivo(movimiento.getMotivo() != null ? movimiento.getMotivo().name() : null)
                .fecha(movimiento.getFecha())
                .costoUnitario(movimiento.getCostoUnitario())
                .registradoPor(movimiento.getRegistradoPor())
                .notas(movimiento.getNotas())
                .build();
    }
}
