package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import co.com.nequi.ticketing.model.gateways.PaymentGateway;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RequiredArgsConstructor
public class ProcessOrderAsyncUseCase {

    private static final long SAFETY_MARGIN_SECONDS = 5L;

    private final TicketOrderRepository ticketOrderRepository;
    private final PaymentGateway paymentGateway;

    public Mono<Void> execute(String orderId, String traceId, String sqsMessageId) {
        return ticketOrderRepository.findById(orderId)
                .flatMap(order -> {
                    if (!TicketStatus.PENDING_CONFIRMATION.equals(order.getStatus())) {
                        return Mono.empty();
                    }

                    long nowEpoch = Instant.now().getEpochSecond();
                    if (order.getDeadlineAt() != null && order.getDeadlineAt() - nowEpoch < SAFETY_MARGIN_SECONDS) {
                        return ticketOrderRepository.expireOrderTransaction(order, traceId).then();
                    }

                    return paymentGateway.charge(order.getId(), order.getPaymentReference())
                            .flatMap(approved -> {
                                if (Boolean.TRUE.equals(approved)) {
                                    return ticketOrderRepository.finalizeOrderSaleTransaction(order, traceId, sqsMessageId).then();
                                } else {
                                    return ticketOrderRepository.rejectOrderPaymentTransaction(order, "BUS006", "Payment rejected by gateway", traceId).then();
                                }
                            });
                })
                .then();
    }
}
