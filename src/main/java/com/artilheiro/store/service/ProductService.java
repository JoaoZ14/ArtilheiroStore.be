package com.artilheiro.store.service;

import com.artilheiro.store.dto.product.ProductRequest;
import com.artilheiro.store.dto.product.ProductResponse;
import com.artilheiro.store.model.Product;
import com.artilheiro.store.repository.ProductRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final SupabaseStorageService supabaseStorageService;

    public ProductService(ProductRepository productRepository, SupabaseStorageService supabaseStorageService) {
        this.productRepository = productRepository;
        this.supabaseStorageService = supabaseStorageService;
    }

    public List<ProductResponse> findAll(String category, String team, String liga, String search) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("active"), true));

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.toLowerCase()));
            }
            if (team != null && !team.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("team")), team.toLowerCase()));
            }
            if (liga != null && !liga.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("liga")), liga.toLowerCase()));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("team")), pattern),
                    cb.like(cb.lower(root.get("liga")), pattern),
                    cb.like(cb.lower(root.get("category")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return productRepository.findAll(spec).stream()
            .map(this::toResponse)
            .toList();
    }

    public Optional<ProductResponse> findById(UUID id) {
        return productRepository.findById(id)
                .filter(Product::getActive)
                .map(this::toResponse);
    }

    /**
     * Lista todos os produtos, inclusive inativos (admin).
     */
    public List<ProductResponse> findAllForAdmin() {
        return productRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Atualiza um produto existente (admin). Novas imagens substituem as atuais se enviadas.
     */
    public Optional<ProductResponse> update(UUID id, ProductRequest request, List<MultipartFile> images) throws IOException {
        return productRepository.findById(id)
                .map(product -> {
                    product.setName(request.getName());
                    product.setTeam(request.getTeam());
                    product.setLiga(request.getLiga());
                    product.setCategory(request.getCategory());
                    product.setPrice(request.getPrice());
                    product.setSizes(request.getSizes());
                    product.setActive(request.getActive() != null ? request.getActive() : true);

                    if (images != null && !images.isEmpty()) {
                        List<String> imageUrls = new ArrayList<>();
                        for (MultipartFile file : images) {
                            if (file == null || file.isEmpty()) continue;
                            try {
                                String path = supabaseStorageService.buildProductImagePath(product.getId(), file);
                                String publicUrl = supabaseStorageService.upload(file, path);
                                imageUrls.add(publicUrl);
                            } catch (IOException e) {
                                throw new RuntimeException("Erro ao enviar imagem: " + e.getMessage());
                            }
                        }
                        if (!imageUrls.isEmpty()) {
                            product.setImages(imageUrls);
                        }
                    }

                    productRepository.save(product);
                    return toResponse(product);
                });
    }

    /**
     * Cadastra um novo produto e envia as imagens para o bucket Supabase (CamisaImages).
     *
     * @param request dados do produto
     * @param images  arquivos de imagem (opcional; se vazio, salva com lista vazia)
     * @return produto criado
     */
    public ProductResponse create(ProductRequest request, List<MultipartFile> images) throws IOException {
        UUID productId = UUID.randomUUID();
        List<String> imageUrls = new ArrayList<>();

        if (images != null && !images.isEmpty()) {
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) continue;
                String path = supabaseStorageService.buildProductImagePath(productId, file);
                String publicUrl = supabaseStorageService.upload(file, path);
                imageUrls.add(publicUrl);
            }
        }

        Product product = new Product();
        product.setId(productId);
        product.setName(request.getName());
        product.setTeam(request.getTeam());
        product.setLiga(request.getLiga());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setImages(imageUrls);
        product.setSizes(request.getSizes());
        product.setActive(request.getActive());
        product.setCreatedAt(LocalDateTime.now());

        productRepository.save(product);
        return toResponse(product);
    }

    private ProductResponse toResponse(Product product) {
        ProductResponse dto = new ProductResponse();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setTeam(product.getTeam());
        dto.setLiga(product.getLiga());
        dto.setCategory(product.getCategory());
        dto.setPrice(product.getPrice());
        dto.setImages(product.getImages());
        dto.setSizes(product.getSizes());
        dto.setActive(product.getActive());
        return dto;
    }
}
