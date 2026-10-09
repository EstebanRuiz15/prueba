package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.GeneralMessage;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.QueueService;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class PurchaseOrderUseCase {

    private static final long RESERVATION_TTL_SECONDS = 600L; // 10 minutes

    private final EventRepository eventRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final QueueService queueService;

    public Mono<TicketOrder> execute(String eventId, String customerId, int quantity) {
        return Mono.just(quantity)
                .filter(q -> q > 0)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.VAL_INVALID_QUANTITY)))
                .flatMap(q -> eventRepository.findById(eventId))
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_EVENT_NOT_FOUND)))
                .flatMap(event -> eventRepository.reserveTickets(eventId, quantity)
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_INSUFFICIENT_INVENTORY)))
                        .map(reserved -> buildTicketOrder(event.getId(), customerId, quantity, event.getTicketPrice())))
                .flatMap(ticketOrderRepository::save)
                .flatMap(order -> queueService.sendPurchaseOrder(order).thenReturn(order));
    }

    private TicketOrder buildTicketOrder(String eventId, String customerId, int quantity, BigDecimal unitPrice) {
        Instant now = Instant.now();
        BigDecimal price = unitPrice != null ? unitPrice.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;

        return TicketOrder.builder()
                .id(UUID.randomUUID().toString())
                .eventId(eventId)
                .customerId(customerId)
                .quantity(quantity)
                .totalPrice(price)
                .status(TicketStatus.PENDING_CONFIRMATION)
                .createdAt(now)
                .expiresAt(now.plusSeconds(RESERVATION_TTL_SECONDS))
                .updatedAt(now)
                .build();
    }
}
