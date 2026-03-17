package ru.mai.voshod.pneumotraining.controllers.general;

import com.mai.siarsp.models.Product;
import com.mai.siarsp.service.employee.warehouseManager.ProductService;
import com.mai.siarsp.service.general.ImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.util.Optional;

@Controller
@Slf4j
public class ImageController {

    private final ProductService productService;

    public ImageController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/image/{product_id}")
    public ResponseEntity<Resource> getImage(@PathVariable long product_id) {
        Optional<Product> productOptional = productService.getProductRepository().findById(product_id);

        if (productOptional.isEmpty() || productOptional.get().getImage() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        try {
            Resource imageResource = ImageService.getImageData(productOptional.get().getImage());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(imageResource);
        } catch (IOException e) {
            log.error("Ошибка загрузки изображения для товара id={}: {}", product_id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }
}
