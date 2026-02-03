# Checkout Transparente – o que o frontend precisa

A **página de checkout e a tokenização do cartão ficam no frontend**. O backend só recebe os dados do pedido e, em seguida, o token do pagamento. Não há página de checkout no backend.

---

## Fluxo geral

1. **Frontend** monta os dados do pedido (cliente, endereço, itens, total) e chama **POST /api/orders**.
2. O backend cria o pedido e devolve **`orderId`** (ex: ART-2025-0001).
3. O **frontend** exibe a tela de pagamento (formulário de cartão do Mercado Pago – SDK/Brick no próprio frontend).
4. O usuário preenche o cartão; o **SDK do Mercado Pago no frontend** gera o **token** (dados do cartão não passam pelo seu servidor).
5. O **frontend** envia o token + dados do pagamento para **POST /api/orders/{orderId}/payments**.
6. O backend processa o pagamento no Mercado Pago e devolve o status (aprovado, pendente, rejeitado).

---

## 1. Criar pedido

```
POST {BASE_URL}/api/orders
Content-Type: application/json
```

**Body (OrderRequest):** cliente, endereço, itens, total (mesmo formato que antes).

**Resposta 201:**

```json
{
  "orderId": "ART-2025-0001",
  "status": "PAYMENT_PENDING",
  "checkoutUrl": null,
  "preferenceId": null
}
```

Guarde **`orderId`** para o próximo passo. Não há `checkoutUrl`; o pagamento é feito na sua própria página.

---

## 2. Tokenização no frontend (sua responsabilidade)

No frontend você deve:

- Incluir o **SDK do Mercado Pago** (ex.: `https://sdk.mercadopago.com/js/v2`).
- Usar a **chave pública** do Mercado Pago (não a access token). Pode obter em **GET /api/config** (`mercadopagoPublicKey`) ou configurar direto no frontend.
- Exibir o **Card Payment Brick** (ou cardform) do Mercado Pago para o usuário digitar número do cartão, validade, CVV, nome, etc.
- No envio do formulário, o SDK retorna um **token** (e `payment_method_id`, `installments`, `issuer_id`, dados do `payer`). Esse token é o que você envia para o backend; os dados do cartão não passam pelo seu servidor.

