package br.com.rony.ecommerce.adapters.domain.entities.checkout;
import br.com.rony.ecommerce.adapters.domain.entities.commons.EntityWithLongId;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="order_items") @Access(AccessType.FIELD)
public class OrderItemImpl extends EntityWithLongId {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private OrderImpl order;
 @Column(name="product_id") private Long productId;
 @Column(name="product_name",nullable=false) private String productName;
 @Column(name="product_photo",length=1024) private String productPhoto;
 @Column(name="unit_price",nullable=false,precision=15,scale=2) private BigDecimal unitPrice;
 @Column(nullable=false) private int quantity;
 @Column(nullable=false,precision=15,scale=2) private BigDecimal subtotal;
 @Column(name="automatic_discount",nullable=false,precision=15,scale=2) private BigDecimal automaticDiscount;
 protected OrderItemImpl(){}
 public OrderItemImpl(Long productId,String productName,String productPhoto,BigDecimal unitPrice,int quantity,BigDecimal automaticDiscount){
  if(quantity<=0 || unitPrice.signum()<0) throw new IllegalArgumentException("Item de pedido inválido.");
  BigDecimal gross=unitPrice.multiply(BigDecimal.valueOf(quantity));
  if(automaticDiscount.signum()<0 || automaticDiscount.compareTo(gross)>0) throw new IllegalArgumentException("Desconto do item inválido.");
  this.productId=productId; this.productName=productName; this.productPhoto=productPhoto; this.unitPrice=unitPrice; this.quantity=quantity; this.subtotal=gross; this.automaticDiscount=automaticDiscount;
 }
 void attachTo(OrderImpl order){if(this.order!=null && this.order!=order) throw new IllegalStateException("Item já pertence a outro pedido."); this.order=order;}
 public Long getProductId(){return productId;} public String getProductName(){return productName;} public String getProductPhoto(){return productPhoto;}
 public BigDecimal getUnitPrice(){return unitPrice;} public int getQuantity(){return quantity;} public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getAutomaticDiscount(){return automaticDiscount;}
}
