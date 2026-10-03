package br.com.rony.ecommerce.adapters.domain.services.checkout;
import br.com.rony.ecommerce.data.repository.checkout.CheckoutRepository;
import br.com.rony.ecommerce.domain.entities.checkout.*;
import br.com.rony.ecommerce.domain.exceptions.CheckoutException;
import br.com.rony.ecommerce.domain.services.checkout.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.Locale;
@Service
public class CouponServiceImpl implements CouponService {
 private final CheckoutRepository repository;private final Clock clock;
 public CouponServiceImpl(CheckoutRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
 @Transactional(readOnly=true)
 public Evaluation evaluate(String code,CartSnapshot cart){
  Coupon coupon=repository.findCouponByCode(normalizeCode(code),false).orElseThrow(()->error("CUPOM_INVALIDO","O cupom informado não existe."));
  return evaluate(coupon,cart,repository.countCustomerCouponUses(coupon.getId(),cart.customerId()),clock.instant());
 }
 /** Reavaliar na finalização com bloqueio do cupom e contagem na mesma transação. */
 public Evaluation evaluate(Coupon coupon,CartSnapshot cart,long customerUses,Instant now){
  if(!coupon.isActive())throw error("CUPOM_INATIVO","O cupom está inativo.");
  if(now.isBefore(coupon.getStartsAt()))throw error("CUPOM_NAO_INICIADO","O período de utilização deste cupom ainda não começou.");
  if(!now.isBefore(coupon.getEndsAt()))throw error("CUPOM_EXPIRADO","O cupom está expirado.");
  long committed=Math.addExact(coupon.getCurrentUses(),coupon.getReservedUses());
  if(coupon.getMaximumUses()!=null&&committed>=coupon.getMaximumUses())throw error("CUPOM_ESGOTADO","O limite de utilização deste cupom foi atingido.");
  if(coupon.getMaximumUsesPerCustomer()!=null&&customerUses>=coupon.getMaximumUsesPerCustomer())throw error("CUPOM_LIMITE_CLIENTE","Você já atingiu o limite de utilização deste cupom.");
  BigDecimal base=cart.totalBeforeCouponAndShipping();
  if(base.compareTo(coupon.getMinimumPurchase())<0)throw error("CUPOM_VALOR_MINIMO","O carrinho não atingiu o valor mínimo exigido pelo cupom.");
  boolean unrestricted=coupon.getProductIds().isEmpty()&&coupon.getCategoryIds().isEmpty();
  BigDecimal eligible=cart.items().stream().filter(i->unrestricted||coupon.getProductIds().contains(i.productId())||coupon.getCategoryIds().contains(i.categoryId())).map(CartSnapshot.Item::netAmount).reduce(Money.ZERO,BigDecimal::add);
  if(eligible.signum()==0)throw error("CUPOM_NAO_APLICAVEL","O cupom não é aplicável aos produtos deste carrinho.");
  BigDecimal discount=coupon.getDiscountType()==CouponType.PERCENTAGE?Money.percentage(eligible,coupon.getDiscountValue()):Money.exact(coupon.getDiscountValue());
  if(coupon.getMaximumDiscount()!=null)discount=discount.min(coupon.getMaximumDiscount());
  discount=Money.exact(discount.min(eligible));
  return new Evaluation(coupon.getId(),coupon.getCode(),coupon.getDiscountType().name(),coupon.getDiscountValue(),eligible,discount,coupon.getMinimumPurchase(),coupon.getEndsAt(),base.subtract(discount));
 }
 public String normalizeCode(String code){
  if(code==null||code.length()>40)throw invalidFormat();String normalized=code.strip().toUpperCase(Locale.ROOT);
  if(!normalized.matches("[A-Z0-9][A-Z0-9_-]{2,39}"))throw invalidFormat();return normalized;
 }
 private CheckoutException invalidFormat(){return error("CUPOM_FORMATO_INVALIDO","O cupom deve ter entre 3 e 40 caracteres alfanuméricos, hífen ou sublinhado.");}
 private CheckoutException error(String code,String message){return new CheckoutException(400,code,message,"codigo_cupom");}
}