Documentação do Brick: [Card Payment Brick](https://www.mercadopago.com.br/developers/pt/docs/checkout-bricks/card-payment-brick/default-rendering).

---

## 3. Criar pagamento (cartão, PIX ou boleto)

```
POST {BASE_URL}/api/orders/{orderId}/payments
Content-Type: application/json
```

**`orderId`** na URL = número do pedido retornado no passo 1 (ex: `ART-2025-0001`).

O backend aceita três meios de pagamento via **`payment_method_id`**:

| payment_method_id | Descrição | Token | Observações |
|-------------------|-----------|--------|-------------|
| `visa`, `master`, `amex`, ... | Cartão | **Obrigatório** | Obter token no frontend com SDK/Brick do MP. Enviar `installments` (ex: 1). |
| `pix` | PIX | **Não** | Sem token. Resposta traz `qrCodeBase64` e `qrCode` (copia e cola) para exibir ao usuário. |
| `bolbradesco` | Boleto | **Não** | Sem token. **Obrigatório** enviar `payer.address` (rua, número, CEP, cidade, UF). Resposta traz `ticketUrl` (link do boleto PDF). |

**Body (PaymentCreateRequest) – campos comuns:**

| Campo | Tipo | Cartão | PIX | Boleto |
|-------|------|--------|-----|--------|
| `token` | string | **Sim** | Não | Não |
| `payment_method_id` | string | Sim | `pix` | `bolbradesco` |
| `installments` | number | Sim (ex: 1) | Não (ou 1) | Não (ou 1) |
| `issuer_id` | string | Opcional | Não | Não |
| `payer` | objeto | Sim | Sim | Sim |
| `payer.email` | string | Sim | Sim | Sim |
| `payer.name` | string | Opcional | Recomendado | Recomendado |
| `payer.identification` | objeto | Sim | Sim | Sim |
| `payer.identification.type` | string | CPF | CPF | CPF |
| `payer.identification.number` | string | Sim | Sim | Sim |
| `payer.address` | objeto | Não | Não | **Sim** (streetName, streetNumber, zipCode, city, federalUnit) |

**Exemplo – cartão:**

```json
{
  "token": "ff8080814c11e237014c1ff593b57b4d",
  "payment_method_id": "visa",
  "installments": 1,
  "payer": {
    "email": "comprador@email.com",
    "name": "João Silva",
    "identification": { "type": "CPF", "number": "12345678900" }
  }
}
```

**Exemplo – PIX:**

```json
{
  "payment_method_id": "pix",
  "payer": {
    "email": "comprador@email.com",
    "name": "João Silva",
    "identification": { "type": "CPF", "number": "12345678900" }
  }
}
```

**Exemplo – boleto:**

```json
{
  "payment_method_id": "bolbradesco",
  "payer": {
    "email": "comprador@email.com",
    "name": "João Silva",
    "identification": { "type": "CPF", "number": "12345678900" },
    "address": {
      "streetName": "Av. Paulista",
      "streetNumber": "1000",
      "zipCode": "01310100",
      "city": "São Paulo",
      "federalUnit": "SP"
    }
  }
}
```

**Resposta 200:**

```json
{
  "paymentId": 12345678901,
  "status": "pending",
  "statusDetail": "pending_waiting_payment",
  "orderId": "ART-2025-0001",
  "qrCodeBase64": "iVBORw0KGgo...",
  "qrCode": "00020126580014br.gov.bcb.pix...",
  "ticketUrl": "https://www.mercadopago.com.br/payments/12345678901/ticket?caller_id=..."
}
```

- **Cartão:** `status` pode ser `approved`, `pending`, `rejected`. Se `approved`, o backend já atualizou o pedido para “pago”.
- **PIX:** use `qrCodeBase64` (imagem do QR) e/ou `qrCode` (copia e cola) para o usuário pagar. `status` geralmente `pending` até a confirmação (webhook).
- **Boleto:** use `ticketUrl` para exibir ou baixar o PDF. `status` geralmente `pending` até o pagamento.

**Erros:** 404 (pedido não encontrado), 400 (pedido já pago, token/address faltando ou inválido), 502 (erro no Mercado Pago – ver corpo da resposta).

---

## 4. Sincronizar status do pagamento (PIX / quando o webhook não for chamado)

Quando o usuário paga com PIX (ou boleto), o Mercado Pago notifica o backend via **webhook**. Em ambiente **local** (localhost) o webhook não é enviado pelo MP, então o pedido pode continuar como "pendente" mesmo após o pagamento.

Para atualizar o status do pedido nesses casos (ou para dar um "refresh" após o usuário voltar da tela do banco), o frontend pode chamar:

```
GET {BASE_URL}/api/orders/{orderId}/sync-payment?paymentId={paymentId}
```

- **orderId** = número do pedido (ex: `ART-2025-0001`).
- **paymentId** = ID do pagamento retornado na criação (ex: `12345678901`).

**Resposta 200:**

```json
{ "updated": true, "orderNumber": "ART-2025-0001" }
```

- `updated: true` = o pedido foi atualizado para RECEIVED (pago).
- `updated: false` = o pagamento ainda não está aprovado no MP ou já estava atualizado.

Depois disso, use **GET /api/orders/lookup** para mostrar o pedido com status atualizado.

---

## 5. Configuração (opcional)

```
GET {BASE_URL}/api/config
```

**Resposta 200:**

```json
{
  "mercadopagoPublicKey": "APP_USR-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

Use **`mercadopagoPublicKey`** no frontend para inicializar o SDK/Brick do Mercado Pago. Se preferir, pode configurar a chave pública direto no frontend (variável de ambiente ou config do projeto).

No backend, a chave pública é definida em **`mercadopago.public-key`** (ou variável **`MERCADOPAGO_PUBLIC_KEY`**).

---

## 6. Consultar pedido

```
GET {BASE_URL}/api/orders/lookup?email={email}&code={orderId}
```

Ex.: `GET /api/orders/lookup?email=joao@email.com&code=ART-2025-0001`

Resposta 200: dados do pedido (status, itens, total, etc.).  
Resposta 404: pedido não encontrado ou e-mail não confere.

---

## Resumo para o dev frontend

1. **Criar pedido:** `POST /api/orders` com cliente, endereço, itens e total → recebe **`orderId`**.
2. **Na sua página de checkout:** usar SDK/Brick do Mercado Pago com a **chave pública** para o usuário preencher o cartão e obter o **token**.
3. **Criar pagamento:** `POST /api/orders/{orderId}/payments` com **token**, **payment_method_id**, **installments** e **payer** (email + identification).
4. Tratar a resposta (approved / pending / rejected) e redirecionar ou exibir mensagem (sucesso, em análise, falha).
5. **PIX/boleto:** após o usuário pagar, chamar **GET /api/orders/{orderId}/sync-payment?paymentId=...** para atualizar o status do pedido quando o webhook não for recebido (ex.: localhost).

O backend **não** renderiza formulário de cartão; ele só recebe o token e os dados que você envia a partir do frontend.
