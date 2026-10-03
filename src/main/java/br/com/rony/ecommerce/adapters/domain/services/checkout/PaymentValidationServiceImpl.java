package br.com.rony.ecommerce.adapters.domain.services.checkout;
import br.com.rony.ecommerce.domain.exceptions.CheckoutException;
import br.com.rony.ecommerce.domain.services.checkout.PaymentValidationService;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.Arrays;
@Service
public class PaymentValidationServiceImpl implements PaymentValidationService {
 private final Clock clock;
 public PaymentValidationServiceImpl(Clock clock){this.clock=clock;}
 /** Consome e limpa os arrays; não elimina cópias feitas por frameworks/JVM. Não exposto no endpoint tokenizado. */
 public void validateCard(char[] number,String holderName,int expirationMonth,int expirationYear,char[] cvv){
  try{validateNumber(number);validateHolder(holderName);validateExpiration(expirationMonth,expirationYear);validateCvvFormat(cvv);}
  finally{if(number!=null)Arrays.fill(number,'\0');if(cvv!=null)Arrays.fill(cvv,'\0');}
 }
 private void validateNumber(char[] number){
  if(number==null||number.length<12||number.length>19)throw invalidCard();
  int sum=0;boolean twice=false,allEqual=true;
  for(int i=number.length-1;i>=0;i--){char c=number[i];if(c<'0'||c>'9')throw invalidCard();if(c!=number[0])allEqual=false;
   int d=c-'0';if(twice){d*=2;if(d>9)d-=9;}sum+=d;twice=!twice;
  }
  if(allEqual||sum%10!=0)throw invalidCard();
 }
 private void validateHolder(String name){
  if(name==null||name.length()>100||name.strip().length()<2||!name.codePoints().anyMatch(Character::isLetter)||name.codePoints().anyMatch(cp->Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT))
   throw new CheckoutException(400,"TITULAR_INVALIDO","Informe um nome de titular válido.","nome_titular");
 }
 private void validateExpiration(int month,int year){
  if(month<1||month>12||year<2000||year>9999)throw new CheckoutException(400,"VALIDADE_INVALIDA","A validade do cartão é inválida.","data_expiracao");
  if(YearMonth.of(year,month).isBefore(YearMonth.now(clock)))throw new CheckoutException(400,"CARTAO_EXPIRADO","O cartão está expirado.","data_expiracao");
 }
 private void validateCvvFormat(char[] cvv){if(cvv==null||cvv.length<3||cvv.length>4)throw invalidCvv();for(char c:cvv)if(c<'0'||c>'9')throw invalidCvv();}
 public void validatePixExpiration(Instant expiresAt){if(expiresAt==null||!clock.instant().isBefore(expiresAt))throw new CheckoutException(409,"PIX_EXPIRADO","O prazo para pagamento deste Pix expirou.");}
 public void validateInstallments(int installments,boolean debit){if(installments<1||installments>12||(debit&&installments!=1))throw new CheckoutException(400,"PARCELAMENTO_INVALIDO","O parcelamento informado não é permitido.","parcelas");}
 private CheckoutException invalidCard(){return new CheckoutException(400,"CARTAO_INVALIDO","O número do cartão é inválido.","numero_cartao");}
 private CheckoutException invalidCvv(){return new CheckoutException(400,"CVV_FORMATO_INVALIDO","O código de segurança deve conter três ou quatro dígitos.","cvv");}
}
