package co.com.nequi.ticketing.api;

import co.com.nequi.ticketing.api.dto.ComplimentaryRequestDTO;
import co.com.nequi.ticketing.api.dto.EventRequestDTO;
import co.com.nequi.ticketing.api.filter.TraceAndSecurityFilter;
import co.com.nequi.ticketing.api.helper.LogAppenderHelper;
import co.com.nequi.ticketing.api.helper.ResponseHelper;
import co.com.nequi.ticketing.api.helper.ValidationHelper;
import co.com.nequi.ticketing.api.mapper.EventDTOMapper;
import co.com.nequi.ticketing.api.mapper.PurchaseOrderDTOMapper;
import co.com.nequi.ticketing.model.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.usecase.CreateComplimentaryUseCase;
import co.com.nequi.ticketing.usecase.CreateEventUseCase;
import co.com.nequi.ticketing.usecase.GetEventUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class EventHandler {

    private final CreateEventUseCase createEventUseCase;
    private final GetEventUseCase getEventUseCase;
    private final CreateComplimentaryUseCase createComplimentaryUseCase;
    private final EventDTOMapper eventDTOMapper;
    private final PurchaseOrderDTOMapper purchaseOrderDTOMapper;
    private final ValidationHelper validationHelper;
    private final ResponseHelper responseHelper;
    private final LogAppenderHelper logAppender;

    public Mono<ServerResponse> createEvent(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String endpoint = "POST /api/v1/events";
        String role = request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse("USER").toString();

        if (!"ADMIN".equalsIgnoreCase(role)) {
            return responseHelper.buildErrorResponse(GeneralMessage.SEC_FORBIDDEN);
        }

        return request.bodyToMono(EventRequestDTO.class)
                .doOnNext(dto -> logAppender.logRequest(endpoint, dto))
                .flatMap(validationHelper::validate)
                .map(eventDTOMapper::toDomain)
                .flatMap(createEventUseCase::execute)
                .map(eventDTOMapper::toResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return responseHelper.buildResponse(GeneralMessage.CREATED, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return responseHelper.buildErrorResponse(error);
                });
    }

    public Mono<ServerResponse> getAllEvents(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String endpoint = "GET /api/v1/events";
        int limit = request.queryParam("limit").map(Integer::parseInt).orElse(20);
        String cursor = request.queryParam("cursor").orElse(null);

        logAppender.logRequest(endpoint, null);

        return getEventUseCase.getAllPaginated(limit, cursor)
                .map(eventDTOMapper::toResponseDTO)
                .collectList()
                .flatMap(list -> {
                    logAppender.logResponse(endpoint, list, System.currentTimeMillis() - startTime);
                    return responseHelper.buildResponse(GeneralMessage.SUCCESS, list);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return responseHelper.buildErrorResponse(error);
                });
    }

    public Mono<ServerResponse> getEventById(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String eventId = request.pathVariable("eventId");
        String endpoint = "GET /api/v1/events/" + eventId;

        logAppender.logRequest(endpoint, eventId);

        return getEventUseCase.getEventDetail(eventId)
                .map(eventDTOMapper::toDetailResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return responseHelper.buildResponse(GeneralMessage.SUCCESS, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return responseHelper.buildErrorResponse(error);
                });
    }

    public Mono<ServerResponse> createComplimentary(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String eventId = request.pathVariable("eventId");
        String endpoint = "POST /api/v1/events/" + eventId + "/complimentary";
        String role = request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse("USER").toString();
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse("admin-usr").toString();
        String traceId = request.attribute(TraceAndSecurityFilter.TRACE_ID_HEADER).orElse("").toString();
        String idempotencyKey = request.headers().firstHeader("Idempotency-Key");

        if (!"ADMIN".equalsIgnoreCase(role)) {
            return responseHelper.buildErrorResponse(GeneralMessage.SEC_FORBIDDEN);
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return responseHelper.buildErrorResponse(GeneralMessage.VAL_IDEMPOTENCY_KEY_REQUIRED);
        }

        return request.bodyToMono(ComplimentaryRequestDTO.class)
                .doOnNext(dto -> logAppender.logRequest(endpoint, dto))
                .flatMap(validationHelper::validate)
                .flatMap(dto -> {
                    String hash = hashBody(dto);
                    return createComplimentaryUseCase.execute(eventId, userId, dto.getQuantity(), dto.getRecipient(), dto.getReason(), idempotencyKey, hash, traceId);
                })
                .map(purchaseOrderDTOMapper::toResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return responseHelper.buildResponse(GeneralMessage.CREATED, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return responseHelper.buildErrorResponse(error);
                });
    }

    private String hashBody(Object body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(body.toString().getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(body.hashCode());
        }
    }
}
