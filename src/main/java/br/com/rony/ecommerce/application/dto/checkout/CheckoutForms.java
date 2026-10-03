package br.com.rony.ecommerce.application.dto.checkout;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
public final class CheckoutForms {
 private CheckoutForms(){}
 public record Item(@NotNull @Positive Long produto_id,@Min(0) @Max(9999) int quantidade){}
 public record Coupon(@NotBlank @Size(max=40) String codigo_cupom){}
 public record Freight(@NotBlank @Pattern(regexp="[0-9]{5}-?[0-9]{3}") String cep_entrega,@Pattern(regexp="PRICE|DELIVERY_TIME") String ordenar){}
 public record Selection(@NotNull UUID cotacao_id){}
 public record Finish(
  @NotNull @Positive Long id_carrinho,@NotNull UUID cotacao_id,
  @NotBlank @Pattern(regexp="pix|cartao_credito|cartao_debito") String metodo_pagamento,
  @Size(max=300) String token_cartao,@Size(max=50) String payment_method_id,
  @Min(1) @Max(12) int parcelas,@NotNull @Valid CustomerFormDTO dados_cliente,
  @NotNull @Valid AddressFormDTO endereco_entrega,@AssertTrue boolean consentimento,
  @NotBlank @Size(max=40) String versao_politica
 ){@Override public String toString(){return "Finish[REDACTED]";}}
 public record Consent(@NotBlank @Size(max=100) String finalidade,@NotBlank @Size(max=40) String versao_politica,boolean concedido){}
 public record Rectification(@NotNull @Valid CustomerFormDTO dados_cliente,@NotNull @Valid AddressFormDTO endereco){@Override public String toString(){return "Rectification[REDACTED]";}}
}
