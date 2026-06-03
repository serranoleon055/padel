package com.padel.rankpadel.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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

    // Cloudinary (opcional). Si cloud-name está definido, se usa Cloudinary en lugar del disco.
    @Value("${app.cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret:}")
    private String apiSecret;

    private volatile Cloudinary cloudinary;

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

            if (cloudinaryHabilitado()) {
                return subirACloudinary(resized, carpeta, prefijo);
            }
            return guardarEnDisco(resized, carpeta, prefijo);
        } catch (IOException e) {
            throw new EstadoInvalidoException("No se pudo procesar la imagen");
        }
    }

    private String guardarEnDisco(BufferedImage resized, String carpeta, String prefijo) throws IOException {
        Path directory = Path.of(uploadDir, carpeta).toAbsolutePath().normalize();
        Files.createDirectories(directory);

        String filename = prefijo + "-" + UUID.randomUUID() + ".jpg";
        Path target = directory.resolve(filename).normalize();
        ImageIO.write(resized, "jpg", target.toFile());

        return publicBaseUrl.replaceAll("/+$", "") + "/" + carpeta + "/" + filename;
    }

    private String subirACloudinary(BufferedImage resized, String carpeta, String prefijo) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resized, "jpg", baos);

        Map<String, Object> opciones = ObjectUtils.asMap(
                "folder", "rankpadel/" + carpeta,
                "public_id", prefijo + "-" + UUID.randomUUID(),
                "resource_type", "image",
                "overwrite", true);

        Map<?, ?> resultado = getCloudinary().uploader().upload(baos.toByteArray(), opciones);
        Object secureUrl = resultado.get("secure_url");
        if (secureUrl == null) {
            throw new EstadoInvalidoException("No se pudo subir la imagen");
        }
        return secureUrl.toString();
    }

    /** Borra la imagen asociada a una URL pública previamente generada (Cloudinary o disco). Best-effort. */
    public void borrarPorUrl(String url) {
        if (url == null) return;

        if (url.contains("res.cloudinary.com")) {
            borrarDeCloudinary(url);
            return;
        }

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

    private void borrarDeCloudinary(String url) {
        try {
            String publicId = extraerPublicId(url);
            if (publicId != null) {
                getCloudinary().uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
            }
        } catch (Exception ignored) {
            // Best-effort: no interrumpimos la operación si el borrado remoto falla.
        }
    }

    /**
     * Extrae el public_id de una URL de Cloudinary.
     * Ej: https://res.cloudinary.com/demo/image/upload/v123/rankpadel/jugadores/x.jpg
     *     -> rankpadel/jugadores/x
     */
    private String extraerPublicId(String url) {
        int idx = url.indexOf("/upload/");
        if (idx < 0) return null;
        String resto = url.substring(idx + "/upload/".length());
        resto = resto.replaceFirst("^v\\d+/", ""); // quitar versión
        int punto = resto.lastIndexOf('.');
        if (punto > 0) resto = resto.substring(0, punto); // quitar extensión
        return resto;
    }

    private boolean cloudinaryHabilitado() {
        return cloudName != null && !cloudName.isBlank();
    }

    private Cloudinary getCloudinary() {
        if (cloudinary == null) {
            synchronized (this) {
                if (cloudinary == null) {
                    cloudinary = new Cloudinary(ObjectUtils.asMap(
                            "cloud_name", cloudName,
                            "api_key", apiKey,
                            "api_secret", apiSecret,
                            "secure", true));
                }
            }
        }
        return cloudinary;
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
        // El Content-Type es falsificable: validamos también la cabecera real del archivo.
        if (!esJpegOPng(file)) {
            throw new EstadoInvalidoException("El archivo no es una imagen JPG o PNG válida");
        }
    }

    /** Verifica los magic bytes: JPEG (FF D8 FF) o PNG (89 50 4E 47 0D 0A 1A 0A). */
    private boolean esJpegOPng(MultipartFile file) {
        try {
            byte[] head = new byte[8];
            int leidos;
            try (var in = file.getInputStream()) {
                leidos = in.read(head);
            }
            if (leidos < 3) {
                return false;
            }
            boolean esJpeg = (head[0] & 0xFF) == 0xFF
                    && (head[1] & 0xFF) == 0xD8
                    && (head[2] & 0xFF) == 0xFF;
            boolean esPng = leidos >= 8
                    && (head[0] & 0xFF) == 0x89 && (head[1] & 0xFF) == 0x50
                    && (head[2] & 0xFF) == 0x4E && (head[3] & 0xFF) == 0x47
                    && (head[4] & 0xFF) == 0x0D && (head[5] & 0xFF) == 0x0A
                    && (head[6] & 0xFF) == 0x1A && (head[7] & 0xFF) == 0x0A;
            return esJpeg || esPng;
        } catch (IOException e) {
            return false;
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
