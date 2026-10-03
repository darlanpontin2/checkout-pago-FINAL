package br.com.rony.ecommerce.domain.entities.checkout;
import java.math.BigDecimal;
public interface Order {
 Long getId(); String getPublicId(); Long getCustomerId(); Long getCartId();
 PaymentStatus getPaymentStatus(); PaymentMethod getPaymentMethod(); BigDecimal getTotalAmount();
}
