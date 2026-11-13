package com.rinda.backend.common;

import com.rinda.backend.config.RindaProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Keeps evidence photos in a private folder (spec §5.3: photos stored privately). The database stores only the
 * key ("problem-reports/uuid.jpg"); photos are served by an endpoint that checks who may see them.
 * Only real JPEG and PNG files are accepted, checked by their first bytes, not by the name the phone sent.
 */
@Component
public class PhotoStorage {

    public record StoredPhoto(byte[] bytes, String contentType) {
    }

    private final Path root;

    public PhotoStorage(RindaProperties properties) {
        this.root = Path.of(properties.uploads().dir()).toAbsolutePath().normalize();
    }

    /** Saves a photo and returns its key, or null when there is no photo. */
    public String save(String folder, MultipartFile photo) {
        return store(folder, photo, false);
    }

    /** Saves a plot document (e-title, portal screenshot, lease, consent): a JPEG, PNG or PDF. */
    public String saveDocument(String folder, MultipartFile document) {
        return store(folder, document, true);
    }

    private String store(String folder, MultipartFile photo, boolean allowPdf) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        byte[] bytes;
        try (InputStream in = photo.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        String extension = allowPdf && isPdf(bytes) ? "pdf" : extension(bytes, allowPdf);
        String key = folder + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return key;
    }

    public StoredPhoto read(String key) {
        try {
            byte[] bytes = Files.readAllBytes(resolve(key));
            return new StoredPhoto(bytes, contentType(key));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "Photo not found.");
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Photo key outside the uploads folder");
        }
        return path;
    }

    public static String contentType(String key) {
        if (key.endsWith(".pdf")) {
            return "application/pdf";
        }
        return key.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private static boolean isPdf(byte[] b) {
        return b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
    }

    private static String extension(byte[] b, boolean allowPdf) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (allowPdf) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_INVALID",
                    "The document must be a JPEG or PNG image, or a PDF.");
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_INVALID", "The photo must be a JPEG or PNG image.");
    }
}
