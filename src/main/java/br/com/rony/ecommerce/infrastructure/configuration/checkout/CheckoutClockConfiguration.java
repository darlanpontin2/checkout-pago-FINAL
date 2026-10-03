package br.com.rony.ecommerce.infrastructure.configuration.checkout;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration
public class CheckoutClockConfiguration {
 @Bean public Clock checkoutClock(){return Clock.systemUTC();}
}
