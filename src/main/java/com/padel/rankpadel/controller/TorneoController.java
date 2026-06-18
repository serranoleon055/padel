package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.padel.rankpadel.dto.request.CambioEstadoRequest;
import com.padel.rankpadel.dto.request.ParejaRequest;
import com.padel.rankpadel.dto.request.ProgramarPartidoRequest;
import com.padel.rankpadel.dto.request.ResultadoRequest;
import com.padel.rankpadel.dto.request.TorneoRequest;
import com.padel.rankpadel.dto.request.WalkoverRequest;
import com.padel.rankpadel.dto.response.PagedResponse;
import com.padel.rankpadel.dto.response.GrupoResponse;
import com.padel.rankpadel.dto.response.ParejaResponse;
import com.padel.rankpadel.dto.response.PartidoResponse;
import com.padel.rankpadel.dto.response.TorneoDetalleResponse;
import com.padel.rankpadel.dto.response.TorneoResponse;
import com.padel.rankpadel.service.ParejaService;
import com.padel.rankpadel.service.PartidoService;
import com.padel.rankpadel.service.ResultadoService;
import com.padel.rankpadel.service.SorteoService;
import com.padel.rankpadel.service.TorneoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/torneos")
@Tag(name = "Torneos", description = "Gestión completa de torneos: CRUD, estados, inscripciones, sorteo, partidos y resultados.")
public class TorneoController {

        private final TorneoService torneoService;
        private final ParejaService parejaService;
        private final SorteoService sorteoService;
        private final PartidoService partidoService;
        private final ResultadoService resultadoService;

        @SecurityRequirements({})
        @Operation(summary = "Listar todos los torneos (sin paginar — para compatibilidad)")
        @ApiResponse(responseCode = "200", description = "Lista de torneos")
        @GetMapping
        public ResponseEntity<List<TorneoResponse>> listarTodos() {
                return ResponseEntity.ok(torneoService.listarTodos());
        }

