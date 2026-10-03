package br.com.rony.ecommerce.adapters.domain.entities.checkout;
import br.com.rony.ecommerce.adapters.domain.entities.commons.EntityWithLongId;
import br.com.rony.ecommerce.domain.entities.checkout.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="orders") @Access(AccessType.FIELD)
public class OrderImpl extends EntityWithLongId implements Order {
 @Column(name="public_id",nullable=false,updatable=false) private String publicId;
 @Column(name="customer_id",nullable=false,updatable=false) private Long customerId;
 @Column(name="cart_id",nullable=false,updatable=false) private Long cartId;
 @Enumerated(EnumType.STRING) @Column(name="payment_status",nullable=false) private PaymentStatus paymentStatus;
 @Enumerated(EnumType.STRING) @Column(name="payment_method",nullable=false) private PaymentMethod paymentMethod;
 @Column(nullable=false,length=3) private String currency="BRL";
 @Column(nullable=false,precision=15,scale=2) private BigDecimal subtotal;
 @Column(name="automatic_discount",nullable=false,precision=15,scale=2) private BigDecimal automaticDiscount;
 @Column(name="coupon_discount",nullable=false,precision=15,scale=2) private BigDecimal couponDiscount;
 @Column(name="shipping_amount",nullable=false,precision=15,scale=2) private BigDecimal shippingAmount;
 @Column(name="total_amount",nullable=false,precision=15,scale=2) private BigDecimal totalAmount;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="coupon_id") private CouponImpl coupon;
 @Column(name="coupon_code") private String couponCode;
 @Column(name="shipping_service",nullable=false) private String shippingService;
 @Column(name="delivery_business_days",nullable=false) private int deliveryBusinessDays;
 @Column(name="customer_data_ciphertext",columnDefinition="MEDIUMTEXT") private String customerDataCiphertext;
 @Column(name="delivery_address_ciphertext",columnDefinition="MEDIUMTEXT") private String deliveryAddressCiphertext;
 @Column(name="pii_retain_until",nullable=false) private Instant piiRetainUntil;
 @Column(name="legal_hold",nullable=false) private boolean legalHold;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Version private long version;
 @OneToMany(mappedBy="order",cascade=CascadeType.PERSIST,orphanRemoval=false) private List<OrderItemImpl> items=new ArrayList<>();
 protected OrderImpl() {}
 public OrderImpl(Long customerId, Long cartId, PaymentMethod paymentMethod, BigDecimal subtotal, BigDecimal automaticDiscount, BigDecimal couponDiscount, BigDecimal shippingAmount, CouponImpl coupon, String shippingService, int deliveryBusinessDays, String customerDataCiphertext, String deliveryAddressCiphertext, Instant piiRetainUntil, Instant now) {
  this.publicId=UUID.randomUUID().toString(); this.customerId=customerId; this.cartId=cartId; this.paymentMethod=paymentMethod; this.paymentStatus=PaymentStatus.CREATED;
  this.subtotal=subtotal; this.automaticDiscount=automaticDiscount; this.couponDiscount=couponDiscount; this.shippingAmount=shippingAmount;
  this.totalAmount=subtotal.subtract(automaticDiscount).subtract(couponDiscount).add(shippingAmount); this.coupon=coupon; this.couponCode=coupon==null?null:coupon.getCode();
  this.shippingService=shippingService; this.deliveryBusinessDays=deliveryBusinessDays; this.customerDataCiphertext=customerDataCiphertext; this.deliveryAddressCiphertext=deliveryAddressCiphertext;
  this.piiRetainUntil=piiRetainUntil; this.createdAt=now; this.updatedAt=now;
 }
 public void addItem(OrderItemImpl item) { if(getId()!=null) throw new IllegalStateException("Pedido persistido não aceita novos itens."); item.attachTo(this); items.add(item); }
 /** Somente após validação pelo serviço de estado; nunca a partir de entrada do cliente. */
 public void changePaymentStatus(PaymentStatus status, Instant now){paymentStatus=status; updatedAt=now;}
 public void erasePersonalData(Instant now){if(legalHold || now.isBefore(piiRetainUntil)) throw new IllegalStateException("Dados ainda sujeitos à retenção."); customerDataCiphertext=null; deliveryAddressCiphertext=null; updatedAt=now;}
 public String getPublicId(){return publicId;} public Long getCustomerId(){return customerId;} public Long getCartId(){return cartId;}
 public PaymentStatus getPaymentStatus(){return paymentStatus;} public PaymentMethod getPaymentMethod(){return paymentMethod;} public BigDecimal getTotalAmount(){return totalAmount;}
 public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getAutomaticDiscount(){return automaticDiscount;} public BigDecimal getCouponDiscount(){return couponDiscount;} public BigDecimal getShippingAmount(){return shippingAmount;}
 public String getCouponCode(){return couponCode;} public String getShippingService(){return shippingService;} public int getDeliveryBusinessDays(){return deliveryBusinessDays;}
 public List<OrderItemImpl> getItems(){return Collections.unmodifiableList(items);} public Instant getCreatedAt(){return createdAt;}
 public String getCustomerDataCiphertext(){return customerDataCiphertext;} public String getDeliveryAddressCiphertext(){return deliveryAddressCiphertext;}
}
