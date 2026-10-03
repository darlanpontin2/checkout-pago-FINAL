package br.com.rony.ecommerce.domain.services.checkout;
import java.math.*;
public final class Money {
 public static final BigDecimal ZERO = new BigDecimal("0.00");
 private Money() {}
 public static BigDecimal exact(BigDecimal value) {
  if (value == null || value.signum() < 0) throw new IllegalArgumentException("Valor monetário inválido.");
  return value.setScale(2, RoundingMode.UNNECESSARY);
 }
 public static BigDecimal percentage(BigDecimal base, BigDecimal percentage) {
  if (percentage == null || percentage.signum() < 0 || percentage.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("Percentual inválido.");
  return exact(base).multiply(percentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
 }
}
