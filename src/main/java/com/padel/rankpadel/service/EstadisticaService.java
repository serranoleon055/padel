package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.EstadisticasResponse;
import com.padel.rankpadel.dto.response.EstadisticasResponse.CanchaUso;
import com.padel.rankpadel.dto.response.EstadisticasResponse.CategoriaDemanda;
import com.padel.rankpadel.dto.response.EstadisticasResponse.CupoCategoria;
import com.padel.rankpadel.dto.response.EstadisticasResponse.ClienteTop;
import com.padel.rankpadel.dto.response.EstadisticasResponse.Kiosco;
import com.padel.rankpadel.dto.response.EstadisticasResponse.OcupacionCancha;
import com.padel.rankpadel.dto.response.EstadisticasResponse.ProductoRendimiento;
import com.padel.rankpadel.dto.response.EstadisticasResponse.ResumenMes;
import com.padel.rankpadel.dto.response.EstadisticasResponse.EmbudoTorneo;
import com.padel.rankpadel.dto.response.EstadisticasResponse.IngresoMes;
import com.padel.rankpadel.dto.response.EstadisticasResponse.OcupacionFranja;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.util.OrdenJornada;
import com.padel.rankpadel.entity.SolicitudInscripcion;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.enums.EstadoSolicitud;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.GastoRepository;
import com.padel.rankpadel.repository.HorarioCanchaRepository;
import com.padel.rankpadel.repository.ProductoRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.SolicitudInscripcionRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.repository.VentaRepository;
import com.padel.rankpadel.util.MontosReserva;

import lombok.RequiredArgsConstructor;

/**
 * Estadísticas de negocio del club.
 *
 * <p><b>Alcance del filtro por sede.</b> El filtro se aplica a lo que pertenece a una
 * cancha —turnos, ocupación, torneos—, pero NO al kiosco, los gastos ni la rentabilidad:
 * la caja del club es una sola. Es una decisión, no un olvido. Un club con dos sedes
 * comparte proveedor, depósito y cajón, y partir el stock por sede obligaría a mover
 * mercadería entre sedes en el sistema para algo que en la realidad es la misma heladera.
 * Si algún día un cliente necesita cajas separadas, hay que colgar {@code Producto},
 * {@code Venta} y {@code Gasto} de un lugar, y las pantallas tienen que avisarlo.
 */
@Service
@RequiredArgsConstructor
public class EstadisticaService {

    private final ReservaRepository reservaRepository;
    private final TorneoRepository torneoRepository;
    private final ParejaRepository parejaRepository;
    private final SolicitudInscripcionRepository solicitudInscripcionRepository;
    private final GastoRepository gastoRepository;
    private final VentaRepository ventaRepository;
    private final CanchaRepository canchaRepository;
    private final HorarioCanchaRepository horarioCanchaRepository;
    private final ProductoRepository productoRepository;
    private final CobroRepository cobroRepository;

    // Un NO_SHOW ocupó la cancha igual (nadie más pudo usar ese horario), así que cuenta
    // para el mapa de ocupación. Lo que NO cuenta es la facturación completa: ver
    // ingresoReserva(), donde solo se computa la seña que quedó cobrada.
    private static final List<EstadoReserva> OCUPAN = List.of(
            EstadoReserva.CONFIRMADA, EstadoReserva.FINALIZADA, EstadoReserva.NO_SHOW);
    private static final List<EstadoReserva> LIBERADAS = List.of(
            EstadoReserva.CANCELADA, EstadoReserva.RECHAZADA, EstadoReserva.EXPIRADA);
    private static final int TOP_PRODUCTOS = 8;
    private static final int TOP_CLIENTES = 8;

    /**
     * Cuántos meses mira el panel, contando el actual. Estaba escrito a mano en tres
     * lugares distintos y había que acordarse de cambiar los tres.
     */
    private static final int MESES_DEL_PANEL = 6;

