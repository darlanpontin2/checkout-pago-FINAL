package br.com.rony.ecommerce.application.dto.checkout;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
public class AddressFormDTO {
 @NotBlank @Size(max=150) @JsonProperty("rua") private String street;
 @NotBlank @Size(max=20) @JsonProperty("numero") private String number;
 @Size(max=100) @JsonProperty("complemento") private String complement;
 @NotBlank @Size(max=100) @JsonProperty("bairro") private String district;
 @NotBlank @Size(max=120) @JsonProperty("cidade") private String city;
 @NotBlank @Pattern(regexp="AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO") @JsonProperty("estado") private String state;
 @NotBlank @Pattern(regexp="[0-9]{5}-?[0-9]{3}") @JsonProperty("cep") private String postalCode;
 public String getStreet(){return street;} public void setStreet(String street){this.street=street;}
 public String getNumber(){return number;} public void setNumber(String number){this.number=number;}
 public String getComplement(){return complement;} public void setComplement(String complement){this.complement=complement;}
 public String getDistrict(){return district;} public void setDistrict(String district){this.district=district;}
 public String getCity(){return city;} public void setCity(String city){this.city=city;}
 public String getState(){return state;} public void setState(String state){this.state=state;}
 public String getPostalCode(){return postalCode;} public void setPostalCode(String postalCode){this.postalCode=postalCode;}
 @Override public String toString(){return "AddressFormDTO[REDACTED]";}
}
