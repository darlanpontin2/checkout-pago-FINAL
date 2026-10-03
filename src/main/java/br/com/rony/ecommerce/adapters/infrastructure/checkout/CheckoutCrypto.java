package br.com.rony.ecommerce.adapters.infrastructure.checkout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Component
public class CheckoutCrypto {
 private final ObjectMapper mapper;
 private final Map<String,byte[]> keys;
 private final String activeKey;
 private final byte[] hmacKey;
 private final SecureRandom random=new SecureRandom();
 public CheckoutCrypto(ObjectMapper source,@Value("${checkout.crypto.keys}") String configuredKeys,@Value("${checkout.crypto.active-key}") String activeKey,@Value("${checkout.crypto.hmac-key}") String configuredHmac){
  mapper=source.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  try{
   Map<String,String> encoded=mapper.readValue(configuredKeys,new TypeReference<Map<String,String>>(){});
   Map<String,byte[]> decoded=new HashMap<>();
   encoded.forEach((id,value)->{
    if(!id.matches("[A-Za-z0-9_-]{1,30}"))throw new IllegalArgumentException("Identificador de chave inválido.");
    byte[] key=Base64.getDecoder().decode(value);
    if(key.length!=32)throw new IllegalArgumentException("AES exige chave de 32 bytes.");
    decoded.put(id,key);
   });
   keys=Map.copyOf(decoded);this.activeKey=activeKey;hmacKey=Base64.getDecoder().decode(configuredHmac);
   if(!keys.containsKey(activeKey)||hmacKey.length<32)throw new IllegalArgumentException("Configuração criptográfica inválida.");
  }catch(Exception exception){throw new IllegalStateException("Configuração criptográfica inválida.");}
 }
 public String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Falha de serialização.");}}
 public JsonNode tree(String json){try{return mapper.readTree(json);}catch(Exception e){throw new IllegalArgumentException("JSON inválido.");}}
 public String hash(String value){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
  catch(GeneralSecurityException e){throw new IllegalStateException("SHA-256 indisponível.");}
 }
 public String hmac(String purpose,String value){return hmacHex(hmacKey,purpose+"\n"+value);}
 public static String hmacHex(byte[] key,String value){
  try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
  catch(GeneralSecurityException e){throw new IllegalStateException("HMAC indisponível.");}
 }
 public String encrypt(String context,String plaintext){
  try{
   byte[] iv=new byte[12];random.nextBytes(iv);
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keys.get(activeKey),"AES"),new GCMParameterSpec(128,iv));
   cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
   byte[] encrypted=cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
   byte[] packed=ByteBuffer.allocate(iv.length+encrypted.length).put(iv).put(encrypted).array();
   return activeKey+"."+Base64.getEncoder().encodeToString(packed);
  }catch(GeneralSecurityException e){throw new IllegalStateException("Falha de criptografia.");}
 }
 public String decrypt(String context,String ciphertext){
  try{
   String[] parts=ciphertext.split("\\.",2);byte[] key=keys.get(parts[0]);byte[] packed=Base64.getDecoder().decode(parts[1]);
   if(key==null||packed.length<28)throw new IllegalArgumentException();
   byte[] iv=Arrays.copyOfRange(packed,0,12);byte[] encrypted=Arrays.copyOfRange(packed,12,packed.length);
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
   cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
   return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
  }catch(Exception e){throw new IllegalStateException("Falha de descriptografia.");}
 }
}
