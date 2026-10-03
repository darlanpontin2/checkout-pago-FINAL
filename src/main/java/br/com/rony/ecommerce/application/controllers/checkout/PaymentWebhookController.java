package br.com.rony.ecommerce.application.controllers.checkout;

import br.com.rony.ecommerce.adapters.domain.services.checkout.CheckoutWebhookService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class PaymentWebhookController {
    private final CheckoutWebhookService service;

    public PaymentWebhookController(CheckoutWebhookService service) {
        this.service = service;
    }

    @PostMapping("/api/webhook/pagamento")
    public Map<String, Object> receive(
            @RequestParam("data.id") String resourceId,
            @RequestHeader("x-request-id") String requestId,
            @RequestHeader("x-signature") String signature,
            @RequestBody String body) {
        service.receive(resourceId, requestId, signature, body);
        return Map.of("sucesso", true, "mensagem", "Notificação recebida.");
    }
}
