package com.artilheiro.store.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public class OrderRequest {

    @NotNull(message = "customer é obrigatório")
    @Valid
    private CustomerDto customer;

    @NotNull(message = "address é obrigatório")
    @Valid
    private AddressDto address;

    @NotEmpty(message = "items não pode ser vazio")
    @Valid
    private List<ItemDto> items;

    @NotNull(message = "total é obrigatório")
    @DecimalMin(value = "0.01", message = "total deve ser maior que zero")
    private BigDecimal total;

    public OrderRequest() {
    }

    public CustomerDto getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerDto customer) {
        this.customer = customer;
    }

    public AddressDto getAddress() {
        return address;
    }

    public void setAddress(AddressDto address) {
        this.address = address;
    }

    public List<ItemDto> getItems() {
        return items;
    }

    public void setItems(List<ItemDto> items) {
        this.items = items;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public static class CustomerDto {
        @NotNull(message = "name é obrigatório")
        private String name;

        @NotNull(message = "email é obrigatório")
        private String email;

        @NotNull(message = "cpf é obrigatório")
        private String cpf;

        public CustomerDto() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getCpf() {
            return cpf;
        }

        public void setCpf(String cpf) {
            this.cpf = cpf;
        }
    }

    public static class AddressDto {
        @NotNull(message = "cep é obrigatório")
        private String cep;

        @NotNull(message = "rua é obrigatório")
        private String rua;

        @NotNull(message = "numero é obrigatório")
        private String numero;

        private String complemento;

        @NotNull(message = "cidade é obrigatório")
        private String cidade;

        @NotNull(message = "estado é obrigatório")
        private String estado;

        public AddressDto() {
        }

        public String getCep() {
            return cep;
        }

        public void setCep(String cep) {
            this.cep = cep;
        }

        public String getRua() {
            return rua;
        }

        public void setRua(String rua) {
            this.rua = rua;
        }

        public String getNumero() {
            return numero;
        }

        public void setNumero(String numero) {
            this.numero = numero;
        }

        public String getComplemento() {
            return complemento;
        }

        public void setComplemento(String complemento) {
            this.complemento = complemento;
        }

        public String getCidade() {
            return cidade;
        }

        public void setCidade(String cidade) {
            this.cidade = cidade;
        }

        public String getEstado() {
            return estado;
        }

        public void setEstado(String estado) {
            this.estado = estado;
        }
    }

    public static class ItemDto {
        @NotNull(message = "productId é obrigatório")
        private String productId;

        private String name;

        @NotNull(message = "size é obrigatório")
        private String size;

        @NotNull(message = "quantity é obrigatório")
        private Integer quantity;

        @NotNull(message = "unitPrice é obrigatório")
        @DecimalMin(value = "0.01", message = "unitPrice deve ser maior que zero")
        private BigDecimal unitPrice;

        public ItemDto() {
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }
    }
}
