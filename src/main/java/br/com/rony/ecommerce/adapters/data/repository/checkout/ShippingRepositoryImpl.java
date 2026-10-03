package br.com.rony.ecommerce.adapters.data.repository.checkout;
import br.com.rony.ecommerce.data.repository.checkout.ShippingRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
@Repository @Transactional(readOnly=true)
public class ShippingRepositoryImpl implements ShippingRepository {
 private final EntityManager em;
 public ShippingRepositoryImpl(EntityManager em){this.em=em;}
 @SuppressWarnings("unchecked")
 public Optional<PostalCode> findPostalCode(String postalCode){
  List<Object[]> rows=em.createNativeQuery("SELECT postal_code, city, state FROM checkout_postal_codes WHERE postal_code = :cep AND active = TRUE").setParameter("cep",postalCode).getResultList();
  return rows.stream().findFirst().map(r->new PostalCode((String)r[0],(String)r[1],(String)r[2]));
 }
 @SuppressWarnings("unchecked")
 public List<ProductMeasurements> findMeasurements(Set<Long> productIds){
  if(productIds.isEmpty())return List.of();
  List<Long> ids=productIds.stream().sorted().toList(); List<String> params=new ArrayList<>();
  for(int i=0;i<ids.size();i++)params.add(":product"+i);
  // Concatena somente nomes de parâmetros gerados, nunca valores do usuário.
  var q=em.createNativeQuery("SELECT product_id, weight_kg, height_cm, width_cm, length_cm FROM product_shipping_profiles WHERE product_id IN ("+String.join(",",params)+")");
  for(int i=0;i<ids.size();i++)q.setParameter("product"+i,ids.get(i));
  List<Object[]> rows=q.getResultList();
  return rows.stream().map(r->new ProductMeasurements(((Number)r[0]).longValue(),decimal(r[1]),decimal(r[2]),decimal(r[3]),decimal(r[4]))).toList();
 }
 @SuppressWarnings("unchecked")
 public List<Tariff> findTariffs(String postalCode){
  List<Object[]> rows=em.createNativeQuery("SELECT id, service_code, service_name, delivery_business_days, base_price, price_per_kg, volumetric_divisor, maximum_weight_kg, maximum_volume_cm3, maximum_item_height_cm, maximum_item_width_cm, maximum_item_length_cm FROM checkout_shipping_tariffs WHERE active = TRUE AND postal_code_start <= :cep AND postal_code_end >= :cep ORDER BY service_code, id").setParameter("cep",postalCode).getResultList();
  return rows.stream().map(r->new Tariff(((Number)r[0]).longValue(),(String)r[1],(String)r[2],((Number)r[3]).intValue(),decimal(r[4]),decimal(r[5]),decimal(r[6]),decimal(r[7]),decimal(r[8]),decimal(r[9]),decimal(r[10]),decimal(r[11]))).toList();
 }
 private BigDecimal decimal(Object value){return value instanceof BigDecimal d?d:new BigDecimal(value.toString());}
}
