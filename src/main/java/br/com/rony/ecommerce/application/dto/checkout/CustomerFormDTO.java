package br.com.rony.ecommerce.application.dto.checkout;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CPF;
public class CustomerFormDTO {
 @NotBlank @Size(min=2,max=150) @JsonProperty("nome") private String name;
 @NotBlank @Email @Size(max=254) @JsonProperty("email") private String email;
 @NotBlank @Pattern(regexp="\\+?[0-9]{10,15}") @JsonProperty("telefone") private String phone;
 @NotBlank @Pattern(regexp="[0-9]{11}") @CPF @JsonProperty("cpf") private String cpf;
 public String getName(){return name;} public void setName(String name){this.name=name;}
 public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
 public String getPhone(){return phone;} public void setPhone(String phone){this.phone=phone;}
 public String getCpf(){return cpf;} public void setCpf(String cpf){this.cpf=cpf;}
 @Override public String toString(){return "CustomerFormDTO[REDACTED]";}
}
