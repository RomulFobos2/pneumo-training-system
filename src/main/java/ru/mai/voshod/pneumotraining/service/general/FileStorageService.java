package ru.mai.voshod.pneumotraining.service.general;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir:uploads/materials}")
    private String uploadDir;

    private Path uploadPath;
    private Path imagePath;

    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final java.util.Set<String> ALLOWED_IMAGE_EXT =
            java.util.Set.of("png", "jpg", "jpeg", "gif", "webp");

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        imagePath = uploadPath.resolve("images");
        try {
            Files.createDirectories(uploadPath);
            Files.createDirectories(imagePath);
            log.info("Директория для загрузок: {}", uploadPath);
            log.info("Директория для картинок: {}", imagePath);
        } catch (IOException e) {
            log.error("Не удалось создать директорию для загрузок: {}", e.getMessage(), e);
        }
    }

    public String saveFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.error("Файл пуст или не передан");
            return null;
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            log.error("Недопустимый тип файла: {}", originalFilename);
            return null;
        }

        try {
            String filename = UUID.randomUUID() + ".pdf";
            Path targetPath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Файл сохранён: {}", filename);
            return filename;
        } catch (IOException e) {
            log.error("Ошибка при сохранении файла: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean deleteFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        try {
            Path filePath = uploadPath.resolve(filename).normalize();
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Файл удалён: {}", filename);
                return true;
            }
            log.warn("Файл не найден для удаления: {}", filename);
            return false;
        } catch (IOException e) {
            log.error("Ошибка при удалении файла: {}", e.getMessage(), e);
            return false;
        }
    }

    public Path getFilePath(String filename) {
        return uploadPath.resolve(filename).normalize();
    }

    public Path getImagePath(String filename) {
        return imagePath.resolve(filename).normalize();
    }

    /**
     * Сохраняет картинку в подпапку images/ и возвращает имя файла (UUID.ext)
     * или null, если файл не прошёл валидацию.
     */
    public String saveImage(MultipartFile file) throws ImageUploadException {
        if (file == null || file.isEmpty()) {
            throw new ImageUploadException("Файл пуст или не передан");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new ImageUploadException("Размер картинки превышает 5 МБ");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new ImageUploadException("Не удалось определить тип файла");
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_IMAGE_EXT.contains(ext)) {
            throw new ImageUploadException("Допустимы только форматы PNG, JPG, GIF, WebP");
        }

        try {
            String filename = UUID.randomUUID() + "." + ext;
            Path targetPath = imagePath.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Картинка сохранена: {}", filename);
            return filename;
        } catch (IOException e) {
            log.error("Ошибка при сохранении картинки: {}", e.getMessage(), e);
            throw new ImageUploadException("Внутренняя ошибка при сохранении файла");
        }
    }

    public static class ImageUploadException extends Exception {
        public ImageUploadException(String message) { super(message); }
    }
}