package com.artilheiro.store.dto.order;

import java.math.BigDecimal;

public class OrderLookupItemResponse {

    private String name;
    private String image;
    private String size;
    private Integer quantity;
    private BigDecimal price;

    public OrderLookupItemResponse() {
    }

    public OrderLookupItemResponse(String name, String image, String size, Integer quantity, BigDecimal price) {
        this.name = name;
        this.image = image;
        this.size = size;
        this.quantity = quantity;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
