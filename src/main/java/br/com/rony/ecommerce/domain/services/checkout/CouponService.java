package br.com.rony.ecommerce.domain.services.checkout;
import br.com.rony.ecommerce.domain.entities.checkout.CartSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
public interface CouponService {
 Evaluation evaluate(String code, CartSnapshot cart);
 record Evaluation(Long couponId, String code, String type, BigDecimal configuredValue, BigDecimal eligibleAmount, BigDecimal discount, BigDecimal minimumPurchase, Instant expiresAt, BigDecimal totalBeforeShipping) {}
}
