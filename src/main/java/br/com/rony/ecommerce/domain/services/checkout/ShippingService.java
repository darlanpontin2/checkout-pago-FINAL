package br.com.rony.ecommerce.domain.services.checkout;
import br.com.rony.ecommerce.domain.entities.checkout.CartSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public interface ShippingService {
 enum Sort { PRICE, DELIVERY_TIME }
 List<Option> calculate(CartSnapshot cart, String postalCode, Sort sort);
 record Option(Long tariffId, String serviceCode, String serviceName, int deliveryBusinessDays, BigDecimal shippingAmount, LocalDate estimatedDeliveryDate) {}
}
