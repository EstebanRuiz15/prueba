package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.ActorType;
import co.com.nequi.ticketing.model.GeneralMessage;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.OrderType;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.IdempotencyRepository;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CreateComplimentaryUseCase {

    private final EventRepository eventRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final IdempotencyRepository idempotencyRepository;

    public Mono<TicketOrder> execute(String eventId, String adminUserId, int quantity, String recipient, String reason, String idempotencyKey, String requestHash, String traceId) {
        String fullIdempotencyKey = adminUserId + "#POST_COMPLIMENTARY#" + idempotencyKey;

        return idempotencyRepository.findByKey(fullIdempotencyKey)
                .flatMap(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        return Mono.error(new BusinessException(GeneralMessage.VAL_IDEMPOTENCY_MISMATCH));
                    }
                    return ticketOrderRepository.findById(record.getOrderId());
                })
                .switchIfEmpty(Mono.defer(() -> createComplimentaryOrder(eventId, adminUserId, quantity, recipient, reason, fullIdempotencyKey, requestHash, traceId)));
    }

    private Mono<TicketOrder> createComplimentaryOrder(String eventId, String adminUserId, int quantity, String recipient, String reason, String fullIdempotencyKey, String requestHash, String traceId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_EVENT_NOT_FOUND)))
                .flatMap(event -> {
                    Instant now = Instant.now();
                    String orderId = UUID.randomUUID().toString();

                    TicketOrder order = TicketOrder.builder()
                            .id(orderId)
                            .eventId(eventId)
                            .userId(adminUserId)
                            .orderType(OrderType.COMPLIMENTARY)
                            .quantity(quantity)
                            .unitPrice(BigDecimal.ZERO)
                            .totalAmount(BigDecimal.ZERO)
                            .currency(event.getCurrency() != null ? event.getCurrency() : "COP")
                            .status(TicketStatus.COMPLIMENTARY)
                            .recipient(recipient)
                            .complimentaryReason(reason)
                            .traceId(traceId)
                            .version(1L)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    OrderHistory history = OrderHistory.builder()
                            .orderId(orderId)
                            .sequence(1L)
                            .fromStatus(null)
                            .toStatus(TicketStatus.COMPLIMENTARY)
                            .reasonCode("COMPLIMENTARY_ISSUED")
                            .actor(ActorType.ADMIN)
                            .quantity(quantity)
                            .traceId(traceId)
                            .occurredAt(now)
                            .build();

                    IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder()
                            .idempotencyKey(fullIdempotencyKey)
                            .requestHash(requestHash)
                            .orderId(orderId)
                            .ttl(now.getEpochSecond() + 86400L)
                            .build();

                    return ticketOrderRepository.createComplimentaryTransaction(order, history, idempotencyRecord);
                });
    }
}
