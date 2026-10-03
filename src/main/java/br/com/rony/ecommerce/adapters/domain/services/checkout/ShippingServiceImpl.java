package br.com.rony.ecommerce.adapters.domain.services.checkout;
import br.com.rony.ecommerce.data.repository.checkout.ShippingRepository;
import br.com.rony.ecommerce.data.repository.checkout.ShippingRepository.*;
import br.com.rony.ecommerce.domain.entities.checkout.CartSnapshot;
import br.com.rony.ecommerce.domain.exceptions.CheckoutException;
import br.com.rony.ecommerce.domain.services.checkout.ShippingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
@Service
public class ShippingServiceImpl implements ShippingService {
 private final ShippingRepository repository;private final Clock clock;private final ZoneId shippingZone;private final Set<LocalDate> holidays;
 public ShippingServiceImpl(ShippingRepository repository,Clock clock,@Value("${checkout.shipping.zone:America/Sao_Paulo}")String zone,@Value("${checkout.shipping.holidays:}")String holidays){
  this.repository=repository;this.clock=clock;this.shippingZone=ZoneId.of(zone);
  this.holidays=Arrays.stream(holidays.split(",")).map(String::strip).filter(s->!s.isEmpty()).map(LocalDate::parse).collect(Collectors.toUnmodifiableSet());
 }
 public List<Option> calculate(CartSnapshot cart,String postalCode,Sort sort){
  String cep=normalizePostalCode(postalCode);
  repository.findPostalCode(cep).orElseThrow(()->new CheckoutException(400,"CEP_NAO_ATENDIDO","O CEP não foi encontrado na base de atendimento.","cep_entrega"));
  Set<Long> ids=cart.items().stream().map(CartSnapshot.Item::productId).collect(Collectors.toSet());
  Map<Long,ProductMeasurements> measurements=repository.findMeasurements(ids).stream().collect(Collectors.toMap(ProductMeasurements::productId,Function.identity()));
  if(!measurements.keySet().containsAll(ids))throw new CheckoutException(409,"DADOS_LOGISTICOS_INDISPONIVEIS","Um ou mais produtos não possuem dados logísticos cadastrados.");
  measurements.values().forEach(this::validateMeasurements);
  BigDecimal weight=BigDecimal.ZERO,volume=BigDecimal.ZERO;
  for(CartSnapshot.Item item:cart.items()){
   ProductMeasurements p=measurements.get(item.productId());BigDecimal quantity=BigDecimal.valueOf(item.quantity());
   weight=weight.add(p.weightKg().multiply(quantity));volume=volume.add(p.heightCm().multiply(p.widthCm()).multiply(p.lengthCm()).multiply(quantity));
  }
  LocalDate today=LocalDate.now(clock.withZone(shippingZone));List<Option> options=new ArrayList<>();
  for(Tariff tariff:repository.findTariffs(cep)){
   validateTariff(tariff);if(!fits(tariff,measurements.values(),weight,volume))continue;
   BigDecimal volumetric=volume.divide(tariff.volumetricDivisor(),6,RoundingMode.UP);
   BigDecimal billed=weight.max(volumetric).setScale(3,RoundingMode.UP);
   if(billed.compareTo(tariff.maximumWeightKg())>0)continue;
   BigDecimal amount=tariff.basePrice().add(tariff.pricePerKg().multiply(billed)).setScale(2,RoundingMode.HALF_UP);
   options.add(new Option(tariff.id(),tariff.serviceCode(),tariff.serviceName(),tariff.deliveryBusinessDays(),amount,addBusinessDays(today,tariff.deliveryBusinessDays())));
  }
  if(options.isEmpty())throw new CheckoutException(422,"FRETE_INDISPONIVEL","Não há modalidade de entrega disponível para este carrinho e CEP.");
  Comparator<Option> comparator=sort==Sort.DELIVERY_TIME?Comparator.comparingInt(Option::deliveryBusinessDays).thenComparing(Option::shippingAmount):Comparator.comparing(Option::shippingAmount).thenComparingInt(Option::deliveryBusinessDays);
  options.sort(comparator.thenComparing(Option::tariffId));return List.copyOf(options);
 }
 public String normalizePostalCode(String cep){
  if(cep==null||!cep.matches("[0-9]{5}-?[0-9]{3}"))throw invalidCep();String normalized=cep.replace("-","");
  if(normalized.chars().distinct().count()==1)throw invalidCep();return normalized;
 }
 // Política interna por item + volume total; não é algoritmo de empacotamento.
 private boolean fits(Tariff t,Collection<ProductMeasurements> measurements,BigDecimal weight,BigDecimal volume){
  if(weight.compareTo(t.maximumWeightKg())>0||volume.compareTo(t.maximumVolumeCm3())>0)return false;
  return measurements.stream().allMatch(p->p.heightCm().compareTo(t.maximumItemHeightCm())<=0&&p.widthCm().compareTo(t.maximumItemWidthCm())<=0&&p.lengthCm().compareTo(t.maximumItemLengthCm())<=0);
 }
 private LocalDate addBusinessDays(LocalDate start,int days){LocalDate result=start;while(days>0){result=result.plusDays(1);if(result.getDayOfWeek()!=DayOfWeek.SATURDAY&&result.getDayOfWeek()!=DayOfWeek.SUNDAY&&!holidays.contains(result))days--;}return result;}
 private void validateMeasurements(ProductMeasurements p){if(!positive(p.weightKg())||!positive(p.heightCm())||!positive(p.widthCm())||!positive(p.lengthCm()))throw new CheckoutException(409,"DADOS_LOGISTICOS_INVALIDOS","Um produto possui medidas logísticas inválidas.");}
 private void validateTariff(Tariff t){if(t.deliveryBusinessDays()<0||!nonNegative(t.basePrice())||!nonNegative(t.pricePerKg())||!positive(t.volumetricDivisor())||!positive(t.maximumWeightKg())||!positive(t.maximumVolumeCm3())||!positive(t.maximumItemHeightCm())||!positive(t.maximumItemWidthCm())||!positive(t.maximumItemLengthCm()))throw new CheckoutException(503,"TARIFA_FRETE_INVALIDA","O cálculo de frete está temporariamente indisponível.");}
 private boolean positive(BigDecimal v){return v!=null&&v.signum()>0;}private boolean nonNegative(BigDecimal v){return v!=null&&v.signum()>=0;}
 private CheckoutException invalidCep(){return new CheckoutException(400,"CEP_INVALIDO","Informe um CEP válido com oito dígitos.","cep_entrega");}
}
