package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.ClienteRequest;
import com.padel.rankpadel.dto.response.ClienteFichaResponse;
import com.padel.rankpadel.dto.response.ClienteResponse;
import com.padel.rankpadel.dto.response.ReservaResponse;
import com.padel.rankpadel.entity.Cliente;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.Venta;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.ReservaMapper;
import com.padel.rankpadel.mapper.TurnoFijoMapper;
import com.padel.rankpadel.repository.ClienteRepository;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.ReservaRepository.ResumenCliente;
import com.padel.rankpadel.repository.TurnoFijoRepository;
import com.padel.rankpadel.repository.VentaRepository;
import com.padel.rankpadel.util.MontosReserva;
import com.padel.rankpadel.util.NormalizadorTelefono;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private static final int HISTORIAL_MAXIMO = 50;
    private static final int COMPRAS_EN_LA_FICHA = 10;
    private static final int MESES_DE_COMPRAS = 12;

    /** Turnos sobre los que puede quedar plata a cobrar. Un cancelado no debe nada. */
    private static final Set<EstadoReserva> CON_SALDO = Set.of(
            EstadoReserva.CONFIRMADA, EstadoReserva.FINALIZADA, EstadoReserva.NO_SHOW);

    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaMapper reservaMapper;
    private final CobroRepository cobroRepository;
    private final VentaRepository ventaRepository;
    private final VentaService ventaService;
    private final TurnoFijoRepository turnoFijoRepository;
    private final TurnoFijoMapper turnoFijoMapper;

    /**
     * Encuentra al cliente por su teléfono o lo crea. Se llama en cada reserva, así que
     * la ficha se arma sola con el uso: el club no tiene que cargar nada a mano.
     *
     * <p>Devuelve null si el teléfono no es utilizable como identidad; en ese caso la
     * reserva queda sin ficha en vez de fallar.
     */
    @Transactional
    public Cliente buscarOCrear(String nombre, String telefono) {
        String normalizado = NormalizadorTelefono.normalizar(telefono);
        if (normalizado == null) {
            return null;
        }
        return clienteRepository.findByTelefonoNormalizado(normalizado)
                .orElseGet(() -> clienteRepository.save(Cliente.builder()
                        .nombre(nombre != null ? nombre.trim() : "Sin nombre")
                        .telefono(telefono.trim())
                        .creadoEn(LocalDateTime.now())
                        .build()));
    }

    @Transactional(readOnly = true)
    public Page<ClienteResponse> listar(String busqueda, int pagina, int tamano) {
        Page<Cliente> clientes = clienteRepository.buscar(
                busqueda, PageRequest.of(pagina, tamano, Sort.by("nombre").ascending()));
        if (clientes.isEmpty()) {
            return clientes.map(cliente -> aResponse(cliente, null, false));
        }

        List<Long> ids = clientes.getContent().stream().map(Cliente::getId).toList();
        Map<Long, ResumenCliente> resumenes = new HashMap<>();
        for (ResumenCliente resumen : reservaRepository.resumenPorCliente(ids)) {
            resumenes.put(resumen.getClienteId(), resumen);
        }
        Set<Long> conJugador = new HashSet<>(clienteRepository.idsConJugador(ids));

        return clientes.map(cliente ->
                aResponse(cliente, resumenes.get(cliente.getId()), conJugador.contains(cliente.getId())));
    }

    @Transactional(readOnly = true)
    public ClienteFichaResponse ficha(Long id) {
        Cliente cliente = buscar(id);
        List<Long> ids = List.of(id);
        ResumenCliente resumen = reservaRepository.resumenPorCliente(ids).stream().findFirst().orElse(null);
        boolean esJugador = !clienteRepository.idsConJugador(ids).isEmpty();

        List<Reserva> turnos = reservaRepository.findHistorialCliente(id, PageRequest.of(0, HISTORIAL_MAXIMO));

        // Lo cobrado y el consumo impago de todo el historial, en dos consultas agrupadas.
        // Antes el historial se mapeaba sin esto y el saldo salía mal: mostraba como
        // impago un turno que ya se había cobrado en el mostrador.
        List<Long> turnoIds = turnos.stream().map(Reserva::getId).toList();
        Map<Long, BigDecimal> cobrado = new HashMap<>();
        Map<Long, BigDecimal> consumo = new HashMap<>();
        if (!turnoIds.isEmpty()) {
            for (CobroRepository.TotalPorReserva total : cobroRepository.totalesPorReserva(turnoIds)) {
                cobrado.put(total.getReservaId(), total.getTotal());
            }
            for (VentaRepository.ConsumoPorReserva total : ventaRepository.consumoACuentaDe(turnoIds)) {
                consumo.put(total.getReservaId(), total.getTotal());
            }
        }

        List<ReservaResponse> historial = turnos.stream()
                .map(turno -> reservaMapper.aResponse(turno, cobrado.get(turno.getId()), consumo.get(turno.getId())))
                .toList();

        List<Venta> compras = ventaRepository.findComprasDelCliente(
                id, LocalDateTime.now().minusMonths(MESES_DE_COMPRAS));

        return ClienteFichaResponse.builder()
                .cliente(aResponse(cliente, resumen, esJugador))
                .historial(historial)
                .deuda(calcularDeuda(id))
                .consumoKiosco(compras.stream().map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .ultimasCompras(compras.stream().limit(COMPRAS_EN_LA_FICHA).map(ventaService::aResponse).toList())
                .abonos(turnoFijoRepository.findPorCliente(id).stream()
                        .map(turnoFijoMapper::aResponse)
                        .toList())
                .otrosNombres(otrosNombres(id, cliente.getNombre()))
                .build();
    }

    @Transactional
    public ClienteResponse actualizar(Long id, ClienteRequest request) {
        Cliente cliente = buscar(id);
        validarTelefonoLibre(id, request.getTelefono());
        cliente.setNombre(request.getNombre().trim());
        cliente.setTelefono(request.getTelefono().trim());
        cliente.setEmail(request.getEmail());
        cliente.setNotas(request.getNotas());
        clienteRepository.save(cliente);
        // Con el resumen en null la fila volvía al listado con todas las estadísticas en
        // cero y sin la marca de jugador, como si corregir el nombre borrara el historial.
        List<Long> ids = List.of(id);
        ResumenCliente resumen = reservaRepository.resumenPorCliente(ids).stream().findFirst().orElse(null);
        return aResponse(cliente, resumen, !clienteRepository.idsConJugador(ids).isEmpty());
    }

    /**
     * Todo lo que este cliente debe, sobre su historial completo. Antes se sumaba el
     * saldo de los turnos que se mostraban en pantalla, que son los últimos 50: a un
     * abonado, lo impago de más atrás le desaparecía de la deuda sin ningún aviso.
     */
    private BigDecimal calcularDeuda(Long clienteId) {
        List<Reserva> cobrables = reservaRepository.findCobrablesDeCliente(clienteId, CON_SALDO);
        if (cobrables.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> ids = cobrables.stream().map(Reserva::getId).toList();
        Map<Long, BigDecimal> cobrado = new HashMap<>();
        for (CobroRepository.TotalPorReserva total : cobroRepository.totalesPorReserva(ids)) {
            cobrado.put(total.getReservaId(), total.getTotal());
        }
        Map<Long, BigDecimal> consumo = new HashMap<>();
        for (VentaRepository.ConsumoPorReserva total : ventaRepository.consumoACuentaDe(ids)) {
            consumo.put(total.getReservaId(), total.getTotal());
        }
        return cobrables.stream()
                .map(reserva -> MontosReserva.saldo(
                        reserva, cobrado.get(reserva.getId()), consumo.get(reserva.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Nombres con los que reservó este teléfono, sin contar el que ya tiene la ficha. */
    private List<String> otrosNombres(Long clienteId, String nombreDeLaFicha) {
        return reservaRepository.nombresUsadosPor(clienteId).stream()
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .filter(nombre -> !nombre.equalsIgnoreCase(nombreDeLaFicha))
                .distinct()
                .toList();
    }

    /**
     * El teléfono es la identidad del cliente, así que no puede repetirse. Sin este
     * control la corrección de un número reventaba contra el índice único con un error
     * interno, en vez de explicar que ese número ya tiene ficha.
     */
    private void validarTelefonoLibre(Long id, String telefono) {
        String normalizado = NormalizadorTelefono.normalizar(telefono);
        if (normalizado == null) {
            throw new EstadoInvalidoException("El teléfono no es válido");
        }
        clienteRepository.findByTelefonoNormalizado(normalizado)
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new EstadoInvalidoException(
                            "Ese teléfono ya es de la ficha de " + existente.getNombre());
                });
    }

    private Cliente buscar(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
    }

    private ClienteResponse aResponse(Cliente cliente, ResumenCliente resumen, boolean esJugador) {
        return ClienteResponse.builder()
                .id(cliente.getId())
                .nombre(cliente.getNombre())
                .telefono(cliente.getTelefono())
                .email(cliente.getEmail())
                .notas(cliente.getNotas())
                .esJugador(esJugador)
                .turnosTotales(resumen != null ? resumen.getTotales() : 0)
                .turnosJugados(resumen != null ? resumen.getJugados() : 0)
                .turnosCaidos(resumen != null ? resumen.getCaidos() : 0)
                .turnosNoShow(resumen != null ? resumen.getNoShows() : 0)
                .gastoAcumulado(resumen != null && resumen.getGastado() != null
                        ? resumen.getGastado() : BigDecimal.ZERO)
                .ultimoTurno(resumen != null ? resumen.getUltimoTurno() : null)
                .build();
    }
}
