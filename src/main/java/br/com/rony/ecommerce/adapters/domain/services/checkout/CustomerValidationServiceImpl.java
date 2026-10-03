package br.com.rony.ecommerce.adapters.domain.services.checkout;
import br.com.rony.ecommerce.application.dto.checkout.*;
import br.com.rony.ecommerce.domain.exceptions.CheckoutException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.Comparator;
@Service
public class CustomerValidationServiceImpl {
 private final Validator validator;
 public CustomerValidationServiceImpl(Validator validator){this.validator=validator;}
 public void validate(CustomerFormDTO customer,AddressFormDTO address){
  if(customer==null||address==null)throw new CheckoutException(400,"DADOS_OBRIGATORIOS","Informe os dados do cliente e o endereço de entrega.");
  customer.setName(normalize(customer.getName(),150,"nome"));customer.setEmail(normalize(customer.getEmail(),254,"email"));
  customer.setPhone(normalize(customer.getPhone(),16,"telefone"));customer.setCpf(normalize(customer.getCpf(),11,"cpf"));
  address.setStreet(normalize(address.getStreet(),150,"rua"));address.setNumber(normalize(address.getNumber(),20,"numero"));
  address.setComplement(normalize(address.getComplement(),100,"complemento"));address.setDistrict(normalize(address.getDistrict(),100,"bairro"));
  address.setCity(normalize(address.getCity(),120,"cidade"));address.setState(normalize(address.getState(),2,"estado"));address.setPostalCode(normalize(address.getPostalCode(),9,"cep"));
  validateBean(customer);validateBean(address);
 }
 private <T>void validateBean(T bean){validator.validate(bean).stream().sorted(Comparator.comparing(v->v.getPropertyPath().toString())).findFirst().ifPresent(v->{throw new CheckoutException(400,"DADOS_INVALIDOS","Um dos campos obrigatórios está ausente ou inválido.",v.getPropertyPath().toString());});}
 private String normalize(String value,int maximum,String field){
  if(value==null)return null;if(value.length()>maximum)throw invalid(field);
  String normalized=Normalizer.normalize(value,Normalizer.Form.NFC).strip();
  boolean forbidden=normalized.codePoints().anyMatch(cp->Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT);
  if(forbidden||normalized.length()>maximum)throw invalid(field);return normalized;
 }
 private CheckoutException invalid(String field){return new CheckoutException(400,"CAMPO_INVALIDO","O campo informado possui formato inválido.",field);}
}
