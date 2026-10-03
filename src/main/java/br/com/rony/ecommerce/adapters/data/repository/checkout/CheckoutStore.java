package br.com.rony.ecommerce.adapters.data.repository.checkout;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
@Repository
public class CheckoutStore {
 private final EntityManager em;
 private final TransactionTemplate required;
 private final TransactionTemplate independent;
 public CheckoutStore(EntityManager em,PlatformTransactionManager manager){
  this.em=em;required=new TransactionTemplate(manager);independent=new TransactionTemplate(manager);
  independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
 }
 public <T>T tx(Supplier<T> operation){return required.execute(s->operation.get());}
 public <T>T independent(Supplier<T> operation){return independent.execute(s->operation.get());}
 public List<Row> rows(String sql,Object... parameters){
  Query query=em.createNativeQuery(sql,Tuple.class);bind(query,parameters);
  @SuppressWarnings("unchecked") List<Tuple> tuples=query.getResultList();
  return tuples.stream().map(tuple->{Map<String,Object> values=new LinkedHashMap<>();tuple.getElements().forEach(e->values.put(e.getAlias().toLowerCase(Locale.ROOT),tuple.get(e)));return new Row(values);}).toList();
 }
 public Optional<Row> optional(String sql,Object... parameters){List<Row> rows=rows(sql,parameters);if(rows.size()>1)throw new IllegalStateException("Consulta retornou múltiplos registros.");return rows.stream().findFirst();}
 public Row one(String sql,Object... parameters){return optional(sql,parameters).orElseThrow(()->new IllegalStateException("Registro esperado ausente."));}
 public int update(String sql,Object... parameters){Query query=em.createNativeQuery(sql);bind(query,parameters);return query.executeUpdate();}
 public long insert(String sql,Object... parameters){update(sql,parameters);return ((Number)em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();}
 private void bind(Query query,Object[] parameters){
  if(parameters.length%2!=0)throw new IllegalArgumentException("Parâmetros devem formar pares.");
  for(int i=0;i<parameters.length;i+=2){Object value=parameters[i+1];if(value instanceof Instant instant)value=Timestamp.from(instant);query.setParameter(parameters[i].toString(),value);}
 }
 public record Row(Map<String,Object> values){
  public String text(String key){Object value=values.get(key);return value==null?null:value.toString();}
  public long number(String key){return ((Number)values.get(key)).longValue();}
  public Long nullableNumber(String key){return values.get(key)==null?null:number(key);}
  public BigDecimal money(String key){Object v=values.get(key);return v instanceof BigDecimal d?d:new BigDecimal(v.toString());}
  public boolean bool(String key){Object v=values.get(key);return v instanceof Boolean b?b:((Number)v).intValue()!=0;}
  public Instant instant(String key){Object v=values.get(key);if(v==null)return null;if(v instanceof Timestamp t)return t.toInstant();if(v instanceof LocalDateTime t)return t.toInstant(ZoneOffset.UTC);return (Instant)v;}
 }
}
