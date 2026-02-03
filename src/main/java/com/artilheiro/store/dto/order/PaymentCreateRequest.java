package com.artilheiro.store.dto.order;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados para criar um pagamento no Checkout Transparente (Mercado Pago).
 * Suporta cartão (token obrigatório), PIX e boleto (sem token).
 */
public class PaymentCreateRequest {

    /** Token do cartão (obrigatório para cartão; omitir para PIX e boleto). */
    private String token;

    /** ID do meio de pagamento: visa, master, amex (cartão), pix, bolbradesco (boleto). */
    @JsonProperty("payment_method_id")
    @JsonAlias("paymentMethodId")
    @NotBlank(message = "payment_method_id é obrigatório")
    @Size(max = 256)
    private String paymentMethodId;

    /** Número de parcelas (obrigatório para cartão; use 1 para PIX/boleto). */
    @Min(value = 1, message = "installments deve ser pelo menos 1")
    private Integer installments;

    /** ID do emissor do cartão (opcional; apenas para cartão). */
    @JsonProperty("issuer_id")
    private String issuerId;

    @NotNull(message = "payer é obrigatório")
    @Valid
    private PayerDto payer;

    public PaymentCreateRequest() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(String paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public Integer getInstallments() {
        return installments;
    }

    public void setInstallments(Integer installments) {
        this.installments = installments;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public PayerDto getPayer() {
        return payer;
    }

    public void setPayer(PayerDto payer) {
        this.payer = payer;
    }

    public static class PayerDto {
        @NotBlank(message = "payer.email é obrigatório")
        private String email;

        @NotNull(message = "payer.identification é obrigatório")
        @Valid
        private IdentificationDto identification;

        /** Nome do titular (obrigatório para boleto; recomendado para PIX). */
        private String name;

        /** Endereço (obrigatório para boleto; não usado em PIX/cartão). */
        @Valid
        private AddressDto address;

        public PayerDto() {
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public IdentificationDto getIdentification() {
            return identification;
        }

        public void setIdentification(IdentificationDto identification) {
            this.identification = identification;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public AddressDto getAddress() {
            return address;
        }

        public void setAddress(AddressDto address) {
            this.address = address;
        }
    }

    /** Endereço do pagador (obrigatório para boleto). */
    public static class AddressDto {
        @JsonProperty("street_name")
        private String streetName;
        @JsonProperty("street_number")
        private String streetNumber;
        @JsonProperty("zip_code")
        private String zipCode;
        private String city;
        @JsonProperty("federal_unit")
        private String federalUnit;

        public String getStreetName() { return streetName; }
        public void setStreetName(String streetName) { this.streetName = streetName; }
        public String getStreetNumber() { return streetNumber; }
        public void setStreetNumber(String streetNumber) { this.streetNumber = streetNumber; }
        public String getZipCode() { return zipCode; }
        public void setZipCode(String zipCode) { this.zipCode = zipCode; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getFederalUnit() { return federalUnit; }
        public void setFederalUnit(String federalUnit) { this.federalUnit = federalUnit; }
    }

    public static class IdentificationDto {
        /** Tipo do documento: CPF. */
        @NotBlank(message = "payer.identification.type é obrigatório")
        private String type;

        /** Número do documento (apenas dígitos). */
        @NotBlank(message = "payer.identification.number é obrigatório")
        private String number;

        public IdentificationDto() {
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number;
        }
    }
}
