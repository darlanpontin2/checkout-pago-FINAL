package br.com.rony.ecommerce.adapters.data.repository.checkout;
import br.com.rony.ecommerce.adapters.domain.entities.checkout.*;
import br.com.rony.ecommerce.data.repository.checkout.CheckoutRepository;
import br.com.rony.ecommerce.domain.entities.checkout.Coupon;
import br.com.rony.ecommerce.domain.entities.checkout.Order;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class CheckoutRepositoryImpl implements CheckoutRepository {
 private final EntityManager em;
 public CheckoutRepositoryImpl(EntityManager em){this.em=em;}
 public Optional<Coupon> findCouponByCode(String code, boolean lock){
  var q=em.createQuery("SELECT c FROM CouponImpl c WHERE c.code = :code",CouponImpl.class).setParameter("code",code);
  if(lock) q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
  return q.getResultStream().findFirst().map(c->{c.getProductIds().size();c.getCategoryIds().size();return (Coupon)c;});
 }
 public long countCustomerCouponUses(Long couponId,Long customerId){
  return ((Number)em.createNativeQuery("SELECT COUNT(*) FROM coupon_redemptions WHERE coupon_id = :coupon AND customer_id = :customer AND status IN ('RESERVED','CONSUMED')")
   .setParameter("coupon",couponId).setParameter("customer",customerId).getSingleResult()).longValue();
 }
 public Optional<Order> findOrder(String publicId,Long customerId,boolean lock){
  var q=em.createQuery("SELECT o FROM OrderImpl o WHERE o.publicId = :publicId AND o.customerId = :customerId",OrderImpl.class)
   .setParameter("publicId",publicId).setParameter("customerId",customerId);
  if(lock) q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
  return q.getResultStream().findFirst().map(o->(Order)o);
 }
 public <T> T persist(T entity){em.persist(entity);return entity;}
 public <T> Optional<T> findById(Class<T> type,Long id,boolean lock){return Optional.ofNullable(lock?em.find(type,id,LockModeType.PESSIMISTIC_WRITE):em.find(type,id));}
 public List<Long> findWebhookIdsReadyForProcessing(Instant now,int limit){
  if(limit<1||limit>500) throw new IllegalArgumentException("Limite de processamento inválido.");
  return em.createQuery("SELECT w.id FROM WebhookLogImpl w WHERE (w.status = :received OR w.status = :retry OR w.status = :processing) AND w.nextAttemptAt <= :now AND (w.lockedUntil IS NULL OR w.lockedUntil <= :now) ORDER BY w.receivedAt, w.id",Long.class)
   .setParameter("received",WebhookLogImpl.Status.RECEIVED).setParameter("retry",WebhookLogImpl.Status.RETRY).setParameter("processing",WebhookLogImpl.Status.PROCESSING)
   .setParameter("now",now).setMaxResults(limit).getResultList();
 }
 public void flush(){em.flush();}
}
