package com.artilheiro.store.service;

import com.artilheiro.store.dto.order.OrderRequest;
import com.artilheiro.store.dto.order.OrderResponse;
import com.artilheiro.store.model.Order;
import com.artilheiro.store.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
