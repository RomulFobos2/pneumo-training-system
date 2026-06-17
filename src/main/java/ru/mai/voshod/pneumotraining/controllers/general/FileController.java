package ru.mai.voshod.pneumotraining.controllers.general;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.mai.voshod.pneumotraining.service.general.FileStorageService;

import java.nio.file.Path;

@Controller
@Slf4j
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable(value = "filename") String filename) {
        try {
            Path filePath = fileStorageService.getFilePath(filename);
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                log.warn("Файл не найден: {}", filename);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Ошибка при отдаче файла {}: {}", filename, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/files/images/{filename}")
    public ResponseEntity<Resource> serveImage(@PathVariable(value = "filename") String filename) {
        try {
            Path filePath = fileStorageService.getImagePath(filename);
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                log.warn("Картинка не найдена: {}", filename);
                return ResponseEntity.notFound().build();
            }
            MediaType mediaType = mediaTypeForExtension(filename);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Ошибка при отдаче картинки {}: {}", filename, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    private MediaType mediaTypeForExtension(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}