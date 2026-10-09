package co.com.nequi.ticketing.api.helper;

import co.com.nequi.ticketing.model.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class ResponseHelper {

    public <T> Mono<ServerResponse> buildResponse(GeneralMessage message, T data) {
        ResponseEnvelope<T> envelope = ResponseEnvelope.success(message.getCode(), message.getDescription(), data);
        return ServerResponse.status(message.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(envelope);
    }

    public Mono<ServerResponse> buildErrorResponse(Throwable error) {
        return Mono.just(error)
                .filter(BusinessException.class::isInstance)
                .cast(BusinessException.class)
                .flatMap(be -> buildErrorResponse(be.getGeneralMessage()))
                .switchIfEmpty(Mono.defer(() -> Mono.just(error)
                        .filter(TechnicalException.class::isInstance)
                        .cast(TechnicalException.class)
                        .flatMap(te -> buildErrorResponse(te.getGeneralMessage()))))
                .switchIfEmpty(Mono.defer(() -> buildErrorResponse(GeneralMessage.TECH_INTERNAL_ERROR)));
    }

    public Mono<ServerResponse> buildErrorResponse(GeneralMessage message) {
        ResponseEnvelope<Object> envelope = ResponseEnvelope.error(message.getCode(), message.getDescription());
        return ServerResponse.status(message.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(envelope);
    }
}
