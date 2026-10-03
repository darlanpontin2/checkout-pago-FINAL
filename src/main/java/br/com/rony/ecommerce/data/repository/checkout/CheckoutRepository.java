package br.com.rony.ecommerce.data.repository.checkout;
import br.com.rony.ecommerce.domain.entities.checkout.Coupon;
import br.com.rony.ecommerce.domain.entities.checkout.Order;
import java.time.Instant;
import java.util.*;
public interface CheckoutRepository {
 Optional<Coupon> findCouponByCode(String code, boolean lock);
 long countCustomerCouponUses(Long couponId, Long customerId);
 Optional<Order> findOrder(String publicId, Long customerId, boolean lock);
 <T> T persist(T entity);
 <T> Optional<T> findById(Class<T> type, Long id, boolean lock);
 List<Long> findWebhookIdsReadyForProcessing(Instant now, int limit);
 void flush();
}
