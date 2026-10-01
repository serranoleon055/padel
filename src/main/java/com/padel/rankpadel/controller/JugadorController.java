package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.padel.rankpadel.dto.request.BatchDeleteRequest;
import com.padel.rankpadel.dto.request.JugadorRequest;
import com.padel.rankpadel.dto.response.JugadorBusquedaResponse;
import com.padel.rankpadel.dto.response.JugadorFichaResponse;
import com.padel.rankpadel.dto.response.JugadorHistorialResponse;
import com.padel.rankpadel.dto.response.JugadorResponse;
import com.padel.rankpadel.dto.response.PagedResponse;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.service.JugadorService;
import com.padel.rankpadel.util.UsuarioActual;

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
@RequestMapping("/api/jugadores")
@Tag(name = "Jugadores", description = "Gestión de jugadores. Consultas son públicas, modificaciones requieren JWT.")
public class JugadorController {

    private final JugadorService jugadorService;

    @SecurityRequirements({})
    @Operation(summary = "Listar todos los jugadores (sin paginar — para compatibilidad)")
    @ApiResponse(responseCode = "200", description = "Lista de jugadores")
    @GetMapping
    public ResponseEntity<List<JugadorResponse>> listarTodos() {
        return ResponseEntity.ok(jugadorService.listarTodos());
    }

    /**
     * Listado paginado con los filtros resueltos en la base.
     *
     * <p>Antes esto traía la tabla entera y la cortaba en memoria, que es paginar de
     * mentira: el costo de la consulta y el tamaño de la respuesta eran los mismos.
     */
    @SecurityRequirements({})
    @Operation(summary = "Listar jugadores paginado", description = "pagina inicia en 0. tamanio por defecto 20.")
    @GetMapping("/paginado")
    public ResponseEntity<PagedResponse<JugadorResponse>> listarPaginado(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Genero genero,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(defaultValue = "false") boolean incluirBajas,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {
        // Este endpoint es público: los jugadores dados de baja solo se listan para quien
        // entró al panel, que es el único que puede reactivarlos. Sin este filtro, mandar
        // el parámetro a mano desde afuera mostraba gente que el club decidió esconder.
        return ResponseEntity.ok(jugadorService.listarPagina(
                busqueda, genero, categoriaId, incluirBajas && UsuarioActual.esAdmin(), pagina, tamanio));
    }

    @SecurityRequirements({})
    @Operation(summary = "Historial completo de un jugador: partidos, torneos y ranking")
    @GetMapping("/{id}/historial")
    public ResponseEntity<JugadorHistorialResponse> historial(
            @Parameter(description = "ID del jugador") @PathVariable Long id) {
        return ResponseEntity.ok(jugadorService.obtenerHistorial(id));
    }

    @SecurityRequirements({})
    @Operation(summary = "Buscar jugadores por nombre (autocompletado público para inscripciones)")
    @GetMapping("/buscar")
    public ResponseEntity<List<JugadorBusquedaResponse>> buscar(@RequestParam String q) {
        return ResponseEntity.ok(jugadorService.buscar(q));
    }

    @Operation(summary = "Ficha admin del jugador (datos sensibles: fecha de nacimiento y teléfono). Requiere JWT.")
    @GetMapping("/{id}/ficha")
    public ResponseEntity<JugadorFichaResponse> ficha(
            @Parameter(description = "ID del jugador") @PathVariable Long id) {
        return ResponseEntity.ok(jugadorService.obtenerFicha(id));
    }

    @SecurityRequirements({})
    @Operation(summary = "Buscar jugador por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jugador encontrado", content = @Content(schema = @Schema(implementation = JugadorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Jugador no encontrado", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<JugadorResponse> buscarPorId(
            @Parameter(description = "ID del jugador") @PathVariable Long id) {
        return ResponseEntity.ok(jugadorService.buscarPorId(id));
    }

    @Operation(summary = "Crear jugador", description = "Requiere JWT de administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Jugador creado exitosamente", content = @Content(schema = @Schema(implementation = JugadorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o jugador duplicado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    @PostMapping
    public ResponseEntity<JugadorResponse> crear(@Valid @RequestBody JugadorRequest jugadorRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jugadorService.crear(jugadorRequest));
    }

    @Operation(summary = "Actualizar jugador", description = "Requiere JWT de administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jugador actualizado"),
            @ApiResponse(responseCode = "404", description = "Jugador no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<JugadorResponse> actualizar(
            @Parameter(description = "ID del jugador") @PathVariable Long id,
            @Valid @RequestBody JugadorRequest jugadorRequest) {
        return ResponseEntity.ok(jugadorService.actualizar(id, jugadorRequest));
    }

    @Operation(summary = "Subir foto de jugador", description = "Requiere JWT. Recibe multipart/form-data con el campo file.")
    @PostMapping(value = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JugadorResponse> subirFoto(
            @Parameter(description = "ID del jugador") @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(jugadorService.actualizarFoto(id, file));
    }

    @Operation(summary = "Quitar foto de jugador", description = "Requiere JWT. Elimina la foto cargada y deja al jugador sin imagen.")
    @DeleteMapping("/{id}/foto")
    public ResponseEntity<JugadorResponse> eliminarFoto(
            @Parameter(description = "ID del jugador") @PathVariable Long id) {
        return ResponseEntity.ok(jugadorService.eliminarFoto(id));
    }

    @Operation(summary = "Eliminar jugador", description = "Requiere JWT de administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Jugador eliminado"),
            @ApiResponse(responseCode = "404", description = "Jugador no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @Parameter(description = "ID del jugador") @PathVariable Long id) {
        jugadorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Eliminar jugadores en lote", description = "Requiere JWT.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Lista de IDs a eliminar", required = true)
    @PostMapping("/delete-batch")
    public ResponseEntity<?> eliminarBatch(@RequestBody BatchDeleteRequest request) {
        jugadorService.eliminarBatch(request.getIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivar jugador", description = "Requiere JWT. Devuelve al listado a un jugador dado de baja.")
    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<JugadorResponse> reactivar(
            @Parameter(description = "ID del jugador") @PathVariable Long id) {
        return ResponseEntity.ok(jugadorService.reactivar(id));
    }

}
