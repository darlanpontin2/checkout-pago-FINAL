package br.com.rony.ecommerce.adapters.domain.entities.checkout;
import br.com.rony.ecommerce.adapters.domain.entities.commons.EntityWithLongId;
import br.com.rony.ecommerce.domain.entities.checkout.PaymentStatus;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="payment_events") @Access(AccessType.FIELD)
public class PaymentEventImpl extends EntityWithLongId {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private OrderImpl order;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="transaction_id") private PaymentTransactionImpl transaction;
 @Column(name="event_key",nullable=false,unique=true) private String eventKey;
 @Column(name="event_type",nullable=false) private String eventType;
 @Enumerated(EnumType.STRING) @Column(name="previous_status") private PaymentStatus previousStatus;
 @Enumerated(EnumType.STRING) @Column(name="new_status",nullable=false) private PaymentStatus newStatus;
 @Column(nullable=false) private String source;
 @Column(name="occurred_at",nullable=false) private Instant occurredAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected PaymentEventImpl(){}
 public PaymentEventImpl(OrderImpl order,PaymentTransactionImpl transaction,String eventKey,String eventType,PaymentStatus previousStatus,PaymentStatus newStatus,String source,Instant occurredAt,Instant createdAt){
  this.order=order; this.transaction=transaction; this.eventKey=eventKey; this.eventType=eventType; this.previousStatus=previousStatus;
  this.newStatus=newStatus; this.source=source; this.occurredAt=occurredAt; this.createdAt=createdAt;
 }
 public String getEventKey(){return eventKey;} public PaymentStatus getNewStatus(){return newStatus;} public Instant getOccurredAt(){return occurredAt;}
}
