package com.padel.rankpadel.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.padel.rankpadel.exception.EstadoInvalidoException;

@Service
public class ImageStorageService {

    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SIDE = 800;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.public-base-url:/uploads}")
    private String publicBaseUrl;

    public String guardarJugadorFoto(Long jugadorId, MultipartFile file) {
        return guardar("jugadores", "jugador-" + jugadorId, file);
    }

    public String guardarTorneoImagen(Long torneoId, MultipartFile file) {
        return guardar("torneos", "torneo-" + torneoId, file);
    }

    private String guardar(String carpeta, String prefijo, MultipartFile file) {
        validar(file);

        try {
            BufferedImage original = ImageIO.read(file.getInputStream());
            if (original == null) {
                throw new EstadoInvalidoException("El archivo no es una imagen válida");
            }

            BufferedImage resized = resize(original);
            Path directory = Path.of(uploadDir, carpeta).toAbsolutePath().normalize();
            Files.createDirectories(directory);

            String filename = prefijo + "-" + UUID.randomUUID() + ".jpg";
            Path target = directory.resolve(filename).normalize();
            ImageIO.write(resized, "jpg", target.toFile());

            return publicBaseUrl.replaceAll("/+$", "") + "/" + carpeta + "/" + filename;
        } catch (IOException e) {
            throw new EstadoInvalidoException("No se pudo procesar la imagen");
        }
    }

    /** Borra el archivo físico asociado a una URL pública previamente generada. No falla si no existe. */
    public void borrarPorUrl(String url) {
        if (url == null) return;
        String base = publicBaseUrl.replaceAll("/+$", "");
        if (!url.startsWith(base + "/")) return;
        String relativo = url.substring(base.length() + 1);
        try {
            Path target = Path.of(uploadDir).toAbsolutePath().normalize().resolve(relativo).normalize();
            Path root = Path.of(uploadDir).toAbsolutePath().normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
            // Borrado best-effort: si el archivo no se puede eliminar, no interrumpimos la operación.
        }
    }

    private void validar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EstadoInvalidoException("La imagen es obligatoria");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new EstadoInvalidoException("La imagen no puede superar 2 MB");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new EstadoInvalidoException("La imagen debe ser JPG o PNG");
        }
    }

    private BufferedImage resize(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();

        return output;
    }
}
