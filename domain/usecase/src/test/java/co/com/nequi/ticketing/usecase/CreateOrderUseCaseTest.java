package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.IdempotencyRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private QueueService queueService;

    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    void setUp() {
        createOrderUseCase = new CreateOrderUseCase(eventRepository, ticketOrderRepository, idempotencyRepository, queueService);
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        Event event = Event.builder().id("evt-1").ticketPrice(BigDecimal.valueOf(100.0)).currency("COP").build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById("evt-1")).thenReturn(Mono.just(event));
        when(ticketOrderRepository.createOrderTransaction(any(TicketOrder.class), any(OrderHistory.class), any(IdempotencyRecord.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(queueService.sendExpireOrderMessage(anyString(), anyLong(), anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(createOrderUseCase.execute("evt-1", "usr-1", 2, "key-123", "hash123", "trace-1"))
                .expectNextMatches(order -> order.getStatus() == TicketStatus.RESERVED && order.getQuantity() == 2)
                .verifyComplete();
    }
}