    /**
     * Una solicitud de inscripción tiene exactamente dos integrantes, y los dos pagan.
     * Está acá y no escrito en el cálculo para que se vea que es un supuesto del modelo
     * (la pareja de pádel) y no una constante mágica.
     */
    private static final int INTEGRANTES_POR_SOLICITUD = 2;
    private static final List<EstadoTorneo> TORNEOS_ABIERTOS = List.of(
            EstadoTorneo.INSCRIPCION, EstadoTorneo.SORTEADO, EstadoTorneo.EN_CURSO);

    /** Con la ventana por defecto de seis meses. */
    @Transactional(readOnly = true)
    public EstadisticasResponse obtener(Long lugarId) {
        return obtener(lugarId, null);
    }

    /**
     * @param meses cuántos meses mira el panel, contando el actual. Null usa el default.
     *              Se acota entre 1 y 24: menos de uno no es un período y más de dos años
     *              es traer medio historial para un gráfico de barras.
     */
    @Transactional(readOnly = true)
    public EstadisticasResponse obtener(Long lugarId, Integer meses) {
        int ventana = meses == null ? MESES_DEL_PANEL : Math.clamp(meses, 1, 24);
        LocalDate hoy = LocalDate.now();
        LocalDate desde = YearMonth.from(hoy).minusMonths(ventana - 1L).atDay(1);

        // El filtro de sede va en el WHERE. Antes se traían las reservas de TODAS las
        // sedes y se descartaban en Java: en un club con dos sedes, la mitad del semestre
        // viajaba de la base para nada.
        List<Reserva> reservas = reservaRepository.findParaEstadisticas(desde, hoy, lugarId);

        List<Reserva> ocupadas = reservas.stream()
                .filter(reserva -> OCUPAN.contains(reserva.getEstado()) && reserva.getHoraInicio() != null && reserva.getFecha() != null)
                .toList();

        // Las canchas y sus horarios, una sola vez. Los tres cálculos que los necesitan
        // —la apertura, las horas abiertas del mes y la ocupación por cancha— preguntaban
        // cada uno por su cuenta, cancha por cancha: con seis canchas eran dieciocho
        // consultas por request para leer siempre lo mismo.
        CalendarioClub calendario = cargarCalendario(lugarId);
        int horaApertura = calendario.horaApertura();
        List<OcupacionFranja> heatmap = calcularHeatmap(ocupadas, horaApertura);
        List<CanchaUso> canchasMasUsadas = calcularCanchasMasUsadas(ocupadas);

        // Solo las aprobadas de la ventana del panel y de la sede elegida, con el torneo y
        // la categoría en el mismo viaje. Antes era un findAll() sobre el historial
        // completo de inscripciones —que crece para siempre— más una consulta por fila al
        // leer el torneo, que es LAZY.
        List<SolicitudInscripcion> solicitudes = solicitudInscripcionRepository.findAprobadasDesde(
                EstadoSolicitud.APROBADA, desde.atStartOfDay(), lugarId);

        List<Gasto> gastos = gastoRepository.findEntreFechas(desde, hoy);
        List<IngresoMes> ingresosPorMes = calcularIngresosPorMes(hoy, ventana, ocupadas, solicitudes, gastos);

        long reservasTotales = reservas.size();
        long reservasCanceladas = reservas.stream()
                .filter(reserva -> LIBERADAS.contains(reserva.getEstado()))
                .count();
        double tasaCancelacion = reservasTotales > 0 ? (double) reservasCanceladas / reservasTotales : 0d;

        // El no-show se mide aparte: no es una cancelación (nadie avisó) y es la métrica
        // que decide si conviene endurecer la política de seña.
        long reservasNoShow = reservas.stream()
                .filter(reserva -> reserva.getEstado() == EstadoReserva.NO_SHOW)
                .count();
        long turnosQueDebieronJugarse = reservas.stream()
                .filter(reserva -> OCUPAN.contains(reserva.getEstado()))
                .count();
        double tasaNoShow = turnosQueDebieronJugarse > 0
                ? (double) reservasNoShow / turnosQueDebieronJugarse
                : 0d;

        List<EmbudoTorneo> embudoTorneos = calcularEmbudo(lugarId);
        List<CategoriaDemanda> categoriasDemandadas = calcularCategoriasDemandadas(solicitudes);

        List<OcupacionCancha> ocupacionPorCancha = calcularOcupacionPorCancha(desde, hoy, ocupadas, calendario);
        long horasAbiertasDelMes = ocupacionPorCancha.isEmpty()
                ? 0
                : horasAbiertasDelMes(hoy, calendario);
        List<ProductoRendimiento> rendimiento = calcularRendimientoProductos(desde, hoy);

        return EstadisticasResponse.builder()
                .mesActual(calcularResumenMes(hoy, reservas, ingresosPorMes, horasAbiertasDelMes))
                .ocupacionPorCancha(ocupacionPorCancha)
                .rendimientoProductos(rendimiento.stream().limit(TOP_PRODUCTOS).toList())
                .mejoresClientes(calcularMejoresClientes(ocupadas))
                .kiosco(calcularKiosco(rendimiento))
                .heatmap(heatmap)
                .horaApertura(horaApertura)
                .mesesDelPanel(ventana)
                .canchasMasUsadas(canchasMasUsadas)
                .ingresosPorMes(ingresosPorMes)
                .reservasTotales(reservasTotales)
                .reservasCanceladas(reservasCanceladas)
                .tasaCancelacion(tasaCancelacion)
                .reservasNoShow(reservasNoShow)
                .tasaNoShow(tasaNoShow)
                .embudoTorneos(embudoTorneos)
                .categoriasDemandadas(categoriasDemandadas)
                .build();
    }

