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
import com.padel.rankpadel.entity.Cliente;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.ReservaMapper;
import com.padel.rankpadel.repository.ClienteRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.ReservaRepository.ResumenCliente;
import com.padel.rankpadel.util.NormalizadorTelefono;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private static final int HISTORIAL_MAXIMO = 50;

    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaMapper reservaMapper;

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

        return ClienteFichaResponse.builder()
                .cliente(aResponse(cliente, resumen, esJugador))
                .historial(reservaRepository
                        .findHistorialCliente(id, PageRequest.of(0, HISTORIAL_MAXIMO)).stream()
                        .map(reservaMapper::aResponse)
                        .toList())
                .build();
    }

    @Transactional
    public ClienteResponse actualizar(Long id, ClienteRequest request) {
        Cliente cliente = buscar(id);
        cliente.setNombre(request.getNombre().trim());
        cliente.setTelefono(request.getTelefono().trim());
        cliente.setEmail(request.getEmail());
        cliente.setNotas(request.getNotas());
        clienteRepository.save(cliente);
        return aResponse(cliente, null, false);
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
