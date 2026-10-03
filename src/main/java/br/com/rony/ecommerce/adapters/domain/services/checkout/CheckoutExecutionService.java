package br.com.rony.ecommerce.adapters.domain.services.checkout;

import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore;
import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore.Row;
import br.com.rony.ecommerce.adapters.infrastructure.checkout.CheckoutCrypto;
import br.com.rony.ecommerce.application.dto.checkout.CheckoutForms.Finish;
import br.com.rony.ecommerce.domain.entities.checkout.CartSnapshot;
import br.com.rony.ecommerce.domain.services.checkout.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static br.com.rony.ecommerce.adapters.domain.services.checkout.CheckoutCartService.error;

@Service
public class CheckoutExecutionService {
 private final CheckoutStore db;private final CheckoutCartService carts;private final CheckoutCrypto crypto;private final CheckoutGuard guard;
 private final CustomerValidationServiceImpl customerValidation;private final PaymentValidationService paymentValidation;private final Clock clock;
 private final String policyVersion;private final long retentionDays;
 public CheckoutExecutionService(CheckoutStore db,CheckoutCartService carts,CheckoutCrypto crypto,CheckoutGuard guard,CustomerValidationServiceImpl customerValidation,PaymentValidationService paymentValidation,Clock clock,@Value("${checkout.privacy.policy-version}") String policyVersion,@Value("${checkout.privacy.order-retention-days}") long retentionDays){
  this.db=db;this.carts=carts;this.crypto=crypto;this.guard=guard;this.customerValidation=customerValidation;this.paymentValidation=paymentValidation;this.clock=clock;this.policyVersion=policyVersion;this.retentionDays=retentionDays;
 }
 public JsonNode finish(long customer,String idempotencyKey,Finish form,String ip){
  validate(form);String key;
  try{key=UUID.fromString(idempotencyKey).toString();}catch(Exception e){throw error(400,"IDEMPOTENCY_KEY_INVALIDA","Informe uma chave UUID.");}
  String keyHash=crypto.hash(key),requestHash=crypto.hmac("request",crypto.json(form));
  Optional<JsonNode> replay=db.tx(()->replay(customer,keyHash,requestHash));if(replay.isPresent())return replay.get();
  BigDecimal amount=db.tx(()->{
   Row cart=carts.lockCart(customer);if(cart.number("id")!=form.id_carrinho())throw error(409,"CARRINHO_DIVERGENTE","Carrinho indisponível.");
   CartSnapshot snapshot=carts.snapshot(cart);var coupon=carts.evaluate(cart,snapshot);Row freight=carts.validateQuote(cart,snapshot,form.cotacao_id().toString());
   return (coupon==null?snapshot.totalBeforeCouponAndShipping():coupon.totalBeforeShipping()).add(freight.money("price"));
  });
  CheckoutGuard.Assessment risk=guard.assess(customer,form.id_carrinho(),amount,ip);
  if(risk.blocked())throw error(403,"SUSPEITA_FRAUDE","A transação foi bloqueada. Entre em contato com o suporte.");
  return db.tx(()->{
   db.one("SELECT id FROM checkout_customers WHERE id=:id FOR UPDATE","id",customer);
   Optional<JsonNode> concurrent=replay(customer,keyHash,requestHash);if(concurrent.isPresent())return concurrent.get();
   Row cart=carts.lockCart(customer);if(cart.number("id")!=form.id_carrinho())throw error(409,"CARRINHO_DIVERGENTE","Carrinho indisponível.");
   CartSnapshot snapshot=carts.snapshot(cart);
   if(cart.text("coupon_code")!=null)db.one("SELECT id FROM coupons WHERE code=:code FOR UPDATE","code",cart.text("coupon_code"));
   CouponService.Evaluation coupon=carts.evaluate(cart,snapshot);Row freight=carts.validateQuote(cart,snapshot,form.cotacao_id().toString());
   String cep=CheckoutCartService.normalizeCep(form.endereco_entrega().getPostalCode());
   if(!cep.equals(freight.text("postal_code")))throw error(409,"CEP_DIVERGENTE","O frete pertence a outro CEP.");
   carts.validateAddress(cep,form.endereco_entrega().getCity(),form.endereco_entrega().getState());
   BigDecimal discount=coupon==null?Money.ZERO:coupon.discount();
   BigDecimal total=snapshot.totalBeforeCouponAndShipping().subtract(discount).add(freight.money("price"));
   if(total.signum()<=0||total.compareTo(amount)!=0)throw error(409,"TOTAL_ALTERADO","Os valores foram alterados. Revise o checkout.");
   String publicId=UUID.randomUUID().toString();Instant now=clock.instant();String method=method(form.metodo_pagamento());
   long order=db.insert("""
    INSERT INTO orders (public_id,customer_id,cart_id,payment_status,payment_method,subtotal,automatic_discount,coupon_discount,shipping_amount,total_amount,coupon_id,coupon_code,shipping_service,delivery_business_days,customer_data_ciphertext,delivery_address_ciphertext,pii_retain_until,created_at,updated_at)
    VALUES (:public,:customer,:cart,'PROCESSING',:method,:subtotal,:automatic,:discount,:shipping,:total,:couponId,:couponCode,:service,:days,:customerData,:address,:retention,:now,:now)
    ""","public",publicId,"customer",customer,"cart",snapshot.cartId(),"method",method,"subtotal",snapshot.subtotal(),"automatic",snapshot.automaticDiscount(),"discount",discount,"shipping",freight.money("price"),"total",total,"couponId",coupon==null?null:coupon.couponId(),"couponCode",coupon==null?null:coupon.code(),"service",freight.text("service_name"),"days",freight.number("delivery_business_days"),"customerData",crypto.encrypt("order-customer:"+publicId,crypto.json(form.dados_cliente())),"address",crypto.encrypt("order-address:"+publicId,crypto.json(form.endereco_entrega())),"retention",now.plus(Duration.ofDays(retentionDays)),"now",now);
   for(CartSnapshot.Item item:snapshot.items())db.update("INSERT INTO order_items (order_id,product_id,product_name,product_photo,unit_price,quantity,subtotal,automatic_discount) VALUES (:order,:product,:name,:photo,:price,:quantity,:subtotal,:discount)","order",order,"product",item.productId(),"name",item.name(),"photo",item.photo(),"price",item.unitPrice(),"quantity",item.quantity(),"subtotal",item.subtotal(),"discount",item.automaticDiscount());
   if(coupon!=null){
    db.update("UPDATE coupons SET reserved_uses=reserved_uses+1,version=version+1 WHERE id=:id","id",coupon.couponId());
    db.update("INSERT INTO coupon_redemptions (coupon_id,customer_id,order_id,status,reserved_until,created_at,updated_at) VALUES (:coupon,:customer,:order,'RESERVED',:until,:now,:now)","coupon",coupon.couponId(),"customer",customer,"order",order,"until",now.plusSeconds(86400),"now",now);
   }
   String operationKey=UUID.randomUUID().toString();
   db.update("INSERT INTO payment_transactions (order_id,operation_key,gateway,status,amount,method,installments,created_at,updated_at) VALUES (:order,:operation,'MERCADO_PAGO','PROCESSING',:amount,:method,:installments,:now,:now)","order",order,"operation",operationKey,"amount",total,"method",method,"installments",form.parcelas(),"now",now);
   Map<String,Object> command=new LinkedHashMap<>();command.put("external_reference",publicId);command.put("transaction_amount",total);command.put("description","Pedido "+publicId);command.put("installments",form.parcelas());
   command.put("payer",Map.of("email",form.dados_cliente().getEmail(),"first_name",form.dados_cliente().getName(),"identification",Map.of("type","CPF","number",form.dados_cliente().getCpf())));
   if(method.equals("PIX")){command.put("payment_method_id","pix");command.put("date_of_expiration",now.plusSeconds(1800).atOffset(ZoneOffset.UTC).toString());}
   else{command.put("payment_method_id",form.payment_method_id());command.put("token",form.token_cartao());if(method.equals("CREDIT_CARD"))command.put("three_d_secure_mode","optional");}
   db.update("INSERT INTO checkout_outbox (event_key,aggregate_id,event_type,payload_ciphertext,status,next_attempt_at,created_at) VALUES (:key,:order,'CREATE_PAYMENT',:payload,'PENDING',:now,:now)","key","PAY:"+publicId,"order",order,"payload",crypto.encrypt("payment-command:"+publicId,crypto.json(command)),"now",now);
   db.update("INSERT INTO checkout_consents (customer_id,purpose,policy_version,granted,recorded_at) VALUES (:customer,'CHECKOUT',:version,TRUE,:now)","customer",customer,"version",policyVersion,"now",now);
   db.update("UPDATE checkout_carts SET status='CHECKED_OUT',updated_at=:now WHERE id=:id","now",now,"id",snapshot.cartId());
   JsonNode response=crypto.tree(crypto.json(Map.of("sucesso",true,"id_pedido",publicId,"status_pagamento","processando","consulta","/api/checkout/pedidos/"+publicId)));
   db.update("INSERT INTO checkout_idempotency_keys (customer_id,operation,key_sha256,request_hmac,order_id,status,response_http_status,response_ciphertext,expires_at,created_at,updated_at) VALUES (:customer,'FINALIZE',:key,:hash,:order,'COMPLETED',202,:response,:expires,:now,:now)","customer",customer,"key",keyHash,"hash",requestHash,"order",order,"response",crypto.encrypt("idempotency:"+customer+":"+keyHash,crypto.json(response)),"expires",now.plusSeconds(86400),"now",now);
   guard.audit(customer,order,"CHECKOUT","CREATED",crypto.hmac("ip",ip));return response;
  });
 }
 private Optional<JsonNode> replay(long customer,String keyHash,String requestHash){
  Optional<Row> saved=db.optional("SELECT * FROM checkout_idempotency_keys WHERE customer_id=:customer AND operation='FINALIZE' AND key_sha256=:key","customer",customer,"key",keyHash);
  if(saved.isEmpty())return Optional.empty();Row row=saved.get();
  if(!row.text("request_hmac").equals(requestHash))throw error(409,"IDEMPOTENCIA_CONFLITO","A chave já foi utilizada com outro conteúdo.");
  return Optional.of(crypto.tree(crypto.decrypt("idempotency:"+customer+":"+keyHash,row.text("response_ciphertext"))));
 }
 public Map<String,Object> order(long customer,String publicId){return db.tx(()->{
  Row row=db.optional("SELECT * FROM orders WHERE public_id=:public AND customer_id=:customer","public",publicId,"customer",customer).orElseThrow(()->error(404,"PEDIDO_NAO_ENCONTRADO","Pedido não encontrado."));
  Map<String,Object> result=new LinkedHashMap<>();result.put("sucesso",true);result.put("id_pedido",publicId);result.put("status_pagamento",row.text("payment_status"));result.put("metodo_pagamento",row.text("payment_method"));
  result.put("resumo_pedido",Map.of("subtotal",row.money("subtotal"),"desconto_automatico",row.money("automatic_discount"),"desconto_cupom",row.money("coupon_discount"),"frete",row.money("shipping_amount"),"total",row.money("total_amount")));
  if(row.text("delivery_address_ciphertext")!=null)result.put("endereco_entrega",crypto.tree(crypto.decrypt("order-address:"+publicId,row.text("delivery_address_ciphertext"))));
  Row payment=db.one("SELECT * FROM payment_transactions WHERE order_id=:order ORDER BY id DESC LIMIT 1","order",row.number("id"));
  if(payment.text("gateway_result_ciphertext")!=null)result.put("pagamento",crypto.tree(crypto.decrypt("payment-result:"+publicId,payment.text("gateway_result_ciphertext"))));
  guard.audit(customer,row.number("id"),"ORDER_READ","SUCCESS",null);return result;
 });}
 private void validate(Finish form){
  customerValidation.validate(form.dados_cliente(),form.endereco_entrega());
  if(!form.consentimento()||!policyVersion.equals(form.versao_politica()))throw error(400,"CONSENTIMENTO_INVALIDO","Confirme a versão vigente da política.");
  boolean pix=form.metodo_pagamento().equals("pix"),debit=form.metodo_pagamento().equals("cartao_debito");paymentValidation.validateInstallments(form.parcelas(),debit||pix);
  if(!pix&&(form.token_cartao()==null||form.token_cartao().isBlank()||form.payment_method_id()==null||!form.payment_method_id().matches("[a-zA-Z0-9_]{1,50}")))throw error(400,"TOKEN_CARTAO_INVALIDO","Informe o token de pagamento.");
  if(pix&&form.token_cartao()!=null)throw error(400,"DADOS_INCOMPATIVEIS","Pix não utiliza token de cartão.");
 }
 private String method(String method){return switch(method){case "pix"->"PIX";case "cartao_credito"->"CREDIT_CARD";case "cartao_debito"->"DEBIT_CARD";default->throw error(400,"METODO_INVALIDO","Método inválido.");};}
}
