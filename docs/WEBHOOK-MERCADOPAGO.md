# Webhook Mercado Pago – status do pedido (PIX, boleto)

Para o status do pedido ser atualizado automaticamente quando o pagamento PIX (ou boleto) for aprovado, o Mercado Pago envia uma notificação **POST** para a URL de webhook do **backend**.

## Por que dá 404?

Se o Mercado Pago retornar **"Falha na entrega - 404"**, a URL de notificação está apontando para um servidor que **não tem esse endpoint** – em geral para o **frontend** (ex.: Netlify) em vez do **backend** (ex.: Render).

- **Frontend** (Netlify, Vercel, etc.) → não tem `/api/orders/webhook/mercadopago` → **404**
- **Backend** (Render, etc.) → tem o endpoint → **200**

## O que configurar

A URL de webhook é montada assim:

1. Se existir **`MERCADOPAGO_NOTIFICATION_URL`** → ela é usada (deve ser a URL **completa** do backend).
2. Senão, usa **`APP_BACKEND_URL`** + `/api/orders/webhook/mercadopago`.
3. Se não houver nenhuma das duas, usa **`APP_BASE_URL`** (que costuma ser o frontend → risco de 404).

### No Render (recomendado)

Defina no serviço do **backend** (Environment):

| Variável | Valor | Observação |
|----------|--------|------------|
| **APP_BACKEND_URL** | `https://SEU-BACKEND.onrender.com` | URL do próprio backend no Render (sem barra no final). Ex.: `https://artilheiro-store-be.onrender.com` |
| **APP_BASE_URL** | `https://artilheirostore.netlify.app` | URL do frontend (para redirecionamento após checkout). |

Assim, a URL de webhook fica:  
`https://SEU-BACKEND.onrender.com/api/orders/webhook/mercadopago`

### Alternativa: URL completa

Se preferir, defina a URL inteira:

| Variável | Valor |
|----------|--------|
| **MERCADOPAGO_NOTIFICATION_URL** | `https://SEU-BACKEND.onrender.com/api/orders/webhook/mercadopago` |

## Conferir no deploy

Ao subir a aplicação, o log deve mostrar algo como:

```text
Webhook MP: notification-url=https://xxx.onrender.com.../api/orders/webhook/mercadopago (pública=true). O webhook deve apontar para o BACKEND.
```

Se aparecer a URL do **frontend** (ex.: netlify.app), ajuste **APP_BACKEND_URL** ou **MERCADOPAGO_NOTIFICATION_URL** para a URL do **backend**.

## Testar o endpoint

```bash
curl -X POST "https://SEU-BACKEND.onrender.com/api/orders/webhook/mercadopago" \
  -H "Content-Type: application/json" \
  -d '{"type":"payment","data":{"id":"123"}}'
```

Resposta esperada: **200** (e o pedido não será alterado se o pagamento 123 não existir ou não estiver aprovado).
