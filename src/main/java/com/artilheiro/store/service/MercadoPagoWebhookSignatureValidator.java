package com.artilheiro.store.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Valida a assinatura do header x-signature das notificações webhook do Mercado Pago.
 * Documentação: https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks
 */
public final class MercadoPagoWebhookSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private MercadoPagoWebhookSignatureValidator() {
    }

    /**
     * Valida se a notificação foi enviada pelo Mercado Pago usando a assinatura secreta.
     *
     * @param secret     assinatura secreta configurada em Suas integrações
     * @param dataId     data.id do payload (id do pagamento)
     * @param xRequestId valor do header x-request-id
     * @param xSignature valor do header x-signature (ex: ts=1704908010,v1=618c85...)
     * @return true se a assinatura for válida
     */
    public static boolean validate(String secret, String dataId, String xRequestId, String xSignature) {
        if (secret == null || secret.isBlank() || xSignature == null || xSignature.isBlank()) {
            return false;
        }
        String ts = null;
        String receivedHash = null;
        for (String part : xSignature.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                String key = part.substring(0, eq).trim();
                String value = part.substring(eq + 1).trim();
                if ("ts".equals(key)) {
                    ts = value;
                } else if ("v1".equals(key)) {
                    receivedHash = value;
                }
            }
        }
        if (ts == null || receivedHash == null) {
            return false;
        }
        String idForManifest = (dataId != null && !dataId.isBlank()) ? dataId : "";
        if (idForManifest.matches("[a-zA-Z0-9]+")) {
            idForManifest = idForManifest.toLowerCase();
        }
        String requestIdForManifest = (xRequestId != null) ? xRequestId : "";
        String manifest = "id:" + idForManifest + ";request-id:" + requestIdForManifest + ";ts:" + ts + ";";
        String computedHash = hmacSha256Hex(secret, manifest);
        return computedHash != null && computedHash.equalsIgnoreCase(receivedHash);
    }

    private static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return null;
        }
    }
}
