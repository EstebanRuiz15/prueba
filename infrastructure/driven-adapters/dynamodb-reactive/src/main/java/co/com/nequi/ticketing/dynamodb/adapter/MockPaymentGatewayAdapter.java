package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.model.gateways.PaymentGateway;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class MockPaymentGatewayAdapter implements PaymentGateway {

    @Override
    public Mono<Boolean> charge(String orderId, String paymentRef) {
        if ("REJECT_PAYMENT".equals(paymentRef)) {
            return Mono.just(false);
        }
        return Mono.just(true);
    }
}
