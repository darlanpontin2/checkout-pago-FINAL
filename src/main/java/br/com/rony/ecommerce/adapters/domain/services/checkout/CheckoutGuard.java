package br.com.rony.ecommerce.adapters.domain.services.checkout;

import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore;
import br.com.rony.ecommerce.adapters.infrastructure.checkout.CheckoutCrypto;
import br.com.rony.ecommerce.domain.exceptions.CheckoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class CheckoutGuard {
 private final CheckoutStore db;private final CheckoutCrypto crypto;private final Clock clock;
 private final int threshold;private final BigDecimal highValue;private final Set<String> blockedCountries;
 public CheckoutGuard(CheckoutStore db,CheckoutCrypto crypto,Clock clock,@Value("${checkout.fraud.threshold:8}") int threshold,@Value("${checkout.fraud.high-value:5000.00}") BigDecimal highValue,@Value("${checkout.fraud.blocked-countries:}") String blockedCountries){
  this.db=db;this.crypto=crypto;this.clock=clock;this.threshold=threshold;this.highValue=highValue;this.blockedCountries=new HashSet<>();
  Arrays.stream(blockedCountries.split(",")).map(String::strip).filter(v->!v.isEmpty()).map(v->v.toUpperCase(Locale.ROOT)).forEach(this.blockedCountries::add);
 }
 public void rate(String identity,String ip,boolean payment){
  boolean allowed=db.independent(()->{
   // Incrementar ambos os contadores mesmo quando um limite é atingido.
   boolean user=bucket((payment?"PAY:":"API:")+identity,payment?5:120);
   boolean address=bucket((payment?"PAYIP:":"APIIP:")+ip,payment?20:300);
   return user&&address;
  });
  if(!allowed)throw new CheckoutException(429,"LIMITE_REQUISICOES","Muitas tentativas. Aguarde antes de tentar novamente.");
 }
 private boolean bucket(String source,int limit){
  long window=clock.instant().getEpochSecond()/60;String key=crypto.hmac("rate",source);
  db.update("INSERT INTO checkout_rate_buckets (bucket_key,window_start,request_count,expires_at) VALUES (:key,:window,1,:expires) ON DUPLICATE KEY UPDATE request_count=request_count+1", "key",key,"window",window,"expires",clock.instant().plusSeconds(120));
  return db.one("SELECT request_count FROM checkout_rate_buckets WHERE bucket_key=:key AND window_start=:window FOR UPDATE","key",key,"window",window).number("request_count")<=limit;
 }
 public Assessment assess(long customer,long cart,BigDecimal amount,String ip){
  // Preservar a avaliação mesmo se a transação de checkout for revertida.
  return db.independent(()->{
   String ipHash=crypto.hmac("ip",ip);Instant since=clock.instant().minusSeconds(600);
   long attempts=db.one("SELECT COUNT(*) AS total FROM checkout_fraud_assessments WHERE (customer_id=:customer OR ip_hmac=:ip) AND created_at>=:since","customer",customer,"ip",ipHash,"since",since).number("total");
   boolean trusted=db.one("SELECT trusted_customer FROM checkout_customers WHERE id=:id","id",customer).bool("trusted_customer");
   String country=db.optional("SELECT country_code FROM checkout_ip_locations WHERE ip_hmac=:ip AND expires_at>:now","ip",ipHash,"now",clock.instant()).map(r->r.text("country_code")).orElse(null);
   int score=0;List<String> reasons=new ArrayList<>();
   if(!trusted&&amount.compareTo(highValue)>=0){score+=4;reasons.add("HIGH_VALUE");}
   if(attempts>=3){score+=8;reasons.add("VELOCITY");}
   if(country!=null&&blockedCountries.contains(country)){score+=10;reasons.add("COUNTRY_RULE");}
   if(!trusted&&country==null){score++;reasons.add("LOCATION_UNAVAILABLE");}
   boolean blocked=score>=threshold;
   db.update("INSERT INTO checkout_fraud_assessments (customer_id,cart_id,ip_hmac,score,blocked,reason_codes,created_at) VALUES (:customer,:cart,:ip,:score,:blocked,:reasons,:now)","customer",customer,"cart",cart,"ip",ipHash,"score",score,"blocked",blocked,"reasons",String.join(",",reasons),"now",clock.instant());
   audit(customer,null,"FRAUD",blocked?"BLOCKED":"ALLOWED",ipHash);
   return new Assessment(score,blocked);
  });
 }
 public void audit(Long customer,Long order,String action,String outcome,String ipHash){
  db.update("INSERT INTO checkout_audit_logs (customer_id,order_id,action,outcome,correlation_id,ip_hmac,retain_until,created_at) VALUES (:customer,:order,:action,:outcome,:correlation,:ip,:retention,:now)","customer",customer,"order",order,"action",action,"outcome",outcome,"correlation",UUID.randomUUID().toString(),"ip",ipHash,"retention",clock.instant().plus(Duration.ofDays(180)),"now",clock.instant());
 }
 public record Assessment(int score,boolean blocked){}
}
