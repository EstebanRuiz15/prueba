package co.com.nequi.ticketing.api;

import co.com.nequi.ticketing.api.dto.CancelOrderRequestDTO;
import co.com.nequi.ticketing.api.dto.ConfirmOrderRequestDTO;
import co.com.nequi.ticketing.api.dto.PurchaseOrderRequestDTO;
import co.com.nequi.ticketing.api.filter.TraceAndSecurityFilter;
import co.com.nequi.ticketing.api.helper.LogAppenderHelper;
import co.com.nequi.ticketing.api.helper.ResponseHelper;
import co.com.nequi.ticketing.api.helper.ValidationHelper;
import co.com.nequi.ticketing.api.mapper.OrderHistoryDTOMapper;
import co.com.nequi.ticketing.api.mapper.PurchaseOrderDTOMapper;
import co.com.nequi.ticketing.model.GeneralMessage;
import co.com.nequi.ticketing.usecase.CancelOrderUseCase;
import co.com.nequi.ticketing.usecase.ConfirmOrderUseCase;
import co.com.nequi.ticketing.usecase.CreateOrderUseCase;
import co.com.nequi.ticketing.usecase.GetOrderHistoryUseCase;
import co.com.nequi.ticketing.usecase.GetOrderUseCase;
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
public class OrderHandler {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final GetOrderHistoryUseCase getOrderHistoryUseCase;
    private final PurchaseOrderDTOMapper purchaseOrderDTOMapper;
    private final OrderHistoryDTOMapper orderHistoryDTOMapper;
    private final ValidationHelper validationHelper;
    private final ResponseHelper responseHelper;
    private final LogAppenderHelper logAppender;

    public Mono<ServerResponse> createOrder(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String endpoint = "POST /api/v1/orders";
        String idempotencyKey = request.headers().firstHeader("Idempotency-Key");
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse("usr-default").toString();
        String traceId = request.attribute(TraceAndSecurityFilter.TRACE_ID_HEADER).orElse("").toString();

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return responseHelper.buildErrorResponse(GeneralMessage.VAL_IDEMPOTENCY_KEY_REQUIRED);
        }

        return request.bodyToMono(PurchaseOrderRequestDTO.class)
                .doOnNext(dto -> logAppender.logRequest(endpoint, dto))
                .flatMap(validationHelper::validate)
                .flatMap(dto -> {
                    String hash = hashBody(dto);
                    return createOrderUseCase.execute(dto.getEventId(), userId, dto.getQuantity(), idempotencyKey, hash, traceId);
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

    public Mono<ServerResponse> getOrderById(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String orderId = request.pathVariable("orderId");
        String endpoint = "GET /api/v1/orders/" + orderId;
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse("usr-default").toString();
        String role = request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse("USER").toString();
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        logAppender.logRequest(endpoint, orderId);

        return getOrderUseCase.getById(orderId, userId, isAdmin)
                .map(purchaseOrderDTOMapper::toResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return responseHelper.buildResponse(GeneralMessage.SUCCESS, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return responseHelper.buildErrorResponse(error);
                });
    }

    public Mono<ServerResponse> confirmOrder(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String orderId = request.pathVariable("orderId");
        String endpoint = "POST /api/v1/orders/" + orderId + "/confirm";
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse("usr-default").toString();
        String role = request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse("USER").toString();
        String traceId = request.attribute(TraceAndSecurityFilter.TRACE_ID_HEADER).orElse("").toString();
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        return request.bodyToMono(ConfirmOrderRequestDTO.class)
                .defaultIfEmpty(new ConfirmOrderRequestDTO())
                .doOnNext(dto -> logAppender.logRequest(endpoint, dto))
                .flatMap(dto -> confirmOrderUseCase.execute(orderId, userId, isAdmin, dto.getPaymentReference(), traceId))
                .map(purchaseOrderDTOMapper::toResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return responseHelper.buildResponse(GeneralMessage.ACCEPTED, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return responseHelper.buildErrorResponse(error);
                });
    }

    public Mono<ServerResponse> cancelOrder(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String orderId = request.pathVariable("orderId");
        String endpoint = "POST /api/v1/orders/" + orderId + "/cancel";
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse("usr-default").toString();
        String role = request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse("USER").toString();
        String traceId = request.attribute(TraceAndSecurityFilter.TRACE_ID_HEADER).orElse("").toString();
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        return request.bodyToMono(CancelOrderRequestDTO.class)
                .defaultIfEmpty(new CancelOrderRequestDTO())
                .doOnNext(dto -> logAppender.logRequest(endpoint, dto))
                .flatMap(dto -> cancelOrderUseCase.execute(orderId, userId, isAdmin, dto.getReason(), traceId))
                .map(purchaseOrderDTOMapper::toResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return responseHelper.buildResponse(GeneralMessage.SUCCESS, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return responseHelper.buildErrorResponse(error);
                });
    }

    public Mono<ServerResponse> getOrderHistory(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String orderId = request.pathVariable("orderId");
        String endpoint = "GET /api/v1/orders/" + orderId + "/history";
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse("usr-default").toString();
        String role = request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse("USER").toString();
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        logAppender.logRequest(endpoint, orderId);

        return getOrderHistoryUseCase.execute(orderId, userId, isAdmin)
                .map(orderHistoryDTOMapper::toResponseDTO)
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