    private List<OcupacionFranja> calcularHeatmap(List<Reserva> ocupadas, int horaApertura) {
        Map<String, Long> conteo = new HashMap<>();
        for (Reserva reserva : ocupadas) {
            int dia = reserva.getFecha().getDayOfWeek().getValue();
            int hora = reserva.getHoraInicio().getHour();
            conteo.merge(dia + "|" + hora, 1L, Long::sum);
        }
        // Dentro de cada día, las horas van en orden de jornada: las de la madrugada son
        // el cierre de esa noche y tienen que quedar al final, no arriba de todo.
        Comparator<OcupacionFranja> porHoraDeJornada = Comparator.comparingInt(
                franja -> OrdenJornada.minutosDesdeApertura(
                        LocalTime.of(franja.getHora(), 0), LocalTime.of(horaApertura, 0)));
        return conteo.entrySet().stream()
                .map(entrada -> {
                    String[] partes = entrada.getKey().split("\\|");
                    return OcupacionFranja.builder()
                            .diaSemana(Integer.parseInt(partes[0]))
                            .hora(Integer.parseInt(partes[1]))
                            .cantidad(entrada.getValue())
                            .build();
                })
                .sorted(Comparator.comparingInt(OcupacionFranja::getDiaSemana).thenComparing(porHoraDeJornada))
                .toList();
    }

    /** Las canchas de la sede y sus bandas de horario, en dos consultas y no en veinte. */
    private CalendarioClub cargarCalendario(Long lugarId) {
        List<Cancha> canchas = lugarId != null
                ? canchaRepository.findByLugarIdAndActivoTrue(lugarId)
                : canchaRepository.findByActivoTrue();
        if (canchas.isEmpty()) {
            return CalendarioClub.de(List.of(), List.of());
        }
        return CalendarioClub.de(canchas, horarioCanchaRepository
                .findByCanchaIdInAndActivoTrue(canchas.stream().map(Cancha::getId).toList()));
    }

