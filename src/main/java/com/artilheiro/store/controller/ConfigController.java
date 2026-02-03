package com.artilheiro.store.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Configuração pública para o frontend (ex.: chave pública do Mercado Pago para tokenização).
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${mercadopago.public-key:}")
    private String mercadopagoPublicKey;

    @GetMapping
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "mercadopagoPublicKey", mercadopagoPublicKey != null ? mercadopagoPublicKey : ""
        ));
    }
}
