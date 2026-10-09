package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.GeneralMessage;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.QueueService;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RequiredArgsConstructor
public class ConfirmOrderUseCase {

    private static final long PENDING_TIMEOUT_SECONDS = 300L;

    private final TicketOrderRepository ticketOrderRepository;
    private final QueueService queueService;

    public Mono<TicketOrder> execute(String orderId, String userId, boolean isAdmin, String paymentReference, String traceId) {
        return ticketOrderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)))
                .filter(order -> isAdmin || order.getUserId().equals(userId))
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)))
                .flatMap(order -> {
                    if (TicketStatus.PENDING_CONFIRMATION.equals(order.getStatus())) {
                        return queueService.sendFinalizeOrderMessage(orderId, traceId).thenReturn(order);
                    }
                    if (TicketStatus.SOLD.equals(order.getStatus())) {
                        return Mono.just(order);
                    }
                    if (!TicketStatus.RESERVED.equals(order.getStatus())) {
                        return Mono.error(new BusinessException(GeneralMessage.BUS_INVALID_STATE_TRANSITION));
                    }
                    if (order.getDeadlineAt() != null && order.getDeadlineAt() <= Instant.now().getEpochSecond()) {
                        return Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_EXPIRED));
                    }

                    return ticketOrderRepository.confirmOrderTransaction(order, paymentReference, traceId)
                            .flatMap(confirmedOrder -> queueService.sendFinalizeOrderMessage(orderId, traceId)
                                    .then(queueService.sendExpireOrderMessage(orderId, confirmedOrder.getDeadlineAt(), PENDING_TIMEOUT_SECONDS).onErrorResume(e -> Mono.empty()))
                                    .thenReturn(confirmedOrder));
                });
    }
}
