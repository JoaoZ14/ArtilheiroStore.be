package com.artilheiro.store.dto.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corpo da notificação webhook do Mercado Pago.
 * Ex.: { "id": 12345, "type": "payment", "action": "payment.updated", "data": { "id": "999999999" } }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MercadoPagoWebhookPayload {

    private String type;
    /** Ação do evento: payment.created, payment.updated, etc. */
    private String action;
    private Data data;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
