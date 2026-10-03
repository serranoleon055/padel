package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.ConteoRequest;
import com.padel.rankpadel.dto.request.RecuentoRequest;
import com.padel.rankpadel.dto.response.PagedResponse;
import com.padel.rankpadel.dto.response.RecuentoResponse;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.entity.Recuento;
import com.padel.rankpadel.entity.RecuentoItem;
import com.padel.rankpadel.enums.EstadoRecuento;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.ProductoRepository;
import com.padel.rankpadel.repository.RecuentoRepository;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;

/**
 * El conteo físico del depósito.
 *
 * <p>Se abre una planilla con lo que el sistema cree que hay, se anota lo que hay de
 * verdad, y al aplicarla salen los ajustes. Hasta ese momento no se tocó una sola unidad:
 * contar y corregir son dos actos distintos, y entre uno y otro suele pasar que alguien
 * recuenta una caja.
 */
@Service
@RequiredArgsConstructor
public class RecuentoService {

    private static final DateTimeFormatter DIA_MES_ANIO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RecuentoRepository recuentoRepository;
    private final ProductoRepository productoRepository;
    private final ProductoService productoService;

    /**
     * Abre la planilla congelando el stock de cada producto.
     *
     * <p>Uno solo a la vez: dos planillas abiertas congelan cada una su propio
     * {@code stockSistema} y al aplicarlas la segunda vuelve a descontar lo que la primera
     * ya corrigió.
     */
    @Transactional
    public RecuentoResponse abrir(RecuentoRequest request) {
        recuentoRepository.findFirstByEstadoOrderByIdDesc(EstadoRecuento.BORRADOR)
                .ifPresent(abierto -> {
                    throw new EstadoInvalidoException("Ya hay un conteo abierto del "
                            + abierto.getFecha() + ". Aplicalo o descartalo antes de empezar otro.");
                });
        if (request.getFecha().isAfter(LocalDate.now())) {
            throw new EstadoInvalidoException("No se puede contar en una fecha futura.");
        }

        List<Producto> productos = productosDeLaPlanilla(request.getProductoIds());
        if (productos.isEmpty()) {
            throw new EstadoInvalidoException(
                    "No hay productos con control de stock para contar.");
        }

        Recuento recuento = Recuento.builder()
                .fecha(request.getFecha())
                .estado(EstadoRecuento.BORRADOR)
                .notas(request.getNotas())
                .creadoEn(LocalDateTime.now())
                .creadoPor(UsuarioActual.nombre())
                .items(new ArrayList<>())
                .build();

        for (Producto producto : productos) {
            recuento.getItems().add(RecuentoItem.builder()
                    .recuento(recuento)
                    .producto(producto)
                    .stockSistema(producto.getStock())
                    // El costo con el que se valúa el depósito es el promedio; el último
                    // costo mueve la pérdida de golpe con una compra chica a precio raro.
                    .costoUnitario(producto.getCostoPromedio() != null
                            ? producto.getCostoPromedio() : producto.getCosto())
                    .build());
        }

        return aResponse(recuentoRepository.save(recuento));
    }

