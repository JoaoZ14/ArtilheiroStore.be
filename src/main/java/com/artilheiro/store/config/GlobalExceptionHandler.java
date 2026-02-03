package com.artilheiro.store.config;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Trata exceções da API do Mercado Pago e token não configurado,
 * retornando mensagens claras para o frontend.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        if (e.getMessage() != null && e.getMessage().contains("MERCADOPAGO_ACCESS_TOKEN")) {
            log.warn("Checkout falhou: token do Mercado Pago não configurado");
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "message", "Pagamento temporariamente indisponível. Configure MERCADOPAGO_ACCESS_TOKEN.",
                            "code", "MP_TOKEN_MISSING"));
        }
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MPApiException.class)
    public ResponseEntity<Map<String, String>> handleMPApiException(MPApiException e) {
        String detail = e.getMessage();
        if (e.getApiResponse() != null && e.getApiResponse().getContent() != null) {
            detail = e.getApiResponse().getContent();
            log.error("Mercado Pago API error: status={} body={}", e.getApiResponse().getStatusCode(), detail);
        } else {
            log.error("Mercado Pago API error: {}", e.getMessage());
        }
        Integer apiStatus = e.getApiResponse() != null ? e.getApiResponse().getStatusCode() : null;
        int statusCode = apiStatus != null ? apiStatus : 502;
        HttpStatus status = statusCode == 401 ? HttpStatus.UNAUTHORIZED
                : statusCode == 400 || statusCode == 422 ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "message", "Erro ao criar checkout no Mercado Pago. Verifique o token e os dados do pedido.",
                        "detail", detail != null ? detail : "Api error"));
    }

    @ExceptionHandler(MPException.class)
    public ResponseEntity<Map<String, String>> handleMPException(MPException e) {
        log.error("Mercado Pago error: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "Erro ao comunicar com o Mercado Pago.", "detail", e.getMessage()));
    }
}
