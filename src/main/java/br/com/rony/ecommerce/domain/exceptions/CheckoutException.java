package br.com.rony.ecommerce.domain.exceptions;
public class CheckoutException extends RuntimeException {
 private final int httpStatus; private final String code; private final String field;
 public CheckoutException(int httpStatus, String code, String message, String field) {
  super(message); this.httpStatus = httpStatus; this.code = code; this.field = field;
 }
 public CheckoutException(int httpStatus, String code, String message) { this(httpStatus, code, message, null); }
 public int getHttpStatus() { return httpStatus; }
 public String getCode() { return code; }
 public String getField() { return field; }
}