    /**
     * El resumen que el dueño mira primero: cuánto entró este mes, si es más o menos que
     * el anterior, cuánto deja cada turno y qué porcentaje de la cancha se está vendiendo.
     * La ocupación es la métrica que decide si conviene bajar el precio de las horas
     * flojas o subir el de las llenas.
     */
    private ResumenMes calcularResumenMes(LocalDate hoy, List<Reserva> reservas, List<IngresoMes> ingresos,
            long horasAbiertasDelMes) {
        YearMonth mes = YearMonth.from(hoy);
        IngresoMes actual = buscarMes(ingresos, mes);
        IngresoMes anterior = buscarMes(ingresos, mes.minusMonths(1));

        BigDecimal facturado = totalDe(actual);
        BigDecimal facturadoAnterior = totalDe(anterior);

        List<Reserva> jugadosDelMes = reservas.stream()
                .filter(reserva -> OCUPAN.contains(reserva.getEstado())
                        && YearMonth.from(reserva.getFecha()).equals(mes))
                .toList();
        long turnos = jugadosDelMes.size();
        long minutosVendidos = jugadosDelMes.stream().mapToLong(Reserva::getDuracionMin).sum();

        BigDecimal facturadoTurnos = actual != null ? actual.getTurnos() : BigDecimal.ZERO;
        BigDecimal ticket = turnos > 0
                ? facturadoTurnos.divide(BigDecimal.valueOf(turnos), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        double ocupacion = horasAbiertasDelMes > 0
                ? (double) minutosVendidos / (horasAbiertasDelMes * 60)
                : 0d;
        BigDecimal porHora = horasAbiertasDelMes > 0
                ? facturadoTurnos.divide(BigDecimal.valueOf(horasAbiertasDelMes), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return ResumenMes.builder()
                .mes(mes.toString())
                .facturado(facturado)
                .facturadoMesAnterior(facturadoAnterior)
                .variacion(variacion(facturado, facturadoAnterior))
                .resultado(actual != null ? actual.getResultado() : BigDecimal.ZERO)
                .turnosJugados(turnos)
                .ticketPromedioTurno(ticket)
                .ocupacion(ocupacion)
                .ingresoPorHoraAbierta(porHora)
                .deudaAcumulada(calcularDeuda(jugadosDelMes))
                .build();
    }

    /** Horas de cancha que el club tuvo a la venta este mes, sumando todas las canchas. */
    private long horasAbiertasDelMes(LocalDate hoy, CalendarioClub calendario) {
        return calendario.horasAbiertasDeTodas(YearMonth.from(hoy).atDay(1), hoy);
    }

    private IngresoMes buscarMes(List<IngresoMes> ingresos, YearMonth mes) {
        return ingresos.stream()
                .filter(ingreso -> mes.toString().equals(ingreso.getMes()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal totalDe(IngresoMes mes) {
        if (mes == null) {
            return BigDecimal.ZERO;
        }
        return mes.getTurnos().add(mes.getInscripciones()).add(mes.getVentas());
    }

    /** Null cuando el mes anterior fue cero: dividir por cero daría un "+∞%" sin sentido. */
    private Double variacion(BigDecimal actual, BigDecimal anterior) {
        if (anterior.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return actual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Plata de turnos ya jugados que sigue sin cobrarse. */
    private BigDecimal calcularDeuda(List<Reserva> jugados) {
        if (jugados.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> ids = jugados.stream().map(Reserva::getId).toList();
        Map<Long, BigDecimal> cobrado = new HashMap<>();
        for (CobroRepository.TotalPorReserva total : cobroRepository.totalesPorReserva(ids)) {
            cobrado.put(total.getReservaId(), total.getTotal());
        }
        Map<Long, BigDecimal> consumo = new HashMap<>();
        for (VentaRepository.ConsumoPorReserva total : ventaRepository.consumoACuentaDe(ids)) {
            consumo.put(total.getReservaId(), total.getTotal());
        }
        return jugados.stream()
                .map(reserva -> MontosReserva.saldo(
                        reserva, cobrado.get(reserva.getId()), consumo.get(reserva.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Ocupación por cancha en el período. El denominador son las horas que el club tuvo
     * esa cancha abierta, no el día entero: una cancha que abre 5 horas y vende 4 está
     * al 80%, no al 17%.
     */
    private List<OcupacionCancha> calcularOcupacionPorCancha(LocalDate desde, LocalDate hasta,
            List<Reserva> ocupadas, CalendarioClub calendario) {
        Map<Long, long[]> minutosPorCancha = new HashMap<>();
        Map<Long, BigDecimal> facturadoPorCancha = new HashMap<>();
        for (Reserva reserva : ocupadas) {
            if (reserva.getCancha() == null) {
                continue;
            }
            Long id = reserva.getCancha().getId();
            minutosPorCancha.computeIfAbsent(id, k -> new long[1])[0] += reserva.getDuracionMin();
            facturadoPorCancha.merge(id, ingresoReserva(reserva), BigDecimal::add);
        }

        return calendario.canchas().stream()
                .map(cancha -> {
                    long minutos = minutosPorCancha.containsKey(cancha.getId())
                            ? minutosPorCancha.get(cancha.getId())[0] : 0L;
                    long horasAbiertas = calendario.horasAbiertas(cancha.getId(), desde, hasta);
                    return OcupacionCancha.builder()
                            .canchaNombre(cancha.getNombre())
                            .horasVendidas(minutos / 60)
                            .horasDisponibles(horasAbiertas)
                            .ocupacion(horasAbiertas > 0 ? (double) minutos / (horasAbiertas * 60) : 0d)
                            .facturado(facturadoPorCancha.getOrDefault(cancha.getId(), BigDecimal.ZERO))
                            .build();
                })
                .sorted(Comparator.comparingDouble(OcupacionCancha::getOcupacion).reversed())
                .toList();
    }


    /**
     * Qué conviene tener en la heladera. Ordenado por ganancia, no por facturación: un
     * producto que vende mucho con margen chico puede dejar menos que uno que vende poco.
     */
    private List<ProductoRendimiento> calcularRendimientoProductos(LocalDate desde, LocalDate hasta) {
        return ventaRepository.rankingProductos(desde, hasta).stream()
                .map(fila -> ProductoRendimiento.builder()
                        .productoId(fila.getProductoId())
                        .nombre(fila.getNombre())
                        .unidades(fila.getUnidades())
                        .facturado(fila.getFacturado())
                        .ganancia(fila.getGanancia())
                        .unidadesSinCosto(fila.getUnidadesSinCosto())
                        // El margen se mide sobre lo que tiene costo cargado. Dividir por
                        // la facturación total daría un margen diluido por los renglones
                        // cuyo costo el club todavía no cargó, y eso no es un margen bajo:
                        // es un dato que falta. Si no hay NADA con costo, viaja null y la
                        // pantalla lo dice.
                        .margen(fila.getFacturadoConCosto() != null
                                && fila.getFacturadoConCosto().compareTo(BigDecimal.ZERO) > 0
                                        ? fila.getGanancia()
                                                .divide(fila.getFacturadoConCosto(), 4, RoundingMode.HALF_UP)
                                                .doubleValue()
                                        : null)
                        .build())
                .sorted(Comparator.comparing(ProductoRendimiento::getGanancia).reversed())
                .toList();
    }

    /**
     * Estado del kiosco: lo que deja y lo que hay parado en el depósito. Recibe el ranking
     * COMPLETO, no el recorte que se muestra en pantalla: sumar solo los más vendidos
     * dejaba afuera la facturación del resto del catálogo.
     */
    private Kiosco calcularKiosco(List<ProductoRendimiento> rendimiento) {
        // Dos agregados en la base. Antes se traía el catálogo entero para multiplicar y
        // sumar en Java, y las filas de stock bajo solo para contarlas.
        BigDecimal capital = productoRepository.capitalEnStock();
        long bajoMinimo = productoRepository.contarConStockBajo();
        return Kiosco.builder()
                .facturado(rendimiento.stream().map(ProductoRendimiento::getFacturado)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .ganancia(rendimiento.stream().map(ProductoRendimiento::getGanancia)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .capitalEnStock(capital)
                .productosBajoMinimo(bajoMinimo)
                .build();
    }

    /** Los clientes que más dejaron. Sirve para saber a quién cuidar. */
    private List<ClienteTop> calcularMejoresClientes(List<Reserva> ocupadas) {
        Map<Long, ClienteTop> porCliente = new HashMap<>();
        for (Reserva reserva : ocupadas) {
            if (reserva.getCliente() == null) {
                continue;
            }
            ClienteTop actual = porCliente.get(reserva.getCliente().getId());
            BigDecimal gastado = ingresoReserva(reserva);
            if (actual == null) {
                porCliente.put(reserva.getCliente().getId(), ClienteTop.builder()
                        .clienteId(reserva.getCliente().getId())
                        .nombre(reserva.getCliente().getNombre())
                        .turnos(1)
                        .gastado(gastado)
                        .build());
            } else {
                actual.setTurnos(actual.getTurnos() + 1);
                actual.setGastado(actual.getGastado().add(gastado));
            }
        }
        return porCliente.values().stream()
                .sorted(Comparator.comparing(ClienteTop::getGastado).reversed())
                .limit(TOP_CLIENTES)
                .toList();
    }

    private List<CanchaUso> calcularCanchasMasUsadas(List<Reserva> ocupadas) {
        Map<String, Long> porCancha = ocupadas.stream()
                .filter(reserva -> reserva.getCancha() != null && reserva.getCancha().getNombre() != null)
                .collect(Collectors.groupingBy(reserva -> reserva.getCancha().getNombre(), Collectors.counting()));
        return porCancha.entrySet().stream()
                .map(entrada -> CanchaUso.builder().canchaNombre(entrada.getKey()).reservas(entrada.getValue()).build())
                .sorted(Comparator.comparingLong(CanchaUso::getReservas).reversed())
                .toList();
    }

    private List<IngresoMes> calcularIngresosPorMes(LocalDate hoy, int ventana, List<Reserva> ocupadas,
            List<SolicitudInscripcion> solicitudes, List<Gasto> gastos) {
        List<IngresoMes> ingresos = new ArrayList<>();
        YearMonth actual = YearMonth.from(hoy);

        // Una sola consulta agrupada para los seis meses: recorrer las ventas en Java
        // sería traer todos los renglones del semestre para sumarlos.
        LocalDate desdeVentas = actual.minusMonths(ventana - 1L).atDay(1);
        Map<String, BigDecimal> ventasPorMes = new HashMap<>();
        for (VentaRepository.TotalPorMes fila : ventaRepository.totalPorMes(desdeVentas)) {
            ventasPorMes.put(fila.getMes(), fila.getTotal());
        }
        Map<String, BigDecimal> costoPorMes = new HashMap<>();
        for (VentaRepository.TotalPorMes fila : ventaRepository.costoMercaderiaVendidaPorMes(desdeVentas)) {
            costoPorMes.put(fila.getMes(), fila.getTotal());
        }

        for (int i = ventana - 1; i >= 0; i--) {
            YearMonth mes = actual.minusMonths(i);
            BigDecimal turnos = ocupadas.stream()
                    .filter(reserva -> YearMonth.from(reserva.getFecha()).equals(mes))
                    .map(this::ingresoReserva)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal inscripciones = solicitudes.stream()
                    .filter(solicitud -> solicitud.getEstado() == EstadoSolicitud.APROBADA
                            && solicitud.getCreadoEn() != null
                            && YearMonth.from(solicitud.getCreadoEn()).equals(mes))
                    .map(this::ingresoSolicitud)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // Los gastos se parten en dos: la compra de mercadería es inventario (queda en
            // el stock hasta venderse) y el resto son gastos operativos del mes. Meter la
            // compra en los gastos Y restar el costo de lo vendido contaría dos veces la
            // misma plata.
            //
            // El discriminador es el flag `esMercaderia` y no `Gasto.producto`: una compra
            // con varios productos en un solo comprobante no puede apuntar a uno, así que
            // mirando el producto se habría colado como gasto operativo.
            List<Gasto> delMes = gastos.stream()
                    .filter(gasto -> gasto.getFecha() != null && YearMonth.from(gasto.getFecha()).equals(mes))
                    .toList();
            BigDecimal gastosOperativos = delMes.stream()
                    .filter(gasto -> !gasto.isEsMercaderia())
                    .map(Gasto::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal comprasMercaderia = delMes.stream()
                    .filter(Gasto::isEsMercaderia)
                    .map(Gasto::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal ventas = ventasPorMes.getOrDefault(mes.toString(), BigDecimal.ZERO);
            BigDecimal costoMercaderia = costoPorMes.getOrDefault(mes.toString(), BigDecimal.ZERO);
            BigDecimal totalIngresos = turnos.add(inscripciones).add(ventas);
            BigDecimal gananciaBruta = totalIngresos.subtract(costoMercaderia);

            ingresos.add(IngresoMes.builder()
                    .mes(mes.toString())
                    .turnos(turnos)
                    .inscripciones(inscripciones)
                    .ventas(ventas)
                    .ingresos(totalIngresos)
                    .costoMercaderia(costoMercaderia)
                    .gananciaBruta(gananciaBruta)
                    .gastosOperativos(gastosOperativos)
                    .comprasMercaderia(comprasMercaderia)
                    .resultado(gananciaBruta.subtract(gastosOperativos))
                    .build());
        }
        return ingresos;
    }

    /**
     * Lo que el club efectivamente facturó por un turno. Precio congelado en la reserva:
     * el histórico no cambia si después se actualiza la tarifa de la cancha.
     *
     * <p>Si el cliente no vino, el club se quedó solo con la seña que ya estaba cobrada
     * —no con el turno completo—. Sin seña online no se computa nada: contar el precio
     * entero inflaría la facturación con plata que nunca entró.
     */
    private BigDecimal ingresoReserva(Reserva reserva) {
        if (reserva.getEstado() != EstadoReserva.NO_SHOW) {
            return MontosReserva.precio(reserva);
        }
        return MontosReserva.seniaPagada(reserva);
    }

    /**
     * Inscripciones contra cupo. El cupo se define <b>por categoría</b>: mostrar las
     * parejas de todo el torneo contra el cupo de una sola categoría daba números
     * imposibles del tipo "36/12". Acá se desglosa por categoría y el total del torneo
     * es la suma de los cupos, no un número suelto.
     */
    private List<EmbudoTorneo> calcularEmbudo(Long lugarId) {
        List<Torneo> torneos = torneoRepository.findByActivoTrueAndEstadoIn(TORNEOS_ABIERTOS).stream()
                .filter(torneo -> deLugarTorneo(torneo, lugarId))
                .toList();
        if (torneos.isEmpty()) {
            return List.of();
        }
        List<Long> ids = torneos.stream().map(Torneo::getId).toList();

        // Los torneos abiertos pueden ser más viejos que la ventana del panel, así que la
        // facturación de cada uno se pide agrupada en vez de filtrar la lista de
        // solicitudes: lo único que hace falta es la suma.
        Map<Long, BigDecimal> ingresoPorTorneo = new HashMap<>();
        for (SolicitudInscripcionRepository.IngresoPorTorneo fila : solicitudInscripcionRepository
                .sumarAprobadasPorTorneo(EstadoSolicitud.APROBADA, ids)) {
            ingresoPorTorneo.put(fila.getTorneoId(),
                    fila.getTotalPorJugador().multiply(BigDecimal.valueOf(INTEGRANTES_POR_SOLICITUD)));
        }

        Map<Long, Map<Long, long[]>> conteo = new HashMap<>();
        Map<Long, String> nombreCategoria = new HashMap<>();
        for (Object[] fila : parejaRepository.contarPorTorneoYCategoria(ids)) {
            Long torneoId = (Long) fila[0];
            Long categoriaId = (Long) fila[1];
            nombreCategoria.put(categoriaId, (String) fila[2]);
            conteo.computeIfAbsent(torneoId, k -> new HashMap<>())
                    .put(categoriaId, new long[] { (Long) fila[3] });
        }

        return torneos.stream()
                .map(torneo -> {
                    Map<Long, long[]> porCategoria = conteo.getOrDefault(torneo.getId(), Map.of());
                    BigDecimal ingresos = ingresoPorTorneo.getOrDefault(torneo.getId(), BigDecimal.ZERO);
                    return EmbudoTorneo.builder()
                            .torneoId(torneo.getId())
                            .torneoNombre(torneo.getNombre())
                            .inscriptos(porCategoria.values().stream().mapToLong(valor -> valor[0]).sum())
                            .cupo(cupoTotal(torneo))
                            .ingresos(ingresos)
                            .categorias(cuposPorCategoria(torneo, porCategoria, nombreCategoria))
                            .build();
                })
                .sorted(Comparator.comparingLong(EmbudoTorneo::getInscriptos).reversed())
                .toList();
    }

    private List<CupoCategoria> cuposPorCategoria(Torneo torneo, Map<Long, long[]> porCategoria,
            Map<Long, String> nombreCategoria) {
        return torneo.getCategorias().stream()
                .map(categoria -> CupoCategoria.builder()
                        .categoriaId(categoria.getId())
                        .categoriaNombre(nombreCategoria.getOrDefault(categoria.getId(), categoria.getNombre()))
                        .inscriptos(porCategoria.containsKey(categoria.getId())
                                ? porCategoria.get(categoria.getId())[0]
                                : 0L)
                        .cupo(cupoDeCategoria(torneo, categoria.getId()))
                        .build())
                .sorted(Comparator.comparing(CupoCategoria::getCategoriaNombre))
                .toList();
    }

    private Integer cupoDeCategoria(Torneo torneo, Long categoriaId) {
        Integer cupo = torneo.getCuposPorCategoria() != null
                ? torneo.getCuposPorCategoria().get(categoriaId)
                : null;
        return cupo != null && cupo > 0 ? cupo : null;
    }

    /**
     * Techo real del torneo: la suma de los cupos de sus categorías. Si a alguna le falta
     * el cupo devuelve null, porque un total incompleto se leería como si fuera el tope.
     */
    private Integer cupoTotal(Torneo torneo) {
        List<com.padel.rankpadel.entity.Categoria> categorias = torneo.getCategorias();
        if (categorias.isEmpty()) {
            Integer suelto = torneo.getCupoMaximoParejas() != null
                    ? torneo.getCupoMaximoParejas()
                    : torneo.getCantidadParejasObjetivo();
            return suelto != null && suelto > 0 ? suelto : null;
        }
        int total = 0;
        for (com.padel.rankpadel.entity.Categoria categoria : categorias) {
            Integer cupo = cupoDeCategoria(torneo, categoria.getId());
            if (cupo == null) {
                return null;
            }
            total += cupo;
        }
        return total;
    }

    private List<CategoriaDemanda> calcularCategoriasDemandadas(List<SolicitudInscripcion> solicitudes) {
        Map<String, Long> porCategoria = solicitudes.stream()
                .filter(solicitud -> solicitud.getEstado() == EstadoSolicitud.APROBADA && solicitud.getCategoria() != null)
                .collect(Collectors.groupingBy(solicitud -> solicitud.getCategoria().getNombre(), Collectors.counting()));
        return porCategoria.entrySet().stream()
                .map(entrada -> CategoriaDemanda.builder().categoriaNombre(entrada.getKey()).inscriptos(entrada.getValue()).build())
                .sorted(Comparator.comparingLong(CategoriaDemanda::getInscriptos).reversed())
                .toList();
    }

    /**
     * Lo que facturó una inscripción. Usa el costo CONGELADO en la solicitud, no el del
     * torneo: con el costo vivo, cambiar el precio de inscripción reescribía la
     * facturación de todos los meses pasados, y el mismo torneo de julio valía distinto
     * según el día en que se mirara el informe. Mismo criterio que
     * {@code Reserva.precioAplicado} y {@code VentaItem.precioUnitario}.
     *
     * <p>Las solicitudes viejas, de antes de que existiera la columna, caen al costo del
     * torneo: es el único dato que hay.
     */
    private BigDecimal ingresoSolicitud(SolicitudInscripcion solicitud) {
        BigDecimal costo = solicitud.getCostoAplicado() != null
                ? solicitud.getCostoAplicado()
                : solicitud.getTorneo().getCostoInscripcionJugador();
        return costo != null
                ? costo.multiply(BigDecimal.valueOf(INTEGRANTES_POR_SOLICITUD))
                : BigDecimal.ZERO;
    }

    private boolean deLugar(Reserva reserva, Long lugarId) {
        if (lugarId == null) return true;
        return reserva.getCancha() != null && reserva.getCancha().getLugar() != null
                && lugarId.equals(reserva.getCancha().getLugar().getId());
    }

    private boolean deLugarTorneo(Torneo torneo, Long lugarId) {
        if (lugarId == null) return true;
        return torneo.getLugar() != null && lugarId.equals(torneo.getLugar().getId());
    }
}
