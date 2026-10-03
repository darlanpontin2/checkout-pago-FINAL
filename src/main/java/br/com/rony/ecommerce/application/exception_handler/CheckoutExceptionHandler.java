package br.com.rony.ecommerce.application.exception_handler;

import br.com.rony.ecommerce.domain.exceptions.CheckoutException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import java.util.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "br.com.rony.ecommerce.application.controllers.checkout")
public class CheckoutExceptionHandler {
    @ExceptionHandler(CheckoutException.class)
    public ResponseEntity<Map<String, Object>> business(CheckoutException exception) {
        Map<String, Object> response = body(exception.getCode(), exception.getMessage());
        if (exception.getField() != null) response.put("campo_erro", exception.getField());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.getHttpStatus());
        if (exception.getHttpStatus() == 429) builder.header("Retry-After", "60");
        return builder.body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        Map<String, Object> response = body("DADOS_INVALIDOS", "Verifique os campos informados.");
        response.put("detalhes", exception.getBindingResult().getFieldErrors().stream()
            .map(error -> Map.of("campo", error.getField(), "mensagem", "Campo ausente ou inválido.")).toList());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> malformed(Exception exception) {
        return ResponseEntity.badRequest().body(body("REQUISICAO_INVALIDA",
            "Formato ou parâmetros da requisição inválidos."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception exception) {
        return ResponseEntity.status(500).body(body("ERRO_INTERNO", "Não foi possível concluir a operação."));
    }

    private Map<String, Object> body(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sucesso", false); result.put("erro", code); result.put("mensagem", message);
        return result;
    }
}
