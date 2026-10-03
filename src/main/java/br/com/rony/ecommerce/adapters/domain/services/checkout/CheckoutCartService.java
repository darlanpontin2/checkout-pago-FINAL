package br.com.rony.ecommerce.adapters.domain.services.checkout;

import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore;
import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore.Row;
import br.com.rony.ecommerce.adapters.infrastructure.checkout.CheckoutCrypto;
import br.com.rony.ecommerce.data.repository.checkout.ShippingRepository;
import br.com.rony.ecommerce.domain.entities.checkout.CartSnapshot;
import br.com.rony.ecommerce.domain.exceptions.CheckoutException;
import br.com.rony.ecommerce.domain.services.checkout.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CheckoutCartService {
 private final CheckoutStore db;private final CheckoutCrypto crypto;private final CouponService coupons;
 private final ShippingService shipping;private final ShippingRepository shippingRepository;private final Clock clock;
 public CheckoutCartService(CheckoutStore db,CheckoutCrypto crypto,CouponService coupons,ShippingService shipping,ShippingRepository shippingRepository,Clock clock){this.db=db;this.crypto=crypto;this.coupons=coupons;this.shipping=shipping;this.shippingRepository=shippingRepository;this.clock=clock;}
 public long customer(String issuer,String subject){
  String key=crypto.hmac("identity",issuer+"\n"+subject);
  return db.tx(()->{
   db.update("INSERT INTO checkout_customers (subject_key,created_at,updated_at) VALUES (:key,:now,:now) ON DUPLICATE KEY UPDATE subject_key=subject_key","key",key,"now",clock.instant());
   return db.one("SELECT id FROM checkout_customers WHERE subject_key=:key","key",key).number("id");
  });
 }
 public Row lockCart(long customer){
  db.one("SELECT id FROM checkout_customers WHERE id=:id FOR UPDATE","id",customer);
  Optional<Row> current=db.optional("SELECT * FROM checkout_carts WHERE customer_id=:customer AND status='OPEN' FOR UPDATE","customer",customer);
  if(current.isPresent())return current.get();
  long id=db.insert("INSERT INTO checkout_carts (customer_id,status,created_at,updated_at) VALUES (:customer,'OPEN',:now,:now)","customer",customer,"now",clock.instant());
  return db.one("SELECT * FROM checkout_carts WHERE id=:id","id",id);
 }
 public Map<String,Object> changeItem(long customer,long product,int quantity){
  return db.tx(()->{
   Row cart=lockCart(customer);long id=cart.number("id");
   if(db.optional("SELECT id FROM products WHERE id=:id","id",product).isEmpty())throw error(404,"PRODUTO_NAO_ENCONTRADO","Produto não encontrado.");
   if(quantity==0){db.update("DELETE FROM checkout_cart_items WHERE cart_id=:cart AND product_id=:product","cart",id,"product",product);}
   else{
    long count=db.one("SELECT COUNT(*) AS total FROM checkout_cart_items WHERE cart_id=:cart","cart",id).number("total");
    boolean exists=db.optional("SELECT id FROM checkout_cart_items WHERE cart_id=:cart AND product_id=:product","cart",id,"product",product).isPresent();
    if(!exists&&count>=500)throw error(400,"LIMITE_ITENS","Limite de itens atingido.");
    db.update("INSERT INTO checkout_cart_items (cart_id,product_id,quantity) VALUES (:cart,:product,:quantity) ON DUPLICATE KEY UPDATE quantity=:quantity","cart",id,"product",product,"quantity",quantity);
   }
   db.update("UPDATE checkout_carts SET revision=revision+1,updated_at=:now WHERE id=:id","now",clock.instant(),"id",id);
   db.update("DELETE FROM checkout_shipping_selections WHERE cart_id=:id","id",id);
   return Map.of("sucesso",true,"id_carrinho",id);
  });
 }
 public CartSnapshot snapshot(Row cart){
  List<Row> products=db.rows("""
   SELECT p.id,p.category_id,p.name,p.price,ci.quantity,
    (SELECT pi.url FROM product_images pi WHERE pi.product_id=p.id ORDER BY pi.image_order,pi.`key` LIMIT 1) AS photo,
    COALESCE((SELECT MAX(pr.percentage) FROM checkout_promotions pr
     WHERE pr.active=TRUE AND pr.starts_at<=:now AND pr.ends_at>:now
      AND ((pr.product_id IS NULL AND pr.category_id IS NULL) OR pr.product_id=p.id OR pr.category_id=p.category_id)),0) AS percentage
   FROM checkout_cart_items ci JOIN products p ON p.id=ci.product_id WHERE ci.cart_id=:cart ORDER BY p.id
   ""","now",clock.instant(),"cart",cart.number("id"));
  if(products.isEmpty())throw error(400,"CARRINHO_VAZIO","O carrinho está vazio.");
  List<CartSnapshot.Item> items=products.stream().map(row->{
   BigDecimal price=Money.exact(row.money("price"));int quantity=Math.toIntExact(row.number("quantity"));
   return new CartSnapshot.Item(row.number("id"),row.number("category_id"),row.text("name"),row.text("photo"),price,quantity,Money.percentage(price.multiply(BigDecimal.valueOf(quantity)),row.money("percentage")));
  }).toList();
  return new CartSnapshot(cart.number("id"),cart.number("customer_id"),cart.number("revision"),items);
 }
 public CouponService.Evaluation evaluate(Row cart,CartSnapshot snapshot){String code=cart.text("coupon_code");return code==null?null:coupons.evaluate(code,snapshot);}
 public Map<String,Object> summary(long customer){return db.tx(()->{Row cart=lockCart(customer);CartSnapshot snapshot=snapshot(cart);return summary(snapshot,evaluate(cart,snapshot));});}
 public Map<String,Object> summary(CartSnapshot cart,CouponService.Evaluation coupon){
  Map<String,Object> result=new LinkedHashMap<>();result.put("id_carrinho",cart.cartId());
  result.put("itens",cart.items().stream().map(item->{
   Map<String,Object> view=new LinkedHashMap<>();view.put("produto_id",item.productId());view.put("nome",item.name());view.put("foto",item.photo());view.put("valor_unitario",item.unitPrice());view.put("quantidade",item.quantity());view.put("valor_total",item.subtotal());return view;
  }).toList());
  result.put("subtotal",cart.subtotal());result.put("desconto_automatico",cart.automaticDiscount());result.put("cupom_aplicado",coupon==null?null:coupon.code());
  result.put("desconto_cupom",coupon==null?Money.ZERO:coupon.discount());result.put("total_antes_frete",coupon==null?cart.totalBeforeCouponAndShipping():coupon.totalBeforeShipping());return result;
 }
 public Map<String,Object> coupon(long customer,String code){return db.tx(()->{
  Row cart=lockCart(customer);CartSnapshot snapshot=snapshot(cart);CouponService.Evaluation evaluation=code==null?null:coupons.evaluate(code,snapshot);
  db.update("UPDATE checkout_carts SET coupon_code=:code,updated_at=:now WHERE id=:id","code",evaluation==null?null:evaluation.code(),"now",clock.instant(),"id",cart.number("id"));
  return summary(snapshot,evaluation);
 });}
 public List<Map<String,Object>> quotes(long customer,String postalCode,ShippingService.Sort sort){return db.tx(()->{
  Row cart=lockCart(customer);CartSnapshot snapshot=snapshot(cart);String cep=normalizeCep(postalCode);
  // Revalidar tarifas e logística antes de aproveitar o registro de cotação.
  List<ShippingService.Option> options=shipping.calculate(snapshot,cep,sort);String fingerprint=fingerprint(snapshot,options);List<Map<String,Object>> result=new ArrayList<>();
  for(ShippingService.Option option:options){
   Row quote=db.optional("SELECT * FROM checkout_shipping_quotes WHERE cart_id=:cart AND customer_id=:customer AND postal_code=:cep AND tariff_id=:tariff AND cart_fingerprint=:fingerprint AND expires_at>:now ORDER BY created_at DESC LIMIT 1","cart",snapshot.cartId(),"customer",customer,"cep",cep,"tariff",option.tariffId(),"fingerprint",fingerprint,"now",clock.instant()).orElseGet(()->{
    String id=UUID.randomUUID().toString();
    db.update("""
     INSERT INTO checkout_shipping_quotes
      (id,cart_id,customer_id,tariff_id,cart_fingerprint,postal_code,service_code,service_name,price,delivery_business_days,estimated_delivery_date,expires_at,created_at)
     VALUES (:id,:cart,:customer,:tariff,:fingerprint,:cep,:code,:name,:price,:days,:date,:expires,:now)
     ""","id",id,"cart",snapshot.cartId(),"customer",customer,"tariff",option.tariffId(),"fingerprint",fingerprint,"cep",cep,"code",option.serviceCode(),"name",option.serviceName(),"price",option.shippingAmount(),"days",option.deliveryBusinessDays(),"date",java.sql.Date.valueOf(option.estimatedDeliveryDate()),"expires",clock.instant().plusSeconds(600),"now",clock.instant());
    return db.one("SELECT * FROM checkout_shipping_quotes WHERE id=:id","id",id);
   });
   result.add(quoteView(quote));
  }
  return result;
 });}
 public Row validateQuote(Row cart,CartSnapshot snapshot,String quoteId){
  Row quote=db.optional("SELECT * FROM checkout_shipping_quotes WHERE id=:id AND cart_id=:cart AND customer_id=:customer","id",quoteId,"cart",cart.number("id"),"customer",cart.number("customer_id")).orElseThrow(()->error(404,"FRETE_NAO_ENCONTRADO","Cotação não encontrada."));
  if(!clock.instant().isBefore(quote.instant("expires_at")))throw error(409,"FRETE_EXPIRADO","Calcule o frete novamente.");
  List<ShippingService.Option> options=shipping.calculate(snapshot,quote.text("postal_code"),ShippingService.Sort.PRICE);
  if(!fingerprint(snapshot,options).equals(quote.text("cart_fingerprint")))throw error(409,"FRETE_DESATUALIZADO","Calcule o frete novamente.");
  return quote;
 }
 public Map<String,Object> select(long customer,String quoteId){return db.tx(()->{
  Row cart=lockCart(customer);Row quote=validateQuote(cart,snapshot(cart),quoteId);
  db.update("INSERT INTO checkout_shipping_selections (cart_id,quote_id,selected_at) VALUES (:cart,:quote,:now) ON DUPLICATE KEY UPDATE quote_id=:quote,selected_at=:now","cart",cart.number("id"),"quote",quoteId,"now",clock.instant());
  return quoteView(quote);
 });}
 public Map<String,Object> selected(long customer){return db.tx(()->{
  Row cart=lockCart(customer);Row selection=db.optional("SELECT quote_id FROM checkout_shipping_selections WHERE cart_id=:cart","cart",cart.number("id")).orElseThrow(()->error(404,"FRETE_NAO_SELECIONADO","Selecione um frete."));
  return quoteView(validateQuote(cart,snapshot(cart),selection.text("quote_id")));
 });}
 public void validateAddress(String postalCode,String city,String state){
  ShippingRepository.PostalCode registered=shippingRepository.findPostalCode(normalizeCep(postalCode)).orElseThrow(()->error(400,"CEP_NAO_ATENDIDO","CEP não atendido."));
  if(!registered.state().equalsIgnoreCase(state)||!registered.city().equalsIgnoreCase(city.strip()))throw error(400,"ENDERECO_DIVERGENTE","Cidade ou estado não correspondem ao CEP.");
 }
 private String fingerprint(CartSnapshot snapshot,List<ShippingService.Option> options){
  Set<Long> ids=snapshot.items().stream().map(CartSnapshot.Item::productId).collect(Collectors.toSet());
  var measurements=shippingRepository.findMeasurements(ids).stream().sorted(Comparator.comparing(ShippingRepository.ProductMeasurements::productId)).toList();
  var ordered=options.stream().sorted(Comparator.comparing(ShippingService.Option::tariffId)).toList();
  return crypto.hash(crypto.json(List.of(snapshot,measurements,ordered)));
 }
 public static String normalizeCep(String cep){if(cep==null||!cep.matches("[0-9]{5}-?[0-9]{3}"))throw error(400,"CEP_INVALIDO","Informe um CEP válido.");return cep.replace("-","");}
 private Map<String,Object> quoteView(Row row){return Map.of("cotacao_id",row.text("id"),"tipo_frete",row.text("service_name"),"prazo_dias",row.number("delivery_business_days"),"valor_frete",row.money("price"),"data_entrega_estimada",row.text("estimated_delivery_date"),"expira_em",row.instant("expires_at").toString());}
 public static CheckoutException error(int status,String code,String message){return new CheckoutException(status,code,message);}
}
