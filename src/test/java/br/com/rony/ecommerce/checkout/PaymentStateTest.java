package br.com.rony.ecommerce.checkout;

import br.com.rony.ecommerce.adapters.domain.services.checkout.PaymentResultService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentStateTest {
    @Test
    void approvedPaymentCannotRegressToPending() {
        assertFalse(PaymentResultService.allowed("APPROVED", "PENDING"));
    }

    @Test
    void approvedPaymentCanBeRefunded() {
        assertTrue(PaymentResultService.allowed("APPROVED", "REFUNDED"));
    }

    @Test
    void refundedPaymentCannotBecomeApprovedAgainAutomatically() {
        assertFalse(PaymentResultService.allowed("REFUNDED", "APPROVED"));
    }

    @Test
    void partialRefundHasOwnStatus() throws Exception {
        var payment = new ObjectMapper().readTree("""
            {"status": "approved", "transaction_amount_refunded": 10.00}
            """);
        assertEquals("PARTIALLY_REFUNDED", PaymentResultService.status(payment));
    }
}
