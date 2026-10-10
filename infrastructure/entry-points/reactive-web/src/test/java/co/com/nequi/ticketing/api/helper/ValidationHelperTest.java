package co.com.nequi.ticketing.api.helper;

import co.com.nequi.ticketing.api.dto.request.EventRequestDTO;
import co.com.nequi.ticketing.model.exception.BusinessException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

class ValidationHelperTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ValidationHelper validationHelper = new ValidationHelper(validator);

    @Test
    void validate_withValidObject_shouldPassThrough() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .name("Concert")
                .eventDate("2026-01-01T20:00:00Z")
                .venue("Stadium")
                .totalCapacity(100)
                .ticketPrice(BigDecimal.TEN)
                .build();

        StepVerifier.create(validationHelper.validate(dto))
                .expectNextMatches(d -> d.getName().equals("Concert"))
                .verifyComplete();
    }

    @Test
    void validate_withInvalidObject_shouldReturnBusinessException() {
        EventRequestDTO dto = EventRequestDTO.builder().build();

        StepVerifier.create(validationHelper.validate(dto))
                .expectError(BusinessException.class)
                .verify();
    }
}
