package com.artilheiro.store.dto.product;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO para cadastro de produto.
 * Usado como parte do multipart (campo "product" em JSON).
 */
public class ProductRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Time é obrigatório")
    @Size(max = 255)
    private String team;

    @NotBlank(message = "Liga é obrigatória")
    @Size(max = 255)
    private String liga;

    @NotBlank(message = "Categoria é obrigatória")
    @Size(max = 255)
    private String category;

    @NotNull(message = "Preço é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço deve ser positivo")
    private BigDecimal price;

    @NotNull(message = "Tamanhos são obrigatórios")
    private Map<String, Integer> sizes;

    private Boolean active = true;

    public ProductRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getLiga() {
        return liga;
    }

    public void setLiga(String liga) {
        this.liga = liga;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Map<String, Integer> getSizes() {
        return sizes;
    }

    public void setSizes(Map<String, Integer> sizes) {
        this.sizes = sizes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active != null ? active : true;
    }
}
