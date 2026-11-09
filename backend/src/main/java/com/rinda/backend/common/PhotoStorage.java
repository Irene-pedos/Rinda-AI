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
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        byte[] bytes;
        try (InputStream in = photo.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        String extension = extension(bytes);
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
            return new StoredPhoto(bytes, key.endsWith(".png") ? "image/png" : "image/jpeg");
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

    private static String extension(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_INVALID", "The photo must be a JPEG or PNG image.");
    }
}
