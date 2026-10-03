package br.com.rony.ecommerce.adapters.domain.entities.checkout;
import br.com.rony.ecommerce.adapters.domain.entities.commons.EntityWithLongId;
import br.com.rony.ecommerce.domain.entities.checkout.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="payment_transactions") @Access(AccessType.FIELD)
public class PaymentTransactionImpl extends EntityWithLongId {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private OrderImpl order;
 @Column(name="operation_key",nullable=false,unique=true) private String operationKey;
 @Column(nullable=false) private String gateway;
 @Column(name="gateway_transaction_id") private String gatewayTransactionId;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private PaymentStatus status;
 @Column(nullable=false,precision=15,scale=2) private BigDecimal amount;
 @Column(nullable=false,length=3) private String currency="BRL";
 @Enumerated(EnumType.STRING) @Column(nullable=false) private PaymentMethod method;
 @Column(nullable=false) private int installments;
 @Column(name="card_last_four",length=4) private String cardLastFour;
 @Column(name="card_brand") private String cardBrand;
 @Column(name="pix_expires_at") private Instant pixExpiresAt;
 @Column(name="gateway_result_ciphertext",columnDefinition="MEDIUMTEXT") private String gatewayResultCiphertext;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Version private long version;
 protected PaymentTransactionImpl(){}
 public PaymentTransactionImpl(OrderImpl order,String gateway,int installments,Instant now){
  if(installments<1 || installments>12) throw new IllegalArgumentException("Quantidade de parcelas inválida.");
  this.order=order; this.operationKey=UUID.randomUUID().toString(); this.gateway=gateway; this.status=PaymentStatus.CREATED;
  this.amount=order.getTotalAmount(); this.method=order.getPaymentMethod(); this.installments=installments; this.createdAt=now; this.updatedAt=now;
 }
 public void applyVerifiedGatewayResult(String gatewayTransactionId,PaymentStatus status,String cardLastFour,String cardBrand,Instant pixExpiresAt,String sanitizedResultCiphertext,Instant now){
  if(cardLastFour!=null && !cardLastFour.matches("[0-9]{4}")) throw new IllegalArgumentException("Últimos dígitos inválidos.");
  if(this.gatewayTransactionId!=null && !this.gatewayTransactionId.equals(gatewayTransactionId)) throw new IllegalStateException("Identificador do gateway divergente.");
  this.gatewayTransactionId=gatewayTransactionId; this.status=status; this.cardLastFour=cardLastFour; this.cardBrand=cardBrand;
  this.pixExpiresAt=pixExpiresAt; this.gatewayResultCiphertext=sanitizedResultCiphertext; this.updatedAt=now;
 }
 public OrderImpl getOrder(){return order;} public String getOperationKey(){return operationKey;} public String getGateway(){return gateway;}
 public String getGatewayTransactionId(){return gatewayTransactionId;} public PaymentStatus getStatus(){return status;} public BigDecimal getAmount(){return amount;}
 public PaymentMethod getMethod(){return method;} public int getInstallments(){return installments;} public String getCardLastFour(){return cardLastFour;} public String getCardBrand(){return cardBrand;} public Instant getPixExpiresAt(){return pixExpiresAt;}
}
