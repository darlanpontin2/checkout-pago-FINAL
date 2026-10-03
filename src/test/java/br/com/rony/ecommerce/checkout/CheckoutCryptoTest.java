package br.com.rony.ecommerce.checkout;

import br.com.rony.ecommerce.adapters.infrastructure.checkout.CheckoutCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutCryptoTest {
    private CheckoutCrypto crypto() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // Chave determinística exclusivamente para testes; nunca usar em produção.
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        return new CheckoutCrypto(mapper, mapper.writeValueAsString(Map.of("test", key)), "test", key);
    }

    @Test
    void encryptsAndDecrypts() throws Exception {
        CheckoutCrypto crypto = crypto();
        String encrypted = crypto.encrypt("customer:1", "conteudo");
        assertNotEquals("conteudo", encrypted);
        assertEquals("conteudo", crypto.decrypt("customer:1", encrypted));
    }

    @Test
    void rejectsDifferentAssociatedContext() throws Exception {
        CheckoutCrypto crypto = crypto();
        String encrypted = crypto.encrypt("customer:1", "conteudo");
        assertThrows(IllegalStateException.class, () -> crypto.decrypt("customer:2", encrypted));
    }

    @Test
    void randomIvProducesDifferentCiphertexts() throws Exception {
        CheckoutCrypto crypto = crypto();
        assertNotEquals(crypto.encrypt("customer:1", "conteudo"), crypto.encrypt("customer:1", "conteudo"));
    }
}
