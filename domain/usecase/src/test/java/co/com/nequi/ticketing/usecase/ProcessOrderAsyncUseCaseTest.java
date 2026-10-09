package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import co.com.nequi.ticketing.model.gateways.PaymentGateway;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessOrderAsyncUseCaseTest {

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private PaymentGateway paymentGateway;

    private ProcessOrderAsyncUseCase processOrderAsyncUseCase;

    @BeforeEach
    void setUp() {
        processOrderAsyncUseCase = new ProcessOrderAsyncUseCase(ticketOrderRepository, paymentGateway);
    }

    @Test
    void shouldFinalizeOrderSaleWhenPaymentApproved() {
        TicketOrder order = TicketOrder.builder()
                .id("ord-1")
                .status(TicketStatus.PENDING_CONFIRMATION)
                .deadlineAt(Instant.now().getEpochSecond() + 200L)
                .paymentReference("tok_visa")
                .build();

        when(ticketOrderRepository.findById("ord-1")).thenReturn(Mono.just(order));
        when(paymentGateway.charge("ord-1", "tok_visa")).thenReturn(Mono.just(true));
        when(ticketOrderRepository.finalizeOrderSaleTransaction(any(TicketOrder.class), anyString(), anyString()))
                .thenReturn(Mono.just(order.toBuilder().status(TicketStatus.SOLD).build()));

        StepVerifier.create(processOrderAsyncUseCase.execute("ord-1", "trace-1", "msg-1"))
                .verifyComplete();
    }
}