    private List<Producto> productosDeLaPlanilla(List<Long> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            return productoRepository.findAll().stream()
                    .filter(Producto::isControlaStock)
                    .filter(Producto::isActivo)
                    .toList();
        }
        // Un producto sin control de stock no tiene unidades que contar, así que se
        // descarta en silencio aunque lo pidan: rechazar la planilla entera por eso sería
        // hacerle resolver al club una distinción que no hizo.
        return productoRepository.findAllById(productoIds).stream()
                .filter(Producto::isControlaStock)
                .toList();
    }

    /** Anota lo contado. No toca el stock. */
    @Transactional
    public RecuentoResponse contar(Long recuentoId, ConteoRequest request) {
        Recuento recuento = exigirBorrador(recuentoId);
        Map<Long, RecuentoItem> porId = recuento.getItems().stream()
                .collect(Collectors.toMap(RecuentoItem::getId, Function.identity()));

        for (ConteoRequest.Item pedido : request.getItems()) {
            RecuentoItem item = porId.get(pedido.getItemId());
            if (item == null) {
                throw new EstadoInvalidoException(
                        "El renglón " + pedido.getItemId() + " no es de este conteo.");
            }
            item.setStockContado(pedido.getStockContado());
        }
        return aResponse(recuentoRepository.save(recuento));
    }

    /**
     * Aplica el conteo: genera un ajuste por cada renglón con diferencia y congela lo que
     * faltó y lo que sobró.
     *
     * <p>Lo que se aplica es la **diferencia** contra el stock congelado al abrir, no el
     * total contado. Entre que se empieza a contar y se aplica siguen entrando ventas:
     * escribir el total contado como valor absoluto las borraría del inventario.
     *
     * <p>Los renglones sin contar se saltean. No contar algo no es contar cero, y asumir lo
     * contrario daría de baja todo lo que el club no llegó a mirar.
     *
     * <p>**El faltante no genera un gasto**, por el mismo criterio que la merma (V48): lo
     * que se perdió sale del inventario pero no del estado de resultados. El número queda
     * en la planilla para que el dueño lo vea.
     */
    @Transactional
    public RecuentoResponse aplicar(Long recuentoId) {
        Recuento recuento = exigirBorrador(recuentoId);

        BigDecimal faltante = BigDecimal.ZERO;
        BigDecimal sobrante = BigDecimal.ZERO;

        for (RecuentoItem item : recuento.getItems()) {
            if (item.getStockContado() == null) {
                continue;
            }
            int diferencia = item.diferencia();
            if (diferencia == 0) {
                continue;
            }

            productoService.aplicarDeRecuento(item.getProducto(), diferencia, recuento,
                    item.getCostoUnitario(),
                    // La fecha se escribe como la lee una persona: la nota del movimiento
                    // sale en el kardex, al lado de fechas ya formateadas.
                    "Conteo del " + recuento.getFecha().format(DIA_MES_ANIO)
                            + ": contadas " + item.getStockContado()
                            + ", el sistema decía " + item.getStockSistema());

            BigDecimal valor = valorizar(item);
            if (valor == null) {
                continue;
            }
            if (diferencia < 0) {
                faltante = faltante.add(valor.negate());
            } else {
                sobrante = sobrante.add(valor);
            }
        }

        recuento.setEstado(EstadoRecuento.APLICADO);
        recuento.setAplicadoEn(LocalDateTime.now());
        recuento.setAplicadoPor(UsuarioActual.nombre());
        recuento.setFaltanteValorizado(faltante);
        recuento.setSobranteValorizado(sobrante);
        return aResponse(recuentoRepository.save(recuento));
    }

    /** Tira la planilla sin aplicar nada. Solo mientras esté en borrador. */
    @Transactional
    public void descartar(Long recuentoId) {
        recuentoRepository.delete(exigirBorrador(recuentoId));
    }

    @Transactional(readOnly = true)
    public RecuentoResponse obtener(Long recuentoId) {
        return aResponse(buscar(recuentoId));
    }

    /** El conteo abierto, o null si no hay ninguno. */
    @Transactional(readOnly = true)
    public RecuentoResponse enCurso() {
        return recuentoRepository.findFirstByEstadoOrderByIdDesc(EstadoRecuento.BORRADOR)
                .map(this::aResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RecuentoResponse> listar(int pagina, int tamanio) {
        var page = recuentoRepository.buscar(PageRequest.of(pagina, tamanio));
        return PagedResponse.<RecuentoResponse>builder()
                .contenido(page.getContent().stream().map(this::aResumen).toList())
                .pagina(pagina)
                .tamanio(tamanio)
                .totalElementos(page.getTotalElements())
                .totalPaginas(page.getTotalPages())
                .esPrimera(page.isFirst())
                .esUltima(page.isLast())
                .build();
    }

    private Recuento buscar(Long id) {
        return recuentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recuento", id));
    }

    private Recuento exigirBorrador(Long id) {
        Recuento recuento = buscar(id);
        if (recuento.estaAplicado()) {
            throw new EstadoInvalidoException(
                    "Ese conteo ya se aplicó: los ajustes salieron y no se puede volver a tocar.");
        }
        return recuento;
    }

    /** Lo que vale la diferencia del renglón. Null si el producto no tiene costo cargado. */
    private BigDecimal valorizar(RecuentoItem item) {
        return item.getCostoUnitario() == null ? null
                : item.getCostoUnitario().multiply(BigDecimal.valueOf(item.diferencia()));
    }

    private RecuentoResponse aResponse(Recuento recuento) {
        RecuentoResponse response = aResumen(recuento);
        response.setItems(recuento.getItems().stream()
                .map(item -> RecuentoResponse.Item.builder()
                        .id(item.getId())
                        .productoId(item.getProducto().getId())
                        .productoNombre(item.getProducto().getNombre())
                        .categoria(item.getProducto().getCategoria() != null
                                ? item.getProducto().getCategoria().name() : null)
                        .stockSistema(item.getStockSistema())
                        .stockContado(item.getStockContado())
                        .diferencia(item.diferencia())
                        .costoUnitario(item.getCostoUnitario())
                        .valorizada(item.getStockContado() == null ? null : valorizar(item))
                        .build())
                .sorted((a, b) -> a.getProductoNombre().compareToIgnoreCase(b.getProductoNombre()))
                .toList());
        return response;
    }

    /** La cabecera, sin los renglones. Es lo que necesita el listado. */
    private RecuentoResponse aResumen(Recuento recuento) {
        List<RecuentoItem> items = recuento.getItems();
        // En borrador el faltante todavía no está congelado: se calcula sobre lo contado
        // hasta ahora, para que el club vea cuánto va perdiendo mientras cuenta.
        BigDecimal faltante = recuento.getFaltanteValorizado();
        BigDecimal sobrante = recuento.getSobranteValorizado();
        if (!recuento.estaAplicado()) {
            faltante = BigDecimal.ZERO;
            sobrante = BigDecimal.ZERO;
            for (RecuentoItem item : items) {
                BigDecimal valor = item.getStockContado() == null ? null : valorizar(item);
                if (valor == null || item.diferencia() == 0) {
                    continue;
                }
                if (item.diferencia() < 0) {
                    faltante = faltante.add(valor.negate());
                } else {
                    sobrante = sobrante.add(valor);
                }
            }
        }

        return RecuentoResponse.builder()
                .id(recuento.getId())
                .fecha(recuento.getFecha())
                .estado(recuento.getEstado() != null ? recuento.getEstado().name() : null)
                .notas(recuento.getNotas())
                .creadoEn(recuento.getCreadoEn())
                .creadoPor(recuento.getCreadoPor())
                .aplicadoEn(recuento.getAplicadoEn())
                .aplicadoPor(recuento.getAplicadoPor())
                .faltanteValorizado(faltante)
                .sobranteValorizado(sobrante)
                .contados((int) items.stream().filter(i -> i.getStockContado() != null).count())
                .total(items.size())
                .build();
    }
}
