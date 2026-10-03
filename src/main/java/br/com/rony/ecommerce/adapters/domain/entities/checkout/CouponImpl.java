package br.com.rony.ecommerce.adapters.domain.entities.checkout;
import br.com.rony.ecommerce.adapters.domain.entities.commons.EntityWithLongId;
import br.com.rony.ecommerce.domain.entities.checkout.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="coupons") @Access(AccessType.FIELD)
public class CouponImpl extends EntityWithLongId implements Coupon {
 @Column(nullable=false, unique=true, length=40) private String code;
 @Enumerated(EnumType.STRING) @Column(name="discount_type",nullable=false) private CouponType discountType;
 @Column(name="discount_value",nullable=false,precision=15,scale=2) private BigDecimal discountValue;
 @Column(name="minimum_purchase",nullable=false,precision=15,scale=2) private BigDecimal minimumPurchase;
 @Column(name="maximum_discount",precision=15,scale=2) private BigDecimal maximumDiscount;
 @Column(name="starts_at",nullable=false) private Instant startsAt;
 @Column(name="ends_at",nullable=false) private Instant endsAt;
 @Column(name="maximum_uses") private Long maximumUses;
 @Column(name="maximum_uses_per_customer") private Long maximumUsesPerCustomer;
 @Column(name="current_uses",nullable=false) private long currentUses;
 @Column(name="reserved_uses",nullable=false) private long reservedUses;
 @Column(nullable=false) private boolean active;
 @Version private long version;
 @ElementCollection @CollectionTable(name="coupon_products",joinColumns=@JoinColumn(name="coupon_id")) @Column(name="product_id") private Set<Long> productIds = new HashSet<>();
 @ElementCollection @CollectionTable(name="coupon_categories",joinColumns=@JoinColumn(name="coupon_id")) @Column(name="category_id") private Set<Long> categoryIds = new HashSet<>();
 protected CouponImpl() {}
 public CouponImpl(String code, CouponType discountType, BigDecimal discountValue, BigDecimal minimumPurchase, BigDecimal maximumDiscount, Instant startsAt, Instant endsAt, Long maximumUses, Long maximumUsesPerCustomer, Set<Long> productIds, Set<Long> categoryIds) {
  this.code=code; this.discountType=discountType; this.discountValue=discountValue; this.minimumPurchase=minimumPurchase; this.maximumDiscount=maximumDiscount;
  this.startsAt=startsAt; this.endsAt=endsAt; this.maximumUses=maximumUses; this.maximumUsesPerCustomer=maximumUsesPerCustomer;
  this.productIds=new HashSet<>(productIds); this.categoryIds=new HashSet<>(categoryIds); this.active=true;
 }
 /** Chamar com lock pessimista na mesma transação da reserva. */
 public void reserveUse() {
  if (maximumUses != null && currentUses + reservedUses >= maximumUses) throw new IllegalStateException("Limite global do cupom atingido.");
  reservedUses++;
 }
 public void consumeReservedUse() { if(reservedUses<=0) throw new IllegalStateException("Não existe reserva para consumir."); reservedUses--; currentUses++; }
 public void releaseReservedUse() { if(reservedUses<=0) throw new IllegalStateException("Não existe reserva para liberar."); reservedUses--; }
 public String getCode(){return code;} public CouponType getDiscountType(){return discountType;}
 public BigDecimal getDiscountValue(){return discountValue;} public BigDecimal getMinimumPurchase(){return minimumPurchase;}
 public BigDecimal getMaximumDiscount(){return maximumDiscount;} public Instant getStartsAt(){return startsAt;} public Instant getEndsAt(){return endsAt;}
 public Long getMaximumUses(){return maximumUses;} public Long getMaximumUsesPerCustomer(){return maximumUsesPerCustomer;}
 public long getCurrentUses(){return currentUses;} public long getReservedUses(){return reservedUses;} public boolean isActive(){return active;}
 public Set<Long> getProductIds(){return Collections.unmodifiableSet(productIds);} public Set<Long> getCategoryIds(){return Collections.unmodifiableSet(categoryIds);}
}
