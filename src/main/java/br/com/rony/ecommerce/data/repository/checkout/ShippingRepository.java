package br.com.rony.ecommerce.data.repository.checkout;
import java.math.BigDecimal;
import java.util.*;
public interface ShippingRepository {
 Optional<PostalCode> findPostalCode(String postalCode);
 List<ProductMeasurements> findMeasurements(Set<Long> productIds);
 List<Tariff> findTariffs(String postalCode);
 record PostalCode(String code,String city,String state){}
 record ProductMeasurements(Long productId,BigDecimal weightKg,BigDecimal heightCm,BigDecimal widthCm,BigDecimal lengthCm){}
 record Tariff(Long id,String serviceCode,String serviceName,int deliveryBusinessDays,BigDecimal basePrice,BigDecimal pricePerKg,BigDecimal volumetricDivisor,BigDecimal maximumWeightKg,BigDecimal maximumVolumeCm3,BigDecimal maximumItemHeightCm,BigDecimal maximumItemWidthCm,BigDecimal maximumItemLengthCm){}
}
