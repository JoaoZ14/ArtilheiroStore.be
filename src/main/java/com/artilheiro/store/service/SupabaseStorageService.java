package com.artilheiro.store.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Serviço para upload de arquivos no bucket Supabase Storage (CamisaImages).
 * Utiliza a REST API do Supabase.
 */
@Service
public class SupabaseStorageService {

    private static final String STORAGE_OBJECT_PATH = "/storage/v1/object/";
    private static final String PUBLIC_PATH = "/storage/v1/object/public/";

    private final RestTemplate restTemplate;
    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final String bucket;
    private final long maxFileSize;
    private final List<String> allowedContentTypes;

    public SupabaseStorageService(
            RestTemplate restTemplate,
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-role-key}") String serviceRoleKey,
            @Value("${supabase.storage.bucket}") String bucket,
            @Value("${app.upload.max-file-size:5242880}") long maxFileSize,
            @Value("${app.upload.allowed-content-types:image/jpeg,image/png,image/webp}") String allowedContentTypesStr) {
        this.restTemplate = restTemplate;
        this.supabaseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        this.serviceRoleKey = serviceRoleKey != null ? serviceRoleKey.trim() : null;
        this.bucket = bucket;
        this.maxFileSize = maxFileSize;
        this.allowedContentTypes = Arrays.stream(allowedContentTypesStr.split(","))
                .map(String::trim)
                .toList();
    }

    /**
     * Faz upload de um arquivo para o bucket no path especificado.
     *
     * @param file       arquivo enviado
     * @param objectPath path no bucket (ex.: "produtos/uuid/nome.jpg")
     * @return URL pública do arquivo no Supabase
     */
    public String upload(MultipartFile file, String objectPath) throws IOException {
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new IllegalStateException("supabase.service-role-key não configurada. Defina SUPABASE_SERVICE_ROLE_KEY ou a propriedade no application.properties.");
        }
        validateFile(file);

        byte[] bytes = file.getBytes();
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        // Supabase Storage espera multipart/form-data (parte "file") + apikey e Bearer.
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            name = objectPath.substring(objectPath.lastIndexOf('/') + 1);
        }
        final String filename = name;
        Resource fileResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("apikey", serviceRoleKey);
        headers.set("Authorization", "Bearer " + serviceRoleKey);

        String url = supabaseUrl + STORAGE_OBJECT_PATH + bucket + "/" + objectPath;
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        var response = restTemplate.postForEntity(url, entity, String.class);
        if (!response.getStatusCode().equals(HttpStatus.OK)) {
            throw new IOException("Falha no upload para Supabase: " + response.getStatusCode());
        }

        return supabaseUrl + PUBLIC_PATH + bucket + "/" + objectPath;
    }

    /**
     * Gera um path único para a imagem do produto.
     */
    public String buildProductImagePath(UUID productId, MultipartFile file) {
        String ext = getFileExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + (ext != null ? "." + ext : "");
        return "produtos/" + productId + "/" + filename;
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo não pode ser vazio");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("Arquivo excede o tamanho máximo permitido (" + (maxFileSize / 1024 / 1024) + " MB)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Use: " + String.join(", ", allowedContentTypes));
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
