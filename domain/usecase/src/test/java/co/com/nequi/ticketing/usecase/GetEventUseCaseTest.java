package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.Inventory;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetEventUseCaseTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    private GetEventUseCase getEventUseCase;

    @BeforeEach
    void setUp() {
        getEventUseCase = new GetEventUseCase(eventRepository, inventoryRepository);
    }

    @Test
    void shouldGetEventDetailSuccessfully() {
        Event event = Event.builder().id("evt-1").name("Concert").build();
        Inventory inventory = Inventory.builder().eventId("evt-1").available(100).totalCapacity(100).build();

        when(eventRepository.findById("evt-1")).thenReturn(Mono.just(event));
        when(inventoryRepository.findByEventIdConsistent("evt-1")).thenReturn(Mono.just(inventory));

        StepVerifier.create(getEventUseCase.getEventDetail("evt-1"))
                .expectNextMatches(detail -> detail.getEvent().getId().equals("evt-1") && detail.getInventory().getAvailable() == 100)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenEventNotFound() {
        when(eventRepository.findById("evt-404")).thenReturn(Mono.empty());

        StepVerifier.create(getEventUseCase.getEventDetail("evt-404"))
                .expectError(BusinessException.class)
                .verify();
    }
}
