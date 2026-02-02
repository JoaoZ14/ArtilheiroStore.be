package com.artilheiro.store.service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MercadoPagoService {

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @Value("${mercadopago.back-url.success}")
    private String successUrl;

    @Value("${mercadopago.back-url.failure}")
    private String failureUrl;

    @Value("${mercadopago.back-url.pending}")
    private String pendingUrl;

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

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(successUrl)
                .failure(failureUrl)
                .pending(pendingUrl)
                .build();

        PreferencePayerRequest payer = PreferencePayerRequest.builder()
                .email(payerEmail)
                .name(payerName != null ? payerName : payerEmail)
                .build();

        PreferenceRequest request = PreferenceRequest.builder()
                .items(preferenceItems)
                .backUrls(backUrls)
                .externalReference(orderNumber)
                .payer(payer)
                .autoReturn("approved")
                .build();

        PreferenceClient client = new PreferenceClient();
        Preference preference = client.create(request);

        return new PreferenceResult(
                preference.getId(),
                preference.getInitPoint()
        );
    }

    /**
     * Busca um pagamento pelo id (para validar webhook e obter external_reference e status).
     */
    public com.mercadopago.resources.payment.Payment getPayment(Long paymentId) throws MPException, MPApiException {
        com.mercadopago.client.payment.PaymentClient client = new com.mercadopago.client.payment.PaymentClient();
        return client.get(paymentId);
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
