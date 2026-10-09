package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RequiredArgsConstructor
public class ReleaseExpiredReservationsUseCase {

    private static final int EXPIRY_SHARD_COUNT = 4;
    private final TicketOrderRepository ticketOrderRepository;

    public Flux<TicketOrder> executeSweeper() {
        long nowEpoch = Instant.now().getEpochSecond();
        return Flux.range(0, EXPIRY_SHARD_COUNT)
                .map(shardIdx -> "S#" + shardIdx)
                .flatMap(shard -> ticketOrderRepository.findExpiredOrdersByShard(shard, nowEpoch))
                .flatMap(this::expireSingleOrder);
    }

    public Mono<Void> expireOrderById(String orderId, long messageDeadlineAt, String traceId) {
        return ticketOrderRepository.findById(orderId)
                .flatMap(order -> {
                    if (!TicketStatus.RESERVED.equals(order.getStatus()) && !TicketStatus.PENDING_CONFIRMATION.equals(order.getStatus())) {
                        return Mono.empty();
                    }
                    if (order.getDeadlineAt() != null && order.getDeadlineAt() != messageDeadlineAt) {
                        return Mono.empty(); // Stale message
                    }
                    return expireSingleOrder(order);
                })
                .then();
    }

    private Mono<TicketOrder> expireSingleOrder(TicketOrder order) {
        return ticketOrderRepository.expireOrderTransaction(order, "SYSTEM_EXPIRER");
    }
}
