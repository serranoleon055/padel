package com.padel.rankpadel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.AprobarInscripcionRequest;
import com.padel.rankpadel.dto.request.IntegranteInscripcionRequest;
import com.padel.rankpadel.dto.request.ParejaRequest;
import com.padel.rankpadel.dto.request.SolicitudInscripcionRequest;
import com.padel.rankpadel.dto.response.JugadorCandidatoResponse;
import com.padel.rankpadel.dto.response.SolicitudInscripcionResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.SolicitudInscripcion;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoSolicitud;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.JugadorRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.SolicitudInscripcionRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.util.NormalizadorTexto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InscripcionService {

    private final SolicitudInscripcionRepository solicitudInscripcionRepository;
    private final TorneoRepository torneoRepository;
    private final CategoriaRepository categoriaRepository;
    private final JugadorRepository jugadorRepository;
    private final ParejaRepository parejaRepository;
    private final ParejaService parejaService;

    @Transactional
    public SolicitudInscripcionResponse crear(Long torneoId, SolicitudInscripcionRequest request) {
        SolicitudInscripcion solicitud = construirSolicitud(torneoId, request);
        solicitudInscripcionRepository.save(solicitud);
        return aResponse(solicitud);
    }

    @Transactional
    public SolicitudInscripcion crearParaPago(Long torneoId, SolicitudInscripcionRequest request, Pago pago) {
        SolicitudInscripcion solicitud = construirSolicitud(torneoId, request);
        solicitud.setPago(pago);
        solicitudInscripcionRepository.save(solicitud);
        return solicitud;
    }

    private SolicitudInscripcion construirSolicitud(Long torneoId, SolicitudInscripcionRequest request) {
        Torneo torneo = torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));
        if (!torneo.getEstado().equals(EstadoTorneo.INSCRIPCION)) {
            throw new EstadoInvalidoException("El torneo no está en período de inscripción");
        }

        Categoria categoria = categoriaRepository.findById(request.getCategoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.getCategoriaId()));
        boolean categoriaActiva = torneo.getCategorias().stream()
                .anyMatch(c -> c.getId().equals(categoria.getId()));
        if (!categoriaActiva) {
            throw new EstadoInvalidoException("La categoría no está activa en este torneo");
        }

        Integer cupoCategoria = torneo.getCuposPorCategoria() != null
                ? torneo.getCuposPorCategoria().get(categoria.getId())
                : null;
        if (cupoCategoria != null && cupoCategoria > 0) {
            long inscriptas = parejaRepository.countByTorneoIdAndCategoriaId(torneoId, categoria.getId());
            long pagadasPendientes = solicitudInscripcionRepository
                    .countByTorneoIdAndCategoriaIdAndPagadaTrueAndEstado(torneoId, categoria.getId(),
                            EstadoSolicitud.PENDIENTE);
            if (inscriptas + pagadasPendientes >= cupoCategoria) {
                throw new EstadoInvalidoException(
                        "La categoría '" + categoria.getNombre() + "' ya cubrió su cupo de " + cupoCategoria
                                + " parejas. No se aceptan más inscripciones.");
            }
        }

        SolicitudInscripcion solicitud = SolicitudInscripcion.builder()
                .torneo(torneo)
                .categoria(categoria)
                .estado(EstadoSolicitud.PENDIENTE)
                .telefonoContacto(request.getTelefonoContacto())
                .creadoEn(LocalDateTime.now())
                .build();

        aplicarIntegrante(solicitud, request.getJugador1(), 1);
        aplicarIntegrante(solicitud, request.getJugador2(), 2);

        return solicitud;
    }

    @Transactional(readOnly = true)
    public List<SolicitudInscripcionResponse> listar(Long torneoId, String estado) {
        List<SolicitudInscripcion> solicitudes;
        if (estado != null && !estado.isBlank()) {
            EstadoSolicitud estadoFiltro;
            try {
                estadoFiltro = EstadoSolicitud.valueOf(estado.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new EstadoInvalidoException("Estado de solicitud inválido");
            }
            solicitudes = solicitudInscripcionRepository.findByTorneoIdAndEstado(torneoId, estadoFiltro);
        } else {
            solicitudes = solicitudInscripcionRepository.findByTorneoId(torneoId);
        }
        return solicitudes.stream().map(this::aResponse).collect(Collectors.toList());
    }

    @Transactional
    public SolicitudInscripcionResponse aprobar(Long id, AprobarInscripcionRequest seleccion) {
        SolicitudInscripcion solicitud = pendiente(id);

        Long jugador1IdElegido = seleccion != null ? seleccion.getJugador1Id() : null;
        Long jugador2IdElegido = seleccion != null ? seleccion.getJugador2Id() : null;

        Jugador jugador1 = resolverJugador(solicitud, 1, jugador1IdElegido);
        Jugador jugador2 = resolverJugador(solicitud, 2, jugador2IdElegido);

        ParejaRequest parejaRequest = ParejaRequest.builder()
                .jugador1Id(jugador1.getId())
                .jugador2Id(jugador2.getId())
                .categoriaId(solicitud.getCategoria().getId())
                .build();
        parejaService.inscribir(solicitud.getTorneo().getId(), parejaRequest);

        solicitud.setEstado(EstadoSolicitud.APROBADA);
        solicitudInscripcionRepository.save(solicitud);
        return aResponse(solicitud);
    }

    @Transactional
    public SolicitudInscripcionResponse rechazar(Long id) {
        SolicitudInscripcion solicitud = pendiente(id);
        solicitud.setEstado(EstadoSolicitud.RECHAZADA);
        solicitudInscripcionRepository.save(solicitud);
        return aResponse(solicitud);
    }

    private SolicitudInscripcion pendiente(Long id) {
        SolicitudInscripcion solicitud = solicitudInscripcionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SolicitudInscripcion", id));
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new EstadoInvalidoException("La solicitud ya fue resuelta");
        }
        return solicitud;
    }

    private void aplicarIntegrante(SolicitudInscripcion solicitud, IntegranteInscripcionRequest data, int numero) {
        if (data != null && data.getJugadorId() != null) {
            Jugador jugador = jugadorRepository.findById(data.getJugadorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Jugador", data.getJugadorId()));
            if (numero == 1) {
                solicitud.setJugador1(jugador);
            } else {
                solicitud.setJugador2(jugador);
            }
            return;
        }

        if (data == null || data.getNombre() == null || data.getNombre().isBlank()
                || data.getApellido() == null || data.getApellido().isBlank() || data.getGenero() == null) {
            throw new EstadoInvalidoException("Faltá elegir un jugador existente o completar nombre, apellido y género");
        }

        if (numero == 1) {
            solicitud.setJugador1Nombre(data.getNombre().trim());
            solicitud.setJugador1Apellido(data.getApellido().trim());
            solicitud.setJugador1Genero(data.getGenero());
            solicitud.setJugador1Telefono(data.getTelefono());
            solicitud.setJugador1FechaNacimiento(data.getFechaNacimiento());
            solicitud.setJugador1PosicionJuego(data.getPosicionJuego());
        } else {
            solicitud.setJugador2Nombre(data.getNombre().trim());
            solicitud.setJugador2Apellido(data.getApellido().trim());
            solicitud.setJugador2Genero(data.getGenero());
            solicitud.setJugador2Telefono(data.getTelefono());
            solicitud.setJugador2FechaNacimiento(data.getFechaNacimiento());
            solicitud.setJugador2PosicionJuego(data.getPosicionJuego());
        }
    }

    private Jugador resolverJugador(SolicitudInscripcion solicitud, int numero, Long jugadorIdElegido) {
        Jugador existente = numero == 1 ? solicitud.getJugador1() : solicitud.getJugador2();
        if (existente != null) {
            return existente;
        }
        if (jugadorIdElegido != null) {
            return jugadorRepository.findById(jugadorIdElegido)
                    .orElseThrow(() -> new ResourceNotFoundException("Jugador", jugadorIdElegido));
        }
        Jugador nuevo = Jugador.builder()
                .nombre(numero == 1 ? solicitud.getJugador1Nombre() : solicitud.getJugador2Nombre())
                .apellido(numero == 1 ? solicitud.getJugador1Apellido() : solicitud.getJugador2Apellido())
                .genero(numero == 1 ? solicitud.getJugador1Genero() : solicitud.getJugador2Genero())
                .telefono(numero == 1 ? solicitud.getJugador1Telefono() : solicitud.getJugador2Telefono())
                .fechaNacimiento(numero == 1 ? solicitud.getJugador1FechaNacimiento() : solicitud.getJugador2FechaNacimiento())
                .posicionJuego(numero == 1 ? solicitud.getJugador1PosicionJuego() : solicitud.getJugador2PosicionJuego())
                .categoria(solicitud.getCategoria())
                .fechaRegistro(LocalDate.now())
                .activo(true)
                .build();
        return jugadorRepository.save(nuevo);
    }

    private SolicitudInscripcionResponse aResponse(SolicitudInscripcion solicitud) {
        boolean jugador1EsNuevo = solicitud.getJugador1() == null;
        boolean jugador2EsNuevo = solicitud.getJugador2() == null;
        return SolicitudInscripcionResponse.builder()
                .id(solicitud.getId())
                .torneoId(solicitud.getTorneo() != null ? solicitud.getTorneo().getId() : null)
                .categoriaId(solicitud.getCategoria() != null ? solicitud.getCategoria().getId() : null)
                .categoriaNombre(solicitud.getCategoria() != null ? solicitud.getCategoria().getNombre() : null)
                .estado(solicitud.getEstado() != null ? solicitud.getEstado().name() : null)
                .telefonoContacto(solicitud.getTelefonoContacto())
                .jugador1(nombreIntegrante(solicitud, 1))
                .jugador2(nombreIntegrante(solicitud, 2))
                .jugador1EsNuevo(jugador1EsNuevo)
                .jugador2EsNuevo(jugador2EsNuevo)
                .jugador1Candidatos(jugador1EsNuevo ? buscarCandidatos(solicitud, 1) : Collections.emptyList())
                .jugador2Candidatos(jugador2EsNuevo ? buscarCandidatos(solicitud, 2) : Collections.emptyList())
                .pagada(solicitud.isPagada())
                .estadoPago(solicitud.getPago() != null && solicitud.getPago().getEstado() != null
                        ? solicitud.getPago().getEstado().name()
                        : null)
                .montoSenia(solicitud.getPago() != null ? solicitud.getPago().getMontoSenia() : null)
                .build();
    }

    private List<JugadorCandidatoResponse> buscarCandidatos(SolicitudInscripcion solicitud, int numero) {
        String nombre = numero == 1 ? solicitud.getJugador1Nombre() : solicitud.getJugador2Nombre();
        String apellido = numero == 1 ? solicitud.getJugador1Apellido() : solicitud.getJugador2Apellido();
        Genero genero = numero == 1 ? solicitud.getJugador1Genero() : solicitud.getJugador2Genero();
        if (nombre == null || apellido == null || genero == null) {
            return Collections.emptyList();
        }
        String nombreNormalizado = NormalizadorTexto.normalizarNombre(nombre, apellido);
        return jugadorRepository.findByActivoTrueAndGeneroAndNombreNormalizado(genero, nombreNormalizado).stream()
                .map(jugador -> JugadorCandidatoResponse.builder()
                        .id(jugador.getId())
                        .nombre(jugador.getNombre())
                        .apellido(jugador.getApellido())
                        .categoriaNombre(jugador.getCategoria() != null ? jugador.getCategoria().getNombre() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private String nombreIntegrante(SolicitudInscripcion solicitud, int numero) {
        Jugador existente = numero == 1 ? solicitud.getJugador1() : solicitud.getJugador2();
        if (existente != null) {
            return existente.getNombre() + " " + existente.getApellido();
        }
        String nombre = numero == 1 ? solicitud.getJugador1Nombre() : solicitud.getJugador2Nombre();
        String apellido = numero == 1 ? solicitud.getJugador1Apellido() : solicitud.getJugador2Apellido();
        return ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
    }
}
