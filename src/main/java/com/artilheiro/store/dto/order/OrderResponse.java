package com.artilheiro.store.dto.order;

public class OrderResponse {

    private String orderId;
    private String status;
    private String checkoutUrl;
    private String preferenceId;

    public OrderResponse() {
    }

    public OrderResponse(String orderId, String status) {
        this.orderId = orderId;
        this.status = status;
    }

    public OrderResponse(String orderId, String status, String checkoutUrl, String preferenceId) {
        this.orderId = orderId;
        this.status = status;
        this.checkoutUrl = checkoutUrl;
        this.preferenceId = preferenceId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getPreferenceId() {
        return preferenceId;
    }

    public void setPreferenceId(String preferenceId) {
        this.preferenceId = preferenceId;
    }
}
