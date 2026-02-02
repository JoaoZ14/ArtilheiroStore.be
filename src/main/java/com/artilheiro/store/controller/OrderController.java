package com.artilheiro.store.controller;

import com.artilheiro.store.dto.order.MercadoPagoWebhookPayload;
import com.artilheiro.store.dto.order.OrderLookupResponse;
import com.artilheiro.store.dto.order.OrderRequest;
import com.artilheiro.store.dto.order.OrderResponse;
import com.artilheiro.store.dto.order.OrderUpdateRequest;
import com.artilheiro.store.service.OrderService;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody OrderRequest request) throws MPException, MPApiException {
        return orderService.create(request);
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
     * Webhook do Mercado Pago para notificações de pagamento.
     * Responde 200 rapidamente; o processamento atualiza o pedido quando o pagamento for aprovado.
     */
    @PostMapping("/webhook/mercadopago")
    public ResponseEntity<Void> mercadoPagoWebhook(@RequestBody MercadoPagoWebhookPayload payload) {
        String type = payload != null ? payload.getType() : null;
        String paymentId = payload != null && payload.getData() != null ? payload.getData().getId() : null;
        try {
            orderService.processMercadoPagoWebhook(type, paymentId);
        } catch (Exception e) {
            // Log e responde 200 para o MP não reenviar; o problema pode ser temporário.
            // Em produção, considere log estruturado e métricas.
        }
        return ResponseEntity.ok().build();
    }
}
