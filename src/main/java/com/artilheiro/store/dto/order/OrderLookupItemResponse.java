package com.artilheiro.store.dto.order;

import java.math.BigDecimal;
import java.util.List;

public class OrderLookupItemResponse {

    private String productId;
    private String name;
    private String image;
    private List<String> images;
    private String team;
    private String liga;
    private String category;
    private String size;
    private Integer quantity;
    private BigDecimal price;

    public OrderLookupItemResponse() {
    }

    public OrderLookupItemResponse(String productId, String name, String image, List<String> images,
                                  String team, String liga, String category, String size,
                                  Integer quantity, BigDecimal price) {
        this.productId = productId;
        this.name = name;
        this.image = image;
        this.images = images;
        this.team = team;
        this.liga = liga;
        this.category = category;
        this.size = size;
        this.quantity = quantity;
        this.price = price;
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

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
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
