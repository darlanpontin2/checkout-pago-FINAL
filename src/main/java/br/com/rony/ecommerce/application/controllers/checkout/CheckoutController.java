package br.com.rony.ecommerce.application.controllers.checkout;

import br.com.rony.ecommerce.adapters.domain.services.checkout.*;
import br.com.rony.ecommerce.application.dto.checkout.CheckoutForms.*;
import br.com.rony.ecommerce.domain.services.checkout.ShippingService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
    private final CheckoutCartService carts;
    private final CheckoutExecutionService checkout;
    private final CheckoutPrivacyService privacy;
    private final CheckoutGuard guard;

    public CheckoutController(CheckoutCartService carts, CheckoutExecutionService checkout,
            CheckoutPrivacyService privacy, CheckoutGuard guard) {
        this.carts = carts; this.checkout = checkout;
        this.privacy = privacy; this.guard = guard;
    }

    private long customer(Jwt jwt, HttpServletRequest request, boolean payment) {
        String identity = jwt.getIssuer() + "|" + jwt.getSubject();
        guard.rate(identity, request.getRemoteAddr(), payment);
        return carts.customer(jwt.getIssuer().toString(), jwt.getSubject());
    }

    @PutMapping("/carrinho/itens")
    public Map<String, Object> item(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request, @RequestBody @Valid Item form) {
        return carts.changeItem(customer(jwt, request, false), form.produto_id(), form.quantidade());
    }

    @GetMapping("/resumo")
    public Map<String, Object> summary(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return Map.of("sucesso", true, "resumo", carts.summary(customer(jwt, request, false)));
    }

    @PostMapping("/cupom")
    public Map<String, Object> coupon(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request, @RequestBody @Valid Coupon form) {
        return Map.of("sucesso", true, "mensagem", "Cupom aplicado.",
            "resumo_atualizado", carts.coupon(customer(jwt, request, false), form.codigo_cupom()));
    }

    @DeleteMapping("/cupom")
    public Map<String, Object> removeCoupon(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return Map.of("sucesso", true, "mensagem", "Cupom removido.",
            "resumo_atualizado", carts.coupon(customer(jwt, request, false), null));
    }

    @PostMapping("/cupom/remover")
    public Map<String, Object> removeCouponPost(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return removeCoupon(jwt, request);
    }

    @PostMapping("/calcular-frete")
    public Map<String, Object> freight(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request, @RequestBody @Valid Freight form) {
        ShippingService.Sort sort = form.ordenar() == null ? ShippingService.Sort.PRICE
            : ShippingService.Sort.valueOf(form.ordenar());
        return Map.of("sucesso", true, "opcoes_frete",
            carts.quotes(customer(jwt, request, false), form.cep_entrega(), sort));
    }

    @PostMapping("/selecionar-frete")
    public Map<String, Object> select(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request, @RequestBody @Valid Selection form) {
        return Map.of("sucesso", true, "frete",
            carts.select(customer(jwt, request, false), form.cotacao_id().toString()));
    }

    @GetMapping("/frete")
    public Map<String, Object> selected(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return Map.of("sucesso", true, "frete", carts.selected(customer(jwt, request, false)));
    }

    @PostMapping("/finalizar-compra")
    public ResponseEntity<JsonNode> finish(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request, @RequestHeader("Idempotency-Key") String key,
            @RequestBody @Valid Finish form) {
        JsonNode response = checkout.finish(customer(jwt, request, true), key, form, request.getRemoteAddr());
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/pedidos/{id}")
    public Map<String, Object> order(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request, @PathVariable UUID id) {
        return checkout.order(customer(jwt, request, false), id.toString());
    }

    @GetMapping("/privacidade/dados")
    public Map<String, Object> personalData(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return privacy.export(customer(jwt, request, false));
    }

    @PutMapping("/privacidade/dados")
    public Map<String, Object> rectify(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request, @RequestBody @Valid Rectification form) {
        privacy.rectify(customer(jwt, request, false), form);
        return Map.of("sucesso", true);
    }

    @PostMapping("/privacidade/exclusao")
    public ResponseEntity<Map<String, Object>> erase(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        String id = privacy.requestErasure(customer(jwt, request, false));
        return ResponseEntity.accepted().body(Map.of("sucesso", true, "id_solicitacao", id));
    }

    @PostMapping("/privacidade/consentimentos")
    public Map<String, Object> consent(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request, @RequestBody @Valid Consent form) {
        privacy.consent(customer(jwt, request, false), form);
        return Map.of("sucesso", true);
    }
}
