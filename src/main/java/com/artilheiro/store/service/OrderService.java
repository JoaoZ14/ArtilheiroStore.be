package com.artilheiro.store.service;

import com.artilheiro.store.dto.order.OrderLookupItemResponse;
import com.artilheiro.store.dto.order.OrderLookupResponse;
import com.artilheiro.store.dto.order.OrderRequest;
import com.artilheiro.store.dto.order.OrderResponse;
import com.artilheiro.store.dto.order.OrderUpdateRequest;
import com.mercadopago.resources.payment.Payment;
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

    @Transactional
    public OrderResponse create(OrderRequest request) throws MPException, MPApiException {
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

        MercadoPagoService.PreferenceResult preference = mercadoPagoService.createPreference(
                order.getOrderNumber(),
                order.getItems(),
                order.getTotal(),
                order.getEmail(),
                order.getCustomerName()
        );

        order.setPaymentPreferenceId(preference.preferenceId());
        orderRepository.save(order);

        return new OrderResponse(
                order.getOrderNumber(),
                order.getStatus().name(),
                preference.checkoutUrl(),
                preference.preferenceId()
        );
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
