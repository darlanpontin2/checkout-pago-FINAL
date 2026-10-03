package br.com.rony.ecommerce.adapters.domain.entities.checkout;
import br.com.rony.ecommerce.adapters.domain.entities.commons.EntityWithLongId;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="webhook_logs") @Access(AccessType.FIELD)
public class WebhookLogImpl extends EntityWithLongId {
 public enum Status { RECEIVED, PROCESSING, RETRY, PROCESSED, DEADLETTER }
 @Column(nullable=false) private String gateway;
 @Column(name="event_key",nullable=false) private String eventKey;
 @Column(name="gateway_resource_id",nullable=false) private String gatewayResourceId;
 @Column(name="payload_sha256",nullable=false) private String payloadSha256;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status;
 @Column(nullable=false) private int attempts;
 @Column(name="next_attempt_at",nullable=false) private Instant nextAttemptAt;
 @Column(name="locked_until") private Instant lockedUntil;
 @Column(name="lock_token") private String lockToken;
 @Column(name="last_error_code") private String lastErrorCode;
 @Column(name="received_at",nullable=false) private Instant receivedAt;
 @Column(name="processed_at") private Instant processedAt;
 @Version private long version;
 protected WebhookLogImpl(){}
 public WebhookLogImpl(String gateway,String eventKey,String gatewayResourceId,String payloadSha256,Instant now){
  this.gateway=gateway; this.eventKey=eventKey; this.gatewayResourceId=gatewayResourceId; this.payloadSha256=payloadSha256;
  this.status=Status.RECEIVED; this.nextAttemptAt=now; this.receivedAt=now;
 }
 /** Chamar em transação com lock pessimista. */
 public String claim(Instant now,Duration lease){
  if(lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("Lease inválido.");
  if(status==Status.PROCESSED || status==Status.DEADLETTER) throw new IllegalStateException("Webhook encerrado.");
  if(now.isBefore(nextAttemptAt)) throw new IllegalStateException("Tentativa ainda não disponível.");
  if(lockedUntil!=null && now.isBefore(lockedUntil)) throw new IllegalStateException("Webhook já está em processamento.");
  status=Status.PROCESSING; attempts++; lockToken=UUID.randomUUID().toString(); lockedUntil=now.plus(lease); return lockToken;
 }
 public void complete(String token,Instant now){requireLease(token,now); status=Status.PROCESSED; processedAt=now; lockedUntil=null; lockToken=null; lastErrorCode=null;}
 public void fail(String token,String safeErrorCode,int maximumAttempts,Instant now){
  requireLease(token,now);
  if(maximumAttempts<=0 || safeErrorCode==null || !safeErrorCode.matches("[A-Z0-9_]{1,80}")) throw new IllegalArgumentException("Parâmetros de retry inválidos.");
  lastErrorCode=safeErrorCode; lockedUntil=null; lockToken=null;
  if(attempts>=maximumAttempts){status=Status.DEADLETTER;return;}
  status=Status.RETRY; nextAttemptAt=now.plusSeconds(Math.min(3600L,5L*(1L<<Math.min(attempts-1,10))));
 }
 private void requireLease(String token,Instant now){if(status!=Status.PROCESSING || lockToken==null || !lockToken.equals(token) || lockedUntil==null || !now.isBefore(lockedUntil)) throw new IllegalStateException("Lease de processamento inválido.");}
 public String getGateway(){return gateway;} public String getGatewayResourceId(){return gatewayResourceId;} public String getEventKey(){return eventKey;} public Status getStatus(){return status;} public int getAttempts(){return attempts;}
}
