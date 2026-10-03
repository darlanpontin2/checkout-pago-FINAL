package br.com.rony.ecommerce.domain.entities.checkout;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
public interface Coupon {
 Long getId(); String getCode(); CouponType getDiscountType(); BigDecimal getDiscountValue();
 BigDecimal getMinimumPurchase(); BigDecimal getMaximumDiscount(); Instant getStartsAt(); Instant getEndsAt();
 Long getMaximumUses(); Long getMaximumUsesPerCustomer(); long getCurrentUses(); long getReservedUses();
 boolean isActive(); Set<Long> getProductIds(); Set<Long> getCategoryIds();
}
