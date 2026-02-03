package com.artilheiro.store.service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentPayerAddressRequest;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MercadoPagoService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @Value("${mercadopago.back-url.success}")
    private String successUrl;

    @Value("${mercadopago.back-url.failure}")
    private String failureUrl;

    @Value("${mercadopago.back-url.pending}")
    private String pendingUrl;

    @Value("${mercadopago.notification-url:}")
    private String notificationUrl;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        if (accessToken != null && !accessToken.isBlank()) {
            MercadoPagoConfig.setAccessToken(accessToken);
        }
    }

    /**
     * Cria uma preferência de checkout no Mercado Pago para o pedido.
     * Retorna o link de checkout (init_point) e o id da preferência.
     *
     * @param orderNumber número do pedido (external_reference)
     * @param items       itens do pedido (cada map com name, quantity, unitPrice, productId, image opcional)
     * @param total       total do pedido (validado pelo MP)
     * @param payerEmail  e-mail do comprador
     * @param payerName   nome do comprador
     * @return resultado com initPoint (checkoutUrl) e preferenceId
     */
    public PreferenceResult createPreference(
            String orderNumber,
            List<Map<String, Object>> items,
            BigDecimal total,
            String payerEmail,
            String payerName) throws MPException, MPApiException {

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "MERCADOPAGO_ACCESS_TOKEN não configurado. Defina a variável de ambiente no launch.json ou no sistema.");
        }

        List<PreferenceItemRequest> preferenceItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            String title = item.get("name") != null ? item.get("name").toString() : "Item " + (i + 1);
            Integer quantity = toInt(item.get("quantity"));
            BigDecimal unitPrice = toBigDecimal(item.get("unitPrice"));
            if (quantity == null || quantity <= 0 || unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }
            String productId = item.get("productId") != null ? item.get("productId").toString() : ("item-" + i);
            String pictureUrl = item.get("image") != null ? item.get("image").toString() : null;

            PreferenceItemRequest.PreferenceItemRequestBuilder itemBuilder = PreferenceItemRequest.builder()
                    .id(truncate(productId, 256))
                    .title(truncate(title, 256))
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .currencyId("BRL");
            if (pictureUrl != null && !pictureUrl.isBlank()) {
                itemBuilder.pictureUrl(truncate(pictureUrl, 600));
            }
            preferenceItems.add(itemBuilder.build());
        }

        if (preferenceItems.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item válido para a preferência");
        }

        // Mercado Pago exige back_urls absolutas (http/https) quando auto_return está ativo
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim().replaceAll("/+$", "") : "http://localhost:8080";
        String success = toAbsoluteUrl(successUrl, base, "/pagamento/sucesso");
        String failure = toAbsoluteUrl(failureUrl, base, "/pagamento/falha");
        String pending = toAbsoluteUrl(pendingUrl, base, "/pagamento/pendente");

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(success)
                .failure(failure)
                .pending(pending)
                .build();

        PreferencePayerRequest payer = PreferencePayerRequest.builder()
                .email(payerEmail)
                .name(payerName != null ? payerName : payerEmail)
                .build();

        // Não usar autoReturn("approved") aqui: a API exige back_urls com formato específico e o SDK pode serializar diferente.
        // O redirecionamento após o pagamento continua funcionando pelas back_urls (botão "Voltar à loja" no MP).
        PreferenceRequest.PreferenceRequestBuilder requestBuilder = PreferenceRequest.builder()
                .items(preferenceItems)
                .backUrls(backUrls)
                .externalReference(orderNumber)
                .payer(payer);
        String webhookUrl = (notificationUrl != null && !notificationUrl.isBlank())
                ? toAbsoluteUrl(notificationUrl, base, "/api/orders/webhook/mercadopago") : null;
        if (isPublicWebhookUrl(webhookUrl)) {
            requestBuilder.notificationUrl(webhookUrl);
        }
        PreferenceRequest request = requestBuilder.build();

        PreferenceClient client = new PreferenceClient();
        try {
            Preference preference = client.create(request);
            return new PreferenceResult(
                    preference.getId(),
                    preference.getInitPoint()
            );
        } catch (MPApiException e) {
            String detail = e.getApiResponse() != null && e.getApiResponse().getContent() != null
                    ? e.getApiResponse().getContent()
                    : e.getMessage();
            log.error("Mercado Pago API error creating preference: {} - {}", e.getMessage(), detail);
            throw e;
        }
    }

    /**
     * Cria um pagamento no Checkout Transparente (API Payments).
     * O token é gerado no frontend pelo SDK do Mercado Pago (formulário de cartão).
     *
     * @param transactionAmount valor total (ex: 99.90)
     * @param token             token do cartão (frontend)
     * @param paymentMethodId   ex: visa, master, amex
     * @param installments      número de parcelas (1 = à vista)
     * @param description       descrição do pagamento (ex: "Pedido ART-2025-0001")
     * @param externalReference número do pedido (ex: ART-2025-0001)
     * @param payerEmail        e-mail do comprador
     * @param payerName         nome do comprador (opcional)
     * @param payerIdentificationType tipo do documento (ex: CPF)
     * @param payerIdentificationNumber número do documento (apenas dígitos)
     * @param issuerId          ID do emissor (opcional; pode vir do frontend)
     */
    public Payment createPayment(
            BigDecimal transactionAmount,
            String token,
            String paymentMethodId,
            int installments,
            String description,
            String externalReference,
            String payerEmail,
            String payerName,
            String payerIdentificationType,
            String payerIdentificationNumber,
            String issuerId) throws MPException, MPApiException {

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "MERCADOPAGO_ACCESS_TOKEN não configurado. Defina a variável de ambiente no launch.json ou no sistema.");
        }

        IdentificationRequest identification = IdentificationRequest.builder()
                .type(payerIdentificationType != null ? payerIdentificationType : "CPF")
                .number(normalizeCpf(payerIdentificationNumber))
                .build();

        PaymentPayerRequest.PaymentPayerRequestBuilder payerBuilder = PaymentPayerRequest.builder()
                .email(payerEmail)
                .identification(identification);
        if (payerName != null && !payerName.isBlank()) {
            payerBuilder.entityType("individual").firstName(payerName);
        }
        PaymentPayerRequest payer = payerBuilder.build();

        PaymentCreateRequest.PaymentCreateRequestBuilder builder = PaymentCreateRequest.builder()
                .transactionAmount(transactionAmount)
                .token(token)
                .paymentMethodId(paymentMethodId)
                .installments(installments)
                .description(description != null ? truncate(description, 256) : "Pedido")
                .externalReference(externalReference)
                .payer(payer);

        if (issuerId != null && !issuerId.isBlank()) {
            builder.issuerId(issuerId);
        }

        String notification = (notificationUrl != null && !notificationUrl.isBlank())
                ? toAbsoluteUrl(notificationUrl, (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim().replaceAll("/+$", "") : "http://localhost:8080", "/api/orders/webhook/mercadopago")
                : null;
        if (isPublicWebhookUrl(notification)) {
            builder.notificationUrl(notification);
        }

        return executePaymentCreate(builder.build());
    }

    /**
     * Cria um pagamento PIX (sem token). Retorna QR code e código copia e cola na resposta do MP.
     */
    public Payment createPaymentPix(
            BigDecimal transactionAmount,
            String description,
            String externalReference,
            String payerEmail,
            String payerName,
            String payerIdentificationType,
            String payerIdentificationNumber) throws MPException, MPApiException {

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "MERCADOPAGO_ACCESS_TOKEN não configurado. Defina a variável de ambiente no launch.json ou no sistema.");
        }

        IdentificationRequest identification = IdentificationRequest.builder()
                .type(payerIdentificationType != null ? payerIdentificationType : "CPF")
                .number(normalizeCpf(payerIdentificationNumber))
                .build();

        PaymentPayerRequest.PaymentPayerRequestBuilder payerBuilder = PaymentPayerRequest.builder()
                .email(payerEmail)
                .identification(identification);
        if (payerName != null && !payerName.isBlank()) {
            payerBuilder.entityType("individual").firstName(payerName);
        }
        PaymentPayerRequest payer = payerBuilder.build();

        PaymentCreateRequest.PaymentCreateRequestBuilder builder = PaymentCreateRequest.builder()
                .transactionAmount(transactionAmount)
                .paymentMethodId("pix")
                .installments(1)
                .description(description != null ? truncate(description, 256) : "Pedido")
                .externalReference(externalReference)
                .payer(payer);

        String notification = (notificationUrl != null && !notificationUrl.isBlank())
                ? toAbsoluteUrl(notificationUrl, (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim().replaceAll("/+$", "") : "http://localhost:8080", "/api/orders/webhook/mercadopago")
                : null;
        if (isPublicWebhookUrl(notification)) {
            builder.notificationUrl(notification);
        }

        return executePaymentCreate(builder.build());
    }

    /**
     * Cria um pagamento em boleto (sem token). Exige endereço do pagador. Retorna URL do boleto na resposta do MP.
     */
    public Payment createPaymentBoleto(
            BigDecimal transactionAmount,
            String description,
            String externalReference,
            String payerEmail,
            String payerName,
            String payerIdentificationType,
            String payerIdentificationNumber,
            String streetName,
            String streetNumber,
            String zipCode,
            String city,
            String federalUnit) throws MPException, MPApiException {

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "MERCADOPAGO_ACCESS_TOKEN não configurado. Defina a variável de ambiente no launch.json ou no sistema.");
        }

        IdentificationRequest identification = IdentificationRequest.builder()
                .type(payerIdentificationType != null ? payerIdentificationType : "CPF")
                .number(normalizeCpf(payerIdentificationNumber))
                .build();

        PaymentPayerAddressRequest address = PaymentPayerAddressRequest.builder()
                .streetName(streetName != null ? truncate(streetName, 256) : "")
                .streetNumber(streetNumber != null ? truncate(streetNumber, 256) : "")
                .zipCode(zipCode != null ? zipCode.replaceAll("\\D", "") : "")
                .build();

        PaymentPayerRequest.PaymentPayerRequestBuilder payerBuilder = PaymentPayerRequest.builder()
                .email(payerEmail)
                .identification(identification)
                .address(address);
        if (payerName != null && !payerName.isBlank()) {
            payerBuilder.entityType("individual").firstName(payerName);
        }
        PaymentPayerRequest payer = payerBuilder.build();

        PaymentCreateRequest.PaymentCreateRequestBuilder builder = PaymentCreateRequest.builder()
                .transactionAmount(transactionAmount)
                .paymentMethodId("bolbradesco")
                .installments(1)
                .description(description != null ? truncate(description, 256) : "Pedido")
                .externalReference(externalReference)
                .payer(payer);

        String notification = (notificationUrl != null && !notificationUrl.isBlank())
                ? toAbsoluteUrl(notificationUrl, (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim().replaceAll("/+$", "") : "http://localhost:8080", "/api/orders/webhook/mercadopago")
                : null;
        if (isPublicWebhookUrl(notification)) {
            builder.notificationUrl(notification);
        }

        return executePaymentCreate(builder.build());
    }

    private Payment executePaymentCreate(PaymentCreateRequest request) throws MPException, MPApiException {
        PaymentClient client = new PaymentClient();
        try {
            return client.create(request);
        } catch (MPApiException e) {
            String detail = e.getApiResponse() != null && e.getApiResponse().getContent() != null
                    ? e.getApiResponse().getContent()
                    : e.getMessage();
            log.error("Mercado Pago API error creating payment: {} - {}", e.getMessage(), detail);
            throw e;
        }
    }

    private static String normalizeCpf(String value) {
        if (value == null) return null;
        return value.replaceAll("\\D", "");
    }

    /**
     * Busca um pagamento pelo id (para validar webhook e obter external_reference e status).
     */
    public Payment getPayment(Long paymentId) throws MPException, MPApiException {
        PaymentClient client = new PaymentClient();
        return client.get(paymentId);
    }

    /** Garante URL absoluta (http/https) para o Mercado Pago. */
    private static String toAbsoluteUrl(String url, String base, String path) {
        if (url != null && !url.isBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
            return url.trim();
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    /** Mercado Pago exige URL pública para webhook (rejeita localhost). Retorna true só se for https ou host público. */
    private static boolean isPublicWebhookUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String u = url.trim().toLowerCase();
        if (!u.startsWith("http://") && !u.startsWith("https://")) return false;
        if (u.contains("localhost") || u.contains("127.0.0.1")) return false;
        return true;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private static Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record PreferenceResult(String preferenceId, String checkoutUrl) {
    }
}
