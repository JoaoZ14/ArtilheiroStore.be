package com.artilheiro.store.controller;

import com.artilheiro.store.dto.product.ProductRequest;
import com.artilheiro.store.dto.product.ProductResponse;
import com.artilheiro.store.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String liga,
            @RequestParam(required = false) String search) {
        return productService.findAll(category, team, liga, search);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        return productService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Cadastra um novo produto e envia as imagens para o bucket Supabase (CamisaImages).
     * Request: multipart/form-data com part "product" (JSON) e part "images" (arquivos).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestPart("product") ProductRequest product,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) throws IOException {
        ProductResponse created = productService.create(product, images);
        return ResponseEntity.status(201).body(created);
    }
}
