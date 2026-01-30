package com.artilheiro.store.service;

import com.artilheiro.store.dto.order.OrderLookupItemResponse;
import com.artilheiro.store.dto.order.OrderLookupResponse;
import com.artilheiro.store.dto.order.OrderRequest;
import com.artilheiro.store.dto.order.OrderResponse;
import com.artilheiro.store.dto.order.OrderUpdateRequest;
import com.artilheiro.store.model.Order;
import com.artilheiro.store.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final String ORDER_PREFIX = "ART-";

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderResponse create(OrderRequest request) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber(generateOrderNumber());
        order.setCustomerName(request.getCustomer().getName());
        order.setEmail(request.getCustomer().getEmail());
        order.setCpf(request.getCustomer().getCpf());
        order.setAddress(toAddressMap(request.getAddress()));
        order.setItems(toItemsMapList(request.getItems()));
        order.setTotal(request.getTotal());
        order.setStatus(Order.OrderStatus.RECEIVED);
        order.setCreatedAt(LocalDateTime.now());

        orderRepository.save(order);

        return new OrderResponse(order.getOrderNumber(), order.getStatus().name());
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
        String name = map.get("name") != null ? map.get("name").toString() : null;
        String image = map.get("image") != null ? map.get("image").toString() : null;
        String size = map.get("size") != null ? map.get("size").toString() : null;
        Integer quantity = toInteger(map.get("quantity"));
        BigDecimal price = toBigDecimal(map.get("unitPrice"));
        return new OrderLookupItemResponse(name, image, size, quantity, price);
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
            map.put("productId", item.getProductId());
            if (item.getName() != null) {
                map.put("name", item.getName());
            }
            map.put("size", item.getSize());
            map.put("quantity", item.getQuantity());
            map.put("unitPrice", item.getUnitPrice());
            list.add(map);
        }
        return list;
    }
}
