package br.com.rony.ecommerce.domain.services.checkout;
import java.time.Instant;
public interface PaymentValidationService {
 void validateCard(char[] number, String holderName, int expirationMonth, int expirationYear, char[] cvv);
 void validatePixExpiration(Instant expiresAt);
 void validateInstallments(int installments, boolean debit);
}
