package com.artilheiro.store.repository;

import com.artilheiro.store.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    Optional<Order> findByOrderNumberAndEmailIgnoreCase(String orderNumber, String email);

    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Busca pedidos pelo CPF (compara apenas dígitos, independente da formatação).
     */
    @Query(value = "SELECT * FROM orders WHERE REGEXP_REPLACE(cpf, '[^0-9]', '', 'g') = :normalizedCpf ORDER BY created_at DESC", nativeQuery = true)
    List<Order> findByCpfNormalized(@Param("normalizedCpf") String normalizedCpf);
}
