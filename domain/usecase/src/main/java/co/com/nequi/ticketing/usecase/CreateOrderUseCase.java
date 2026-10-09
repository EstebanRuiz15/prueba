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
import co.com.nequi.ticketing.model.gateways.QueueService;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

@RequiredArgsConstructor
public class CreateOrderUseCase {

    private static final long RESERVATION_TTL_SECONDS = 600L;
    private static final int EXPIRY_SHARD_COUNT = 4;

    private final EventRepository eventRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final QueueService queueService;
    private final Random random = new Random();

    public Mono<TicketOrder> execute(String eventId, String userId, int quantity, String idempotencyKey, String requestHash, String traceId) {
        String fullIdempotencyKey = userId + "#POST_ORDERS#" + idempotencyKey;

        return idempotencyRepository.findByKey(fullIdempotencyKey)
                .flatMap(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        return Mono.error(new BusinessException(GeneralMessage.VAL_IDEMPOTENCY_MISMATCH));
                    }
                    return ticketOrderRepository.findById(record.getOrderId());
                })
                .switchIfEmpty(Mono.defer(() -> createNewOrder(eventId, userId, quantity, fullIdempotencyKey, requestHash, traceId)));
    }

    private Mono<TicketOrder> createNewOrder(String eventId, String userId, int quantity, String fullIdempotencyKey, String requestHash, String traceId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_EVENT_NOT_FOUND)))
                .flatMap(event -> {
                    Instant now = Instant.now();
                    long deadlineEpoch = now.getEpochSecond() + RESERVATION_TTL_SECONDS;
                    String orderId = UUID.randomUUID().toString();
                    String shard = "S#" + random.nextInt(EXPIRY_SHARD_COUNT);
                    BigDecimal unitPrice = event.getTicketPrice() != null ? event.getTicketPrice() : BigDecimal.ZERO;
                    BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

                    TicketOrder newOrder = TicketOrder.builder()
                            .id(orderId)
                            .eventId(eventId)
                            .userId(userId)
                            .orderType(OrderType.PURCHASE)
                            .quantity(quantity)
                            .unitPrice(unitPrice)
                            .totalAmount(totalAmount)
                            .currency(event.getCurrency() != null ? event.getCurrency() : "COP")
                            .status(TicketStatus.RESERVED)
                            .deadlineAt(deadlineEpoch)
                            .expiryShard(shard)
                            .traceId(traceId)
                            .version(1L)
                            .createdAt(now)
                            .expiresAt(Instant.ofEpochSecond(deadlineEpoch))
                            .updatedAt(now)
                            .build();

                    OrderHistory history = OrderHistory.builder()
                            .orderId(orderId)
                            .sequence(1L)
                            .fromStatus(null)
                            .toStatus(TicketStatus.RESERVED)
                            .reasonCode("ORDER_CREATED")
                            .actor(ActorType.API)
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

                    return ticketOrderRepository.createOrderTransaction(newOrder, history, idempotencyRecord)
                            .flatMap(savedOrder -> queueService.sendExpireOrderMessage(orderId, deadlineEpoch, RESERVATION_TTL_SECONDS)
                                    .onErrorResume(e -> Mono.empty())
                                    .thenReturn(savedOrder));
                });
    }
}
