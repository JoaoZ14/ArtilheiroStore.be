package com.artilheiro.store.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Páginas de retorno após o checkout do Mercado Pago.
 * O MP redireciona para as back URLs configuradas na preferência (sucesso, falha, pendente).
 */
@RestController
@RequestMapping("/pagamento")
public class PaymentController {

    @GetMapping(value = "/sucesso", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> sucesso(
            @RequestParam(required = false) String payment_id,
            @RequestParam(required = false) String external_reference,
            @RequestParam(required = false) String status) {
        String html = buildResultPage(
                "Pagamento aprovado",
                "Seu pagamento foi confirmado. Em breve você receberá a confirmação por e-mail.",
                "sucesso",
                external_reference);
        return ResponseEntity.ok(html);
    }

    @GetMapping(value = "/falha", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> falha(
            @RequestParam(required = false) String payment_id,
            @RequestParam(required = false) String external_reference,
            @RequestParam(required = false) String status) {
        String html = buildResultPage(
                "Pagamento não realizado",
                "O pagamento não foi concluído ou foi recusado. Você pode tentar novamente ou escolher outra forma de pagamento.",
                "falha",
                external_reference);
        return ResponseEntity.ok(html);
    }

    @GetMapping(value = "/pendente", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> pendente(
            @RequestParam(required = false) String payment_id,
            @RequestParam(required = false) String external_reference,
            @RequestParam(required = false) String status) {
        String html = buildResultPage(
                "Pagamento pendente",
                "Seu pagamento está em análise. Você receberá um e-mail quando for confirmado.",
                "pendente",
                external_reference);
        return ResponseEntity.ok(html);
    }

    private static String buildResultPage(String titulo, String mensagem, String tipo, String codigoPedido) {
        String corBg = "sucesso".equals(tipo) ? "#1b2e1b" : "falha".equals(tipo) ? "#2e1b1b" : "#2e2a1b";
        String corBorda = "sucesso".equals(tipo) ? "#2e7d32" : "falha".equals(tipo) ? "#c62828" : "#d4a82b";
        String codigoHtml = (codigoPedido != null && !codigoPedido.isBlank())
                ? "<p class=\"codigo\">Número do pedido: <strong>" + escapeHtml(codigoPedido) + "</strong></p>"
                : "";
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>%s – Artilheiro Store</title>
              <style>
                * { box-sizing: border-box; }
                body { margin: 0; font-family: system-ui, sans-serif; background: #0f0f12; color: #e8e8ed; min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 16px; }
                .card { max-width: 420px; width: 100%%; padding: 24px; border-radius: 12px; background: %s; border: 1px solid %s; text-align: center; }
                h1 { margin: 0 0 12px; font-size: 1.25rem; }
                p { margin: 0 0 16px; color: #b8b8c0; font-size: 0.95rem; line-height: 1.5; }
                .codigo { margin-top: 16px; padding-top: 16px; border-top: 1px solid rgba(255,255,255,0.1); }
                a { color: #d4a82b; text-decoration: none; }
                a:hover { text-decoration: underline; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>%s</h1>
                <p>%s</p>
                %s
                <p><a href="/">Voltar ao início</a></p>
              </div>
            </body>
            </html>
            """.formatted(titulo, corBg, corBorda, titulo, mensagem, codigoHtml);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