        @SecurityRequirements({})
        @Operation(summary = "Listar torneos paginado", description = "page inicia en 0. size por defecto 9.")
        @GetMapping("/paginado")
        public ResponseEntity<PagedResponse<TorneoResponse>> listarPaginado(
                        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int pagina,
                        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "9") int tamanio) {
                return ResponseEntity.ok(PagedResponse.of(torneoService.listarTodos(), pagina, tamanio));
        }

        @SecurityRequirements({})
        @Operation(summary = "Obtener torneo por ID")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Torneo encontrado", content = @Content(schema = @Schema(implementation = TorneoResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Torneo no encontrado", content = @Content)
        })
        @GetMapping("/{id}")
        public ResponseEntity<TorneoResponse> buscarPorId(
                        @Parameter(description = "ID del torneo") @PathVariable Long id) {
                return ResponseEntity.ok(torneoService.buscarPorId(id));
        }

        @SecurityRequirements({})
        @Operation(summary = "Obtener detalle publico completo del torneo")
        @ApiResponse(responseCode = "200", description = "Detalle del torneo con parejas y partidos")
        @GetMapping("/{id}/detalle")
        public ResponseEntity<TorneoDetalleResponse> obtenerDetalle(
                        @Parameter(description = "ID del torneo") @PathVariable Long id) {
                return ResponseEntity.ok(TorneoDetalleResponse.builder()
                                .torneo(torneoService.buscarPorId(id))
                                .parejas(parejaService.listarPorTorneo(id))
                                .partidos(partidoService.listarPorTorneo(id))
                                .build());
        }

        @SecurityRequirements({})
        @Operation(summary = "Listar parejas del torneo")
        @ApiResponse(responseCode = "200", description = "Lista de parejas inscriptas")
        @GetMapping("/{id}/parejas")
        public ResponseEntity<List<ParejaResponse>> listarParejas(
                        @Parameter(description = "ID del torneo") @PathVariable Long id) {
                return ResponseEntity.ok(parejaService.listarPorTorneo(id));
        }

        @SecurityRequirements({})
        @Operation(summary = "Listar partidos del torneo")
        @ApiResponse(responseCode = "200", description = "Lista de partidos generados")
        @GetMapping("/{id}/partidos")
        public ResponseEntity<List<PartidoResponse>> listarPartidos(
                        @Parameter(description = "ID del torneo") @PathVariable Long id) {
                return ResponseEntity.ok(partidoService.listarPorTorneo(id));
        }

        @Operation(summary = "Crear torneo", description = "Requiere JWT. El torneo se crea en estado BORRADOR.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Torneo creado", content = @Content(schema = @Schema(implementation = TorneoResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
        })
        @PostMapping
        public ResponseEntity<TorneoResponse> crear(@Valid @RequestBody TorneoRequest torneoRequest) {
                return ResponseEntity.status(HttpStatus.CREATED).body(torneoService.crear(torneoRequest));
        }

        @Operation(summary = "Actualizar torneo", description = "Requiere JWT.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Torneo actualizado"),
                        @ApiResponse(responseCode = "404", description = "Torneo no encontrado", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
        })
        @PutMapping("/{id}")
        public ResponseEntity<TorneoResponse> actualizar(
                        @Parameter(description = "ID del torneo") @PathVariable Long id,
                        @Valid @RequestBody TorneoRequest torneoRequest) {
                return ResponseEntity.ok(torneoService.actualizar(id, torneoRequest));
        }

        @Operation(summary = "Subir imagen del torneo", description = "Requiere JWT. Recibe multipart/form-data con el campo file (JPG o PNG).")
        @PostMapping(value = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<TorneoResponse> subirImagen(
                        @Parameter(description = "ID del torneo") @PathVariable Long id,
                        @RequestPart("file") MultipartFile file) {
                return ResponseEntity.ok(torneoService.actualizarImagen(id, file));
        }

        @Operation(summary = "Eliminar torneo", description = "Requiere JWT.")
        @ApiResponses({
                        @ApiResponse(responseCode = "204", description = "Torneo eliminado"),
                        @ApiResponse(responseCode = "404", description = "Torneo no encontrado", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
        })
        @DeleteMapping("/{id}")
        public ResponseEntity<?> eliminar(
                        @Parameter(description = "ID del torneo") @PathVariable Long id) {
                torneoService.eliminar(id);
                return ResponseEntity.noContent().build();
        }

        @Operation(summary = "Cambiar estado del torneo", description = """
                        Requiere JWT. Transiciones válidas de la máquina de estados:
                        - `BORRADOR` → `INSCRIPCION`
                        - `SORTEADO` → `EN_CURSO`
                        - `EN_CURSO` → `FINALIZADO`
                        - `INSCRIPCION` → `BORRADOR` (cancelar inscripciones)
                        - `BORRADOR` / `INSCRIPCION` / `SORTEADO` → `CANCELADO`

                        El sorteo se genera mediante el endpoint POST /{id}/sorteo, que pasa el
                        torneo de `INSCRIPCION` a `SORTEADO` automáticamente.
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Estado actualizado exitosamente"),
                        @ApiResponse(responseCode = "400", description = "Transición de estado inválida", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Torneo no encontrado", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
        })
        @PatchMapping("/{id}/estado")
        public ResponseEntity<TorneoResponse> cambiarEstado(
                        @Parameter(description = "ID del torneo") @PathVariable Long id,
                        @Valid @RequestBody CambioEstadoRequest request) {
                return ResponseEntity.ok(torneoService.cambiarEstado(id, request.getEstado()));
        }

        @Operation(summary = "Inscribir pareja en torneo", description = "Requiere JWT. El torneo debe estar en estado INSCRIPCION. No se puede inscribir un jugador dos veces.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Pareja inscripta exitosamente", content = @Content(schema = @Schema(implementation = ParejaResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Jugador ya inscripto o estado incorrecto", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Jugador o torneo no encontrado", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
        })
        @PostMapping("/{id}/parejas")
        public ResponseEntity<ParejaResponse> inscribirPareja(
                        @Parameter(description = "ID del torneo") @PathVariable Long id,
                        @Valid @RequestBody ParejaRequest parejaRequest) {
                return ResponseEntity.ok(parejaService.inscribir(id, parejaRequest));
        }

        @Operation(summary = "Generar sorteo del torneo", description = "Requiere JWT. Genera el bracket o grupos según el formato del torneo. El torneo debe estar en estado INSCRIPCION y tener parejas inscriptas. Al completarse, pasa al estado SORTEADO.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Sorteo generado exitosamente"),
                        @ApiResponse(responseCode = "400", description = "Estado incorrecto o pocas parejas", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
        })
        @PostMapping("/{id}/sorteo")
        public ResponseEntity<Void> generarSorteo(
                        @Parameter(description = "ID del torneo") @PathVariable Long id) {
                sorteoService.generarSorteo(id);
                return ResponseEntity.ok().build();
        }

        @Operation(summary = "Cargar resultado de partido", description = "Requiere JWT. Formato del marcador: sets separados por espacio, cada set con guión. Ej: `6-3 6-4` o `6-3 3-6 7-5`.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Resultado cargado exitosamente", content = @Content(schema = @Schema(implementation = PartidoResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Marcador inválido o partido no pertenece al torneo", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Partido o torneo no encontrado", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
        })
        @PutMapping("/{id}/partidos/{idPartido}/resultado")
        public ResponseEntity<PartidoResponse> cargarResultado(
                        @Parameter(description = "ID del torneo") @PathVariable Long id,
                        @Parameter(description = "ID del partido") @PathVariable Long idPartido,
                        @Valid @RequestBody ResultadoRequest resultadoRequest) {
                return ResponseEntity.ok(resultadoService.cargarResultado(id, idPartido, resultadoRequest));
        }

        @Operation(summary = "Corregir resultado de partido", description = "Requiere JWT. Revierte el resultado anterior (ranking y posiciones) y aplica el nuevo. Solo se permite si el partido todavía no generó la ronda siguiente o el cuadro de la categoría.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Resultado corregido", content = @Content(schema = @Schema(implementation = PartidoResponse.class))),
                        @ApiResponse(responseCode = "400", description = "El partido no se puede corregir o el marcador es inválido", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Partido o torneo no encontrado", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
        })
        @PatchMapping("/{id}/partidos/{idPartido}/resultado")
        public ResponseEntity<PartidoResponse> corregirResultado(
                        @Parameter(description = "ID del torneo") @PathVariable Long id,
                        @Parameter(description = "ID del partido") @PathVariable Long idPartido,
                        @Valid @RequestBody ResultadoRequest resultadoRequest) {
                return ResponseEntity.ok(resultadoService.corregirResultado(id, idPartido, resultadoRequest));
        }

        @Operation(summary = "Iniciar partido", description = "Requiere JWT. Marca un partido como EN_CURSO.")
        @PatchMapping("/{id}/partidos/{idPartido}/iniciar")
        public ResponseEntity<PartidoResponse> iniciarPartido(
                        @Parameter(description = "ID del torneo") @PathVariable Long id,
                        @Parameter(description = "ID del partido") @PathVariable Long idPartido) {
                return ResponseEntity.ok(partidoService.iniciarPartido(id, idPartido));
        }

        @Operation(summary = "Programar fecha, hora y cancha de un partido", description = "Requiere JWT. Establece la fecha/hora programada y la cancha sin afectar el estado del partido.")
        @PatchMapping("/{id}/partidos/{idPartido}/programar")
        public ResponseEntity<PartidoResponse> programarPartido(
                        @PathVariable Long id,
                        @PathVariable Long idPartido,
                        @Valid @RequestBody ProgramarPartidoRequest request) {
                return ResponseEntity.ok(partidoService.programarPartido(id, idPartido, request));
        }

        @Operation(summary = "Declarar walkover o retiro", description = "Requiere JWT. WALKOVER: la pareja no se presentó (sin puntos). RETIRO: abandonó durante el partido (ganador recibe puntos normales).")
        @PatchMapping("/{id}/partidos/{idPartido}/walkover")
        public ResponseEntity<PartidoResponse> declararWalkover(
                        @PathVariable Long id,
                        @PathVariable Long idPartido,
                        @Valid @RequestBody WalkoverRequest request) {
                return ResponseEntity.ok(partidoService.declararWalkoverORetiro(id, idPartido, request));
        }

        @Operation(summary = "Retirar pareja del torneo en curso", description = "Requiere JWT. Genera W.O. automático en todos los partidos pendientes de la pareja.")
        @PatchMapping("/{id}/parejas/{parejaId}/retirar")
        public ResponseEntity<Void> retirarPareja(
                        @PathVariable Long id,
                        @PathVariable Long parejaId) {
                parejaService.retirarPareja(id, parejaId);
                return ResponseEntity.noContent().build();
        }

        @Operation(summary = "Eliminar pareja del torneo", description = "Requiere JWT. Solo en estado INSCRIPCION.")
        @DeleteMapping("/{id}/parejas/{parejaId}")
        public ResponseEntity<?> eliminarPareja(
                        @Parameter(description = "ID del torneo") @PathVariable Long id,
                        @Parameter(description = "ID de la pareja") @PathVariable Long parejaId) {
                parejaService.eliminarDelTorneo(id, parejaId);
                return ResponseEntity.noContent().build();
        }

        @Operation(summary = "Editar pareja del torneo", description = "Requiere JWT. Solo en estado INSCRIPCION.")
        @PutMapping("/{id}/parejas/{parejaId}")
        public ResponseEntity<ParejaResponse> editarPareja(
                        @Parameter(description = "ID del torneo") @PathVariable Long id,
                        @Parameter(description = "ID de la pareja") @PathVariable Long parejaId,
                        @Valid @RequestBody ParejaRequest parejaRequest) {
                return ResponseEntity.ok(parejaService.editar(id, parejaId, parejaRequest));
        }

        @SecurityRequirements({})
        @Operation(summary = "Calendario de partidos programados del torneo", description = "Devuelve los partidos con fecha/hora programada pendientes de jugar, ordenados cronológicamente.")
        @GetMapping("/{id}/calendario")
        public ResponseEntity<List<PartidoResponse>> calendario(
                        @Parameter(description = "ID del torneo") @PathVariable Long id) {
                return ResponseEntity.ok(partidoService.calendarioPorTorneo(id));
        }

        @SecurityRequirements({})
        @Operation(summary = "Listar grupos con posiciones del torneo")
        @GetMapping("/{id}/grupos")
        public ResponseEntity<List<GrupoResponse>> listarGrupos(
                        @Parameter(description = "ID del torneo") @PathVariable Long id) {
                return ResponseEntity.ok(torneoService.listarGruposConPosiciones(id));
        }

}
