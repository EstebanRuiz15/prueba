package co.com.nequi.ticketing.api.helper;

import co.com.nequi.ticketing.model.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
public class ValidationHelper {

    private final Validator validator;

    public ValidationHelper(Validator validator) {
        this.validator = validator;
    }

    public <T> Mono<T> validate(T target) {
        return Mono.just(target)
                .flatMap(obj -> {
                    Set<ConstraintViolation<T>> violations = validator.validate(obj);
                    return violations.isEmpty()
                            ? Mono.just(obj)
                            : Mono.error(new BusinessException(GeneralMessage.VAL_INVALID_INPUT,
                            violations.iterator().next().getMessage()));
                });
    }
}
