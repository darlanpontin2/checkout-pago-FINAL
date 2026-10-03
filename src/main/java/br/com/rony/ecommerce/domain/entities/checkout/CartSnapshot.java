package br.com.rony.ecommerce.domain.entities.checkout;
import br.com.rony.ecommerce.domain.services.checkout.Money;
import java.math.BigDecimal;
import java.util.*;
/** Snapshot produzido pelo servidor, nunca aceito diretamente como entrada HTTP. */
public record CartSnapshot(Long cartId, Long customerId, long revision, List<Item> items) {
 public CartSnapshot {
  if (cartId == null || customerId == null || revision < 0) throw new IllegalArgumentException("Carrinho inválido.");
  items = List.copyOf(items);
  if (items.isEmpty() || items.size() > 500) throw new IllegalArgumentException("Quantidade de itens inválida.");
  var ids = new HashSet<Long>();
  for (Item item : items) if (!ids.add(item.productId())) throw new IllegalArgumentException("Produto duplicado no snapshot.");
 }
 public BigDecimal subtotal() { return items.stream().map(Item::subtotal).reduce(Money.ZERO, BigDecimal::add); }
 public BigDecimal automaticDiscount() { return items.stream().map(Item::automaticDiscount).reduce(Money.ZERO, BigDecimal::add); }
 public BigDecimal totalBeforeCouponAndShipping() { return subtotal().subtract(automaticDiscount()); }
 public record Item(Long productId, Long categoryId, String name, String photo, BigDecimal unitPrice, int quantity, BigDecimal automaticDiscount) {
  public Item {
   if (productId == null || categoryId == null || name == null || name.isBlank() || quantity < 1 || quantity > 9999) throw new IllegalArgumentException("Item de carrinho inválido.");
   unitPrice = Money.exact(unitPrice); automaticDiscount = Money.exact(automaticDiscount);
   if (automaticDiscount.compareTo(unitPrice.multiply(BigDecimal.valueOf(quantity))) > 0) throw new IllegalArgumentException("Desconto maior que o item.");
  }
  public BigDecimal subtotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
  public BigDecimal netAmount() { return subtotal().subtract(automaticDiscount); }
 }
}
