package com.artilheiro.store.dto.order;

/**
 * Resposta após criar um pagamento no Checkout Transparente.
 * Para PIX: preenchidos qrCodeBase64 e/ou qrCode (copia e cola).
 * Para boleto: preenchido ticketUrl (link do PDF).
 */
public class PaymentCreateResponse {

    private Long paymentId;
    private String status;
    private String statusDetail;
    private String orderId;
    /** PIX: QR code em Base64 (para exibir imagem). */
    private String qrCodeBase64;
    /** PIX: código copia e cola. */
    private String qrCode;
    /** Boleto/PIX: URL do boleto PDF ou comprovante. */
    private String ticketUrl;

    public PaymentCreateResponse() {
    }

    public PaymentCreateResponse(Long paymentId, String status, String statusDetail, String orderId) {
        this.paymentId = paymentId;
        this.status = status;
        this.statusDetail = statusDetail;
        this.orderId = orderId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public void setStatusDetail(String statusDetail) {
        this.statusDetail = statusDetail;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getQrCodeBase64() {
        return qrCodeBase64;
    }

    public void setQrCodeBase64(String qrCodeBase64) {
        this.qrCodeBase64 = qrCodeBase64;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public String getTicketUrl() {
        return ticketUrl;
    }

    public void setTicketUrl(String ticketUrl) {
        this.ticketUrl = ticketUrl;
    }
}
