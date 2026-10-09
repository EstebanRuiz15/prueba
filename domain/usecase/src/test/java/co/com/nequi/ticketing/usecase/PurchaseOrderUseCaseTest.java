package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.QueueService;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderUseCaseTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private QueueService queueService;

    private PurchaseOrderUseCase purchaseOrderUseCase;

    @BeforeEach
    void setUp() {
        purchaseOrderUseCase = new PurchaseOrderUseCase(eventRepository, ticketOrderRepository, queueService);
    }

    @Test
    void shouldPurchaseOrderSuccessfully() {
        Event event = Event.builder()
                .id("evt-123")
                .name("Festival")
                .availableCapacity(10)
                .ticketPrice(BigDecimal.valueOf(100.0))
                .build();

        when(eventRepository.findById("evt-123")).thenReturn(Mono.just(event));
        when(eventRepository.reserveTickets("evt-123", 2)).thenReturn(Mono.just(true));
        when(ticketOrderRepository.save(any(TicketOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(queueService.sendPurchaseOrder(any(TicketOrder.class))).thenReturn(Mono.empty());

        StepVerifier.create(purchaseOrderUseCase.execute("evt-123", "cust-99", 2))
                .expectNextMatches(order -> order.getStatus() == TicketStatus.PENDING_CONFIRMATION
                        && order.getQuantity() == 2
                        && order.getTotalPrice().compareTo(BigDecimal.valueOf(200.0)) == 0)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenInsufficientInventory() {
        Event event = Event.builder()
                .id("evt-123")
                .availableCapacity(1)
                .build();

        when(eventRepository.findById("evt-123")).thenReturn(Mono.just(event));
        when(eventRepository.reserveTickets("evt-123", 5)).thenReturn(Mono.just(false));

        StepVerifier.create(purchaseOrderUseCase.execute("evt-123", "cust-99", 5))
                .expectError(BusinessException.class)
                .verify();
    }
}
