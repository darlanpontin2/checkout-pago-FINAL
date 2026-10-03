package br.com.rony.ecommerce.checkout;

import br.com.rony.ecommerce.adapters.domain.entities.checkout.CouponImpl;
import br.com.rony.ecommerce.adapters.domain.services.checkout.CouponServiceImpl;
import br.com.rony.ecommerce.data.repository.checkout.CheckoutRepository;
import br.com.rony.ecommerce.domain.entities.checkout.CartSnapshot;
import br.com.rony.ecommerce.domain.entities.checkout.CouponType;
import br.com.rony.ecommerce.domain.exceptions.CheckoutException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CouponServiceTest {
    private final Instant now = Instant.parse("2026-10-03T12:00:00Z");
    private final CouponServiceImpl service = new CouponServiceImpl(
        mock(CheckoutRepository.class), Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void percentageUsesAmountAfterAutomaticDiscount() {
        CouponImpl coupon = coupon(CouponType.PERCENTAGE, "10.00", Set.of(), Set.of());
        var result = service.evaluate(coupon, cart(), 0, now);
        assertEquals(new BigDecimal("240.00"), result.eligibleAmount());
        assertEquals(new BigDecimal("24.00"), result.discount());
        assertEquals(new BigDecimal("216.00"), result.totalBeforeShipping());
    }

    @Test
    void discountOnlyAppliesToEligibleProducts() {
        CouponImpl coupon = coupon(CouponType.PERCENTAGE, "10.00", Set.of(2L), Set.of());
        var result = service.evaluate(coupon, cart(), 0, now);
        assertEquals(new BigDecimal("50.00"), result.eligibleAmount());
        assertEquals(new BigDecimal("5.00"), result.discount());
        assertEquals(new BigDecimal("235.00"), result.totalBeforeShipping());
    }

    @Test
    void fixedDiscountCannotExceedEligibleAmount() {
        CouponImpl coupon = coupon(CouponType.FIXED, "500.00", Set.of(2L), Set.of());
        var result = service.evaluate(coupon, cart(), 0, now);
        assertEquals(new BigDecimal("50.00"), result.discount());
        assertEquals(new BigDecimal("190.00"), result.totalBeforeShipping());
    }

    @Test
    void customerUsageLimitIncludesExistingReservations() {
        CouponImpl coupon = coupon(CouponType.PERCENTAGE, "10.00", Set.of(), Set.of());
        CheckoutException error = assertThrows(CheckoutException.class,
            () -> service.evaluate(coupon, cart(), 1, now));
        assertEquals("CUPOM_LIMITE_CLIENTE", error.getCode());
    }

    @Test
    void couponExpiresAtExactEndInstant() {
        CouponImpl coupon = coupon(CouponType.PERCENTAGE, "10.00", Set.of(), Set.of());
        CheckoutException error = assertThrows(CheckoutException.class,
            () -> service.evaluate(coupon, cart(), 0, coupon.getEndsAt()));
        assertEquals("CUPOM_EXPIRADO", error.getCode());
    }

    private CouponImpl coupon(CouponType type, String value, Set<Long> products, Set<Long> categories) {
        CouponImpl coupon = new CouponImpl("DESCONTO10", type, new BigDecimal(value),
            new BigDecimal("100.00"), null, now.minusSeconds(3600), now.plusSeconds(3600),
            100L, 1L, products, categories);
        coupon.setId(1L);
        return coupon;
    }

    private CartSnapshot cart() {
        return new CartSnapshot(10L, 20L, 1L, List.of(
            new CartSnapshot.Item(1L, 1L, "Produto A", "/imagens/produto-a.jpg",
                new BigDecimal("100.00"), 2, new BigDecimal("10.00")),
            new CartSnapshot.Item(2L, 2L, "Produto B", "/imagens/produto-b.jpg",
                new BigDecimal("50.00"), 1, new BigDecimal("0.00"))));
    }
}
