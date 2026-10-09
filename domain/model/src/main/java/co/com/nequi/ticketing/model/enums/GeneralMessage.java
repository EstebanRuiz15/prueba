package co.com.nequi.ticketing.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum GeneralMessage {
    SUCCESS("0", "Operation completed successfully", HTTP_200),
    CREATED("0", "Resource created successfully", HTTP_201),
    ACCEPTED("0", "Order queued for asynchronous processing", HTTP_202),

    // Validation Errors (VALxxx)
    VAL_INVALID_INPUT("VAL001", "Required field is missing or invalid", HTTP_400),
    VAL_INVALID_QUANTITY("VAL002", "Ticket quantity must be between 1 and maximum allowed", HTTP_400),
    VAL_IDEMPOTENCY_KEY_REQUIRED("VAL003", "Idempotency-Key header is required", HTTP_400),
    VAL_IDEMPOTENCY_MISMATCH("VAL004", "Idempotency key reused with different request body", HTTP_422),

    // Security Errors (SECxxx)
    SEC_UNAUTHORIZED("SEC001", "Authentication header missing or invalid", HTTP_401),
    SEC_FORBIDDEN("SEC002", "Insufficient permissions for this operation", HTTP_403),

    // Business Errors (BUSxxx)
    BUS_EVENT_NOT_FOUND("BUS001", "Event not found or not active", HTTP_404),
    BUS_INSUFFICIENT_INVENTORY("BUS002", "Insufficient tickets available", HTTP_409),
    BUS_ORDER_EXPIRED("BUS003", "Order reservation has expired", HTTP_422),
    BUS_INVALID_STATE_TRANSITION("BUS004", "Illegal ticket status transition", HTTP_409),
    BUS_ORDER_NOT_FOUND("BUS005", "Order not found or unauthorized", HTTP_404),
    BUS_PAYMENT_REJECTED("BUS006", "Payment processing failed", HTTP_422),
    BUS_CONCURRENCY_CONFLICT("BUS007", "High concurrency conflict, please retry", HTTP_409),

    // Technical Errors (ERRxxx)
    TECH_DYNAMO_ERROR("ERR001", "Database operation failed", HTTP_500),
    TECH_SQS_ERROR("ERR002", "Failed to dispatch message to SQS queue", HTTP_500),
    TECH_CIRCUIT_OPEN("ERR003", "Service temporarily unavailable due to circuit breaker", HTTP_503),
    TECH_INTERNAL_ERROR("ERR999", "An unexpected technical error occurred", HTTP_500);

    private static final int HTTP_200 = 200;
    private static final int HTTP_201 = 201;
    private static final int HTTP_202 = 202;
    private static final int HTTP_400 = 400;
    private static final int HTTP_401 = 401;
    private static final int HTTP_403 = 403;
    private static final int HTTP_404 = 404;
    private static final int HTTP_409 = 409;
    private static final int HTTP_422 = 422;
    private static final int HTTP_500 = 500;
    private static final int HTTP_503 = 503;

    private final String code;
    private final String description;
    private final int httpStatus;

    private static final Map<String, GeneralMessage> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(GeneralMessage::getCode, gm -> gm, (a, b) -> a));

    public static GeneralMessage findByCode(String code) {
        return Objects.isNull(code) ? TECH_INTERNAL_ERROR : BY_CODE.getOrDefault(code, TECH_INTERNAL_ERROR);
    }
}
