package com.artilheiro.store.repository;

import com.artilheiro.store.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    Optional<Order> findByOrderNumberAndEmailIgnoreCase(String orderNumber, String email);

    Optional<Order> findByOrderNumber(String orderNumber);
}
