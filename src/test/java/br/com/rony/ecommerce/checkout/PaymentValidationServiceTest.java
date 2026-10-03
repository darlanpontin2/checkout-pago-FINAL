package br.com.rony.ecommerce.checkout;

import br.com.rony.ecommerce.adapters.domain.services.checkout.PaymentValidationServiceImpl;
import br.com.rony.ecommerce.domain.exceptions.CheckoutException;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class PaymentValidationServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
    private final PaymentValidationServiceImpl service = new PaymentValidationServiceImpl(clock);

    @Test
    void acceptsValidCardAndClearsSensitiveArrays() {
        char[] number = "4111111111111111".toCharArray();
        char[] cvv = "123".toCharArray();
        assertDoesNotThrow(() -> service.validateCard(number, "CLIENTE TESTE", 10, 2026, cvv));
        assertArrayEquals(new char[number.length], number);
        assertArrayEquals(new char[cvv.length], cvv);
    }

    @Test
    void rejectsInvalidLuhnAndStillClearsArrays() {
        char[] number = "4111111111111112".toCharArray();
        char[] cvv = "123".toCharArray();
        CheckoutException error = assertThrows(CheckoutException.class,
            () -> service.validateCard(number, "CLIENTE TESTE", 12, 2027, cvv));
        assertEquals("CARTAO_INVALIDO", error.getCode());
        assertArrayEquals(new char[number.length], number);
        assertArrayEquals(new char[cvv.length], cvv);
    }

    @Test
    void rejectsExpiredCard() {
        CheckoutException error = assertThrows(CheckoutException.class,
            () -> service.validateCard("4111111111111111".toCharArray(),
                "CLIENTE TESTE", 9, 2026, "123".toCharArray()));
        assertEquals("CARTAO_EXPIRADO", error.getCode());
    }

    @Test
    void rejectsCvvFormatWithoutClaimingBankVerification() {
        CheckoutException error = assertThrows(CheckoutException.class,
            () -> service.validateCard("4111111111111111".toCharArray(),
                "CLIENTE TESTE", 12, 2027, "12a".toCharArray()));
        assertEquals("CVV_FORMATO_INVALIDO", error.getCode());
    }

    @Test
    void pixIsExpiredAtExactExpirationInstant() {
        CheckoutException error = assertThrows(CheckoutException.class,
            () -> service.validatePixExpiration(clock.instant()));
        assertEquals("PIX_EXPIRADO", error.getCode());
    }

    @Test
    void debitCannotBePaidInMultipleInstallments() {
        assertThrows(CheckoutException.class, () -> service.validateInstallments(2, true));
    }
}
