package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.GeneralMessage;
import co.com.nequi.ticketing.model.ReleaseReason;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CancelOrderUseCase {

    private final TicketOrderRepository ticketOrderRepository;

    public Mono<TicketOrder> execute(String orderId, String userId, boolean isAdmin, String reason, String traceId) {
        return ticketOrderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)))
                .filter(order -> isAdmin || order.getUserId().equals(userId))
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)))
                .flatMap(order -> {
                    if (TicketStatus.AVAILABLE.equals(order.getStatus()) && ReleaseReason.CANCELLED_BY_USER.equals(order.getReleaseReason())) {
                        return Mono.just(order);
                    }
                    if (!TicketStatus.RESERVED.equals(order.getStatus())) {
                        return Mono.error(new BusinessException(GeneralMessage.BUS_INVALID_STATE_TRANSITION));
                    }
                    return ticketOrderRepository.cancelOrderTransaction(order, reason, traceId);
                });
    }
}
