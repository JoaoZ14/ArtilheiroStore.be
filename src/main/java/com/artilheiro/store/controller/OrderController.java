package com.artilheiro.store.controller;

import com.artilheiro.store.dto.order.MercadoPagoWebhookPayload;
import com.artilheiro.store.dto.order.OrderLookupResponse;
import com.artilheiro.store.dto.order.OrderRequest;
import com.artilheiro.store.dto.order.OrderResponse;
import com.artilheiro.store.dto.order.OrderUpdateRequest;
import com.artilheiro.store.dto.order.PaymentCreateRequest;
import com.artilheiro.store.dto.order.PaymentCreateResponse;
import com.artilheiro.store.service.MercadoPagoWebhookSignatureValidator;
import com.artilheiro.store.service.OrderService;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final String MSG_INVALID_PARAMS = "Confira os dados e tente novamente";
    private static final String MSG_NOT_FOUND = "Pedido não encontrado";

    private final OrderService orderService;

    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody OrderRequest request) {
        return orderService.create(request);
    }

    /**
     * Cria o pagamento do pedido (Checkout Transparente).
     * O frontend envia o token do cartão obtido pelo SDK do Mercado Pago.
     */
    @PostMapping("/{orderNumber}/payments")
    public ResponseEntity<?> createPayment(
            @PathVariable String orderNumber,
            @Valid @RequestBody PaymentCreateRequest request) {
        try {
            PaymentCreateResponse response = orderService.createPaymentForOrder(orderNumber, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            boolean orderNotFound = e.getMessage() != null && e.getMessage().startsWith("Pedido não encontrado");
            if (orderNotFound) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", MSG_NOT_FOUND));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage() != null ? e.getMessage() : MSG_INVALID_PARAMS));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (MPApiException e) {
            String detail = e.getApiResponse() != null && e.getApiResponse().getContent() != null
                    ? e.getApiResponse().getContent()
                    : e.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "Erro no Mercado Pago", "detail", detail));
        } catch (MPException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Erro ao processar pagamento"));
        }
    }

    /**
     * Lista todos os pedidos (admin).
     */
    @GetMapping("/admin")
    public List<OrderLookupResponse> listAll() {
        return orderService.listAll();
    }

    /**
     * Atualiza status e/ou dados de envio de um pedido (admin).
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody OrderUpdateRequest request) {
        var result = orderService.updateOrder(id, request);
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", MSG_NOT_FOUND));
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", MSG_INVALID_PARAMS));
        }
        var result = orderService.lookupOrder(email.trim(), code.trim());
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", MSG_NOT_FOUND));
    }

    /**
     * Webhook do Mercado Pago (POST com JSON).
     * Aceita payment.created e payment.updated; atualiza o pedido quando o pagamento for aprovado.
     */
    @PostMapping("/webhook/mercadopago")
    public ResponseEntity<Void> mercadoPagoWebhookPost(
            @RequestBody MercadoPagoWebhookPayload payload,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId) {
        String type = payload != null ? payload.getType() : null;
        String paymentIdStr = payload != null && payload.getData() != null ? payload.getData().getId() : null;
        return processWebhook(type, paymentIdStr, xSignature, xRequestId);
    }

    /**
     * Webhook do Mercado Pago (GET com query params: topic=payment&id=...).
     * O MP pode notificar via GET; aceita e processa da mesma forma.
     */
    @GetMapping("/webhook/mercadopago")
    public ResponseEntity<Void> mercadoPagoWebhookGet(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String id) {
        String type = "payment".equals(topic != null ? topic.trim() : "") ? "payment" : null;
        return processWebhook(type, id, null, null);
    }

    private ResponseEntity<Void> processWebhook(String type, String paymentIdStr, String xSignature, String xRequestId) {
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (!MercadoPagoWebhookSignatureValidator.validate(webhookSecret, paymentIdStr, xRequestId, xSignature)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        try {
            orderService.processMercadoPagoWebhook(type, paymentIdStr);
        } catch (Exception e) {
            // Responde 200 para o MP não reenviar; em produção use log e métricas.
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Sincroniza o status do pedido com o Mercado Pago.
     * Útil quando o webhook não foi chamado (ex.: ambiente local). O frontend chama com o paymentId
     * retornado na criação do pagamento; se o pagamento estiver aprovado, o pedido é atualizado para RECEIVED.
     */
    @GetMapping("/{orderNumber}/sync-payment")
    public ResponseEntity<?> syncPaymentStatus(
            @PathVariable String orderNumber,
            @RequestParam Long paymentId) {
        try {
            boolean updated = orderService.syncOrderPaymentStatus(orderNumber, paymentId);
            return ResponseEntity.ok(Map.of("updated", updated, "orderNumber", orderNumber));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", MSG_NOT_FOUND));
        } catch (MPApiException e) {
            String detail = e.getApiResponse() != null && e.getApiResponse().getContent() != null
                    ? e.getApiResponse().getContent()
                    : e.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "Erro ao consultar Mercado Pago", "detail", detail));
        } catch (MPException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Erro ao consultar pagamento"));
        }
    }
}
