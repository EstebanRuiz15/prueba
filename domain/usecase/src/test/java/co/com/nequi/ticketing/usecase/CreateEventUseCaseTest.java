package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.EventCategory;
import co.com.nequi.ticketing.model.Inventory;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateEventUseCaseTest {

    @Mock
    private EventRepository eventRepository;

    private CreateEventUseCase createEventUseCase;

    @BeforeEach
    void setUp() {
        createEventUseCase = new CreateEventUseCase(eventRepository);
    }

    @Test
    void shouldCreateEventSuccessfully() {
        Event inputEvent = Event.builder()
                .name("Rock Concert 2026")
                .eventDate("2026-12-01T20:00:00Z")
                .venue("Stadium")
                .category(EventCategory.CONCERT)
                .totalCapacity(100)
                .ticketPrice(BigDecimal.valueOf(50.0))
                .build();

        when(eventRepository.createWithInventory(any(Event.class), any(Inventory.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(createEventUseCase.execute(inputEvent))
                .expectNextMatches(saved -> saved.getId() != null && saved.getListingKey().equals("EVENT"))
                .verifyComplete();
    }

    @Test
    void shouldFailWhenCapacityIsInvalid() {
        Event inputEvent = Event.builder()
                .name("Invalid Event")
                .totalCapacity(0)
                .build();

        StepVerifier.create(createEventUseCase.execute(inputEvent))
                .expectError(BusinessException.class)
                .verify();
    }
}
