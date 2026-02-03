package com.artilheiro.store.service;

import com.artilheiro.store.dto.order.OrderLookupItemResponse;
import com.artilheiro.store.dto.order.OrderLookupResponse;
import com.artilheiro.store.dto.order.OrderRequest;
import com.artilheiro.store.dto.order.OrderResponse;
import com.artilheiro.store.dto.order.OrderUpdateRequest;
import com.artilheiro.store.dto.order.PaymentCreateRequest;
import com.artilheiro.store.dto.order.PaymentCreateResponse;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentPointOfInteraction;
import com.mercadopago.resources.payment.PaymentTransactionData;
import com.mercadopago.resources.payment.PaymentTransactionDetails;
import com.artilheiro.store.model.Order;
import com.artilheiro.store.model.Product;
import com.artilheiro.store.repository.OrderRepository;
import com.artilheiro.store.repository.ProductRepository;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final String ORDER_PREFIX = "ART-";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MercadoPagoService mercadoPagoService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        MercadoPagoService mercadoPagoService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.mercadoPagoService = mercadoPagoService;
    }

    /**
     * Cria apenas o pedido (Checkout Transparente).
     * O frontend deve depois chamar POST /api/orders/{orderId}/payments com o token do cartão.
     */
    @Transactional
    public OrderResponse create(OrderRequest request) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber(generateOrderNumber());
        order.setCustomerName(request.getCustomer().getName());
        order.setEmail(request.getCustomer().getEmail());
        order.setCpf(request.getCustomer().getCpf());
        order.setAddress(toAddressMap(request.getAddress()));
        List<Map<String, Object>> items = toItemsMapList(request.getItems());
        order.setItems(items);
        order.setTotal(calculateOrderTotal(items));
        order.setStatus(Order.OrderStatus.PAYMENT_PENDING);
        order.setCreatedAt(LocalDateTime.now());

        orderRepository.save(order);

        return new OrderResponse(
                order.getOrderNumber(),
                order.getStatus().name(),
                null,
                null
        );
    }

    /**
     * Cria o pagamento no Mercado Pago (Checkout Transparente): cartão, PIX ou boleto.
     */
    @Transactional
    public PaymentCreateResponse createPaymentForOrder(String orderNumber, PaymentCreateRequest paymentRequest) throws MPException, MPApiException {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + orderNumber));
        if (order.getStatus() != Order.OrderStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Pedido não está pendente de pagamento: " + orderNumber);
        }

        PaymentCreateRequest.PayerDto payer = paymentRequest.getPayer();
        String paymentMethodId = paymentRequest.getPaymentMethodId() != null ? paymentRequest.getPaymentMethodId().trim().toLowerCase() : "";
        Payment createPayment;

        if ("pix".equals(paymentMethodId)) {
            createPayment = mercadoPagoService.createPaymentPix(
                    order.getTotal(),
                    "Pedido " + order.getOrderNumber(),
                    order.getOrderNumber(),
                    payer.getEmail(),
                    payer.getName(),
                    payer.getIdentification().getType(),
                    payer.getIdentification().getNumber()
            );
        } else if ("bolbradesco".equals(paymentMethodId)) {
            PaymentCreateRequest.AddressDto addr = payer.getAddress();
            if (addr == null) {
                throw new IllegalArgumentException("Para boleto, payer.address é obrigatório (streetName, streetNumber, zipCode, city, federalUnit).");
            }
            createPayment = mercadoPagoService.createPaymentBoleto(
                    order.getTotal(),
                    "Pedido " + order.getOrderNumber(),
                    order.getOrderNumber(),
                    payer.getEmail(),
                    payer.getName(),
                    payer.getIdentification().getType(),
                    payer.getIdentification().getNumber(),
                    addr.getStreetName(),
                    addr.getStreetNumber(),
                    addr.getZipCode(),
                    addr.getCity(),
                    addr.getFederalUnit()
            );
        } else {
            if (paymentRequest.getToken() == null || paymentRequest.getToken().isBlank()) {
                throw new IllegalArgumentException("Para pagamento com cartão, token é obrigatório.");
            }
            int installments = paymentRequest.getInstallments() != null && paymentRequest.getInstallments() >= 1
                    ? paymentRequest.getInstallments().intValue()
                    : 1;
            createPayment = mercadoPagoService.createPayment(
                    order.getTotal(),
                    paymentRequest.getToken(),
                    paymentRequest.getPaymentMethodId(),
                    installments,
                    "Pedido " + order.getOrderNumber(),
                    order.getOrderNumber(),
                    payer.getEmail(),
                    payer.getName(),
                    payer.getIdentification().getType(),
                    payer.getIdentification().getNumber(),
                    paymentRequest.getIssuerId()
            );
        }

        if ("approved".equals(createPayment.getStatus())) {
            order.setStatus(Order.OrderStatus.RECEIVED);
            order.setPaymentId(String.valueOf(createPayment.getId()));
            orderRepository.save(order);
        }

        PaymentCreateResponse response = new PaymentCreateResponse(
                createPayment.getId(),
                createPayment.getStatus(),
                createPayment.getStatusDetail() != null ? createPayment.getStatusDetail() : "",
                order.getOrderNumber()
        );
        fillPixAndBoletoResponse(createPayment, response);
        // Às vezes o create não retorna point_of_interaction; busca o pagamento de novo para obter QR/ticketUrl
        boolean needsQrOrTicket = "pix".equals(paymentMethodId) || "bolbradesco".equals(paymentMethodId);
        if (needsQrOrTicket && createPayment.getId() != null
                && (response.getQrCode() == null && response.getQrCodeBase64() == null && response.getTicketUrl() == null)) {
            try {
                Payment fullPayment = mercadoPagoService.getPayment(createPayment.getId());
                fillPixAndBoletoResponse(fullPayment, response);
            } catch (Exception ignored) {
                // mantém a resposta já preenchida
            }
        }
        return response;
    }

    /** Preenche qrCode, qrCodeBase64 e ticketUrl a partir da resposta do Mercado Pago (PIX e boleto). */
    private void fillPixAndBoletoResponse(Payment payment, PaymentCreateResponse response) {
        if (payment == null || response == null) return;
        PaymentPointOfInteraction poi = payment.getPointOfInteraction();
        if (poi != null && poi.getTransactionData() != null) {
            PaymentTransactionData tdata = poi.getTransactionData();
            if (tdata.getQrCodeBase64() != null && !tdata.getQrCodeBase64().isBlank()) {
                response.setQrCodeBase64(tdata.getQrCodeBase64());
            }
            if (tdata.getQrCode() != null && !tdata.getQrCode().isBlank()) {
                response.setQrCode(tdata.getQrCode());
            }
            if (tdata.getTicketUrl() != null && !tdata.getTicketUrl().isBlank()) {
                response.setTicketUrl(tdata.getTicketUrl());
            }
        }
        if (response.getTicketUrl() == null || response.getTicketUrl().isBlank()) {
            PaymentTransactionDetails details = payment.getTransactionDetails();
            if (details != null && details.getExternalResourceUrl() != null && !details.getExternalResourceUrl().isBlank()) {
                response.setTicketUrl(details.getExternalResourceUrl());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<OrderLookupResponse> listAll() {
        return orderRepository.findAll().stream()
                .map(this::toLookupResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<OrderLookupResponse> lookupOrder(String email, String code) {
        return orderRepository.findByOrderNumberAndEmailIgnoreCase(code, email)
                .map(this::toLookupResponse);
    }

    /**
     * Processa notificação webhook do Mercado Pago.
     * Se type == "payment" e status do pagamento == "approved", atualiza o pedido para RECEIVED e grava payment_id.
     */
    @Transactional
    public void processMercadoPagoWebhook(String type, String paymentIdStr) throws MPException, MPApiException {
        if (type == null || !"payment".equals(type.trim()) || paymentIdStr == null || paymentIdStr.isBlank()) {
            return;
        }
        Long paymentId;
        try {
            paymentId = Long.parseLong(paymentIdStr.trim());
        } catch (NumberFormatException e) {
            return;
        }
        Payment payment = mercadoPagoService.getPayment(paymentId);
        if (payment == null) {
            return;
        }
        if (!"approved".equals(payment.getStatus())) {
            return;
        }
        String externalReference = payment.getExternalReference();
        if (externalReference == null || externalReference.isBlank()) {
            return;
        }
        orderRepository.findByOrderNumber(externalReference.trim()).ifPresent(order -> {
            if (order.getStatus() == Order.OrderStatus.PAYMENT_PENDING) {
                order.setStatus(Order.OrderStatus.RECEIVED);
                order.setPaymentId(String.valueOf(payment.getId()));
                orderRepository.save(order);
            }
        });
    }

    /**
     * Sincroniza o status do pedido com o pagamento no Mercado Pago.
     * Se o pagamento estiver aprovado e o external_reference bater com o pedido, atualiza para RECEIVED.
     * Retorna true se o pedido foi atualizado, false caso contrário.
     */
    @Transactional
    public boolean syncOrderPaymentStatus(String orderNumber, Long paymentId) throws MPException, MPApiException {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + orderNumber));
        if (order.getStatus() == Order.OrderStatus.RECEIVED) {
            return false;
        }
        Payment payment = mercadoPagoService.getPayment(paymentId);
        if (payment == null || !"approved".equals(payment.getStatus())) {
            return false;
        }
        String externalRef = payment.getExternalReference();
        if (externalRef == null || !externalRef.trim().equals(orderNumber)) {
            return false;
        }
        order.setStatus(Order.OrderStatus.RECEIVED);
        order.setPaymentId(String.valueOf(payment.getId()));
        orderRepository.save(order);
        return true;
    }

    @Transactional
    public Optional<OrderLookupResponse> updateOrder(UUID id, OrderUpdateRequest request) {
        return orderRepository.findById(id)
                .map(order -> {
                    if (request.getStatus() != null && !request.getStatus().isBlank()) {
                        try {
                            order.setStatus(Order.OrderStatus.valueOf(request.getStatus().trim().toUpperCase()));
                        } catch (IllegalArgumentException ignored) {
                            // mantém status atual se valor inválido
                        }
                    }
                    if (request.getShippedAt() != null) {
                        order.setShippedAt(request.getShippedAt());
                    }
                    if (request.getCarrier() != null) {
                        order.setCarrier(request.getCarrier().isBlank() ? null : request.getCarrier().trim());
                    }
                    if (request.getTrackingCode() != null) {
                        order.setTrackingCode(request.getTrackingCode().isBlank() ? null : request.getTrackingCode().trim());
                    }
                    if (request.getTrackingUrl() != null) {
                        order.setTrackingUrl(request.getTrackingUrl().isBlank() ? null : request.getTrackingUrl().trim());
                    }
                    orderRepository.save(order);
                    return toLookupResponse(order);
                });
    }

    private OrderLookupResponse toLookupResponse(Order order) {
        List<Map<String, Object>> orderItems = order.getItems() != null ? order.getItems() : List.of();
        List<OrderLookupItemResponse> items = orderItems.stream()
                .map(this::toLookupItemResponse)
                .toList();
        return new OrderLookupResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getTotal(),
                order.getCreatedAt().toLocalDate(),
                order.getShippedAt(),
                order.getCarrier(),
                order.getTrackingCode(),
                order.getTrackingUrl(),
                items
        );
    }

    private OrderLookupItemResponse toLookupItemResponse(Map<String, Object> map) {
        String productId = map.get("productId") != null ? map.get("productId").toString() : null;
        String name = map.get("name") != null ? map.get("name").toString() : null;
        String image = map.get("image") != null ? map.get("image").toString() : null;
        List<String> images = toImagesList(map.get("images"));
        String team = map.get("team") != null ? map.get("team").toString() : null;
        String liga = map.get("liga") != null ? map.get("liga").toString() : null;
        String category = map.get("category") != null ? map.get("category").toString() : null;
        String size = map.get("size") != null ? map.get("size").toString() : null;
        Integer quantity = toInteger(map.get("quantity"));
        BigDecimal price = toBigDecimal(map.get("unitPrice"));
        return new OrderLookupItemResponse(productId, name, image, images, team, liga, category, size, quantity, price);
    }

    @SuppressWarnings("unchecked")
    private List<String> toImagesList(Object value) {
        if (value == null) return null;
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(e -> e != null)
                    .map(Object::toString)
                    .toList();
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String generateOrderNumber() {
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        LocalDateTime startOfYear = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime endOfYear = LocalDateTime.of(year, 12, 31, 23, 59, 59);
        long count = orderRepository.countByCreatedAtBetween(startOfYear, endOfYear);
        return ORDER_PREFIX + year + "-" + String.format("%04d", count + 1);
    }

    private Map<String, Object> toAddressMap(OrderRequest.AddressDto address) {
        Map<String, Object> map = new HashMap<>();
        map.put("cep", address.getCep());
        map.put("rua", address.getRua());
        map.put("numero", address.getNumero());
        map.put("complemento", address.getComplemento() != null ? address.getComplemento() : "");
        map.put("cidade", address.getCidade());
        map.put("estado", address.getEstado());
        return map;
    }

    private List<Map<String, Object>> toItemsMapList(List<OrderRequest.ItemDto> items) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (OrderRequest.ItemDto item : items) {
            Map<String, Object> map = new HashMap<>();
            String productIdStr = item.getProductId();
            map.put("productId", productIdStr);
            map.put("size", item.getSize());
            map.put("quantity", item.getQuantity());
            BigDecimal unitPrice = item.getUnitPrice();

            try {
                UUID productId = UUID.fromString(productIdStr);
                productRepository.findById(productId).ifPresent(product -> {
                    map.put("name", product.getName());
                    List<String> images = product.getImages() != null ? product.getImages() : Collections.emptyList();
                    map.put("images", images);
                    if (!images.isEmpty()) {
                        map.put("image", images.get(0));
                    }
                    map.put("team", product.getTeam());
                    map.put("liga", product.getLiga());
                    map.put("category", product.getCategory());
                    BigDecimal effectivePrice = product.getPromoPrice() != null && product.getPromoPrice().compareTo(BigDecimal.ZERO) > 0
                            ? product.getPromoPrice() : product.getPrice();
                    map.put("unitPrice", effectivePrice);
                });
            } catch (IllegalArgumentException ignored) {
                // productId inválido: mantém só os dados enviados
            }
            if (!map.containsKey("unitPrice")) {
                map.put("unitPrice", unitPrice);
            }
            list.add(map);
        }
        return list;
    }

    private BigDecimal calculateOrderTotal(List<Map<String, Object>> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            BigDecimal price = toBigDecimal(item.get("unitPrice"));
            Integer qty = toInteger(item.get("quantity"));
            if (price != null && qty != null && qty > 0) {
                total = total.add(price.multiply(BigDecimal.valueOf(qty)));
            }
        }
        return total;
    }
}
