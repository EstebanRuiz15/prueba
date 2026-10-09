package co.com.nequi.ticketing.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GeneralMessage {
    SUCCESS("0", "Operation completed successfully", 200),
    CREATED("0", "Resource created successfully", 201),
    ACCEPTED("0", "Order queued for asynchronous processing", 202),

    // Validation Errors (VALxxx)
    VAL_INVALID_INPUT("VAL001", "Required field is missing or invalid", 400),
    VAL_INVALID_QUANTITY("VAL002", "Ticket quantity must be between 1 and maximum allowed", 400),
    VAL_IDEMPOTENCY_KEY_REQUIRED("VAL003", "Idempotency-Key header is required", 400),
    VAL_IDEMPOTENCY_MISMATCH("VAL004", "Idempotency key reused with different request body", 422),

    // Security Errors (SECxxx)
    SEC_UNAUTHORIZED("SEC001", "Authentication header missing or invalid", 401),
    SEC_FORBIDDEN("SEC002", "Insufficient permissions for this operation", 403),

    // Business Errors (BUSxxx)
    BUS_EVENT_NOT_FOUND("BUS001", "Event not found or not active", 404),
    BUS_INSUFFICIENT_INVENTORY("BUS002", "Insufficient tickets available", 409),
    BUS_ORDER_EXPIRED("BUS003", "Order reservation has expired", 422),
    BUS_INVALID_STATE_TRANSITION("BUS004", "Illegal ticket status transition", 409),
    BUS_ORDER_NOT_FOUND("BUS005", "Order not found or unauthorized", 404),
    BUS_PAYMENT_REJECTED("BUS006", "Payment processing failed", 422),
    BUS_CONCURRENCY_CONFLICT("BUS007", "High concurrency conflict, please retry", 409),

    // Technical Errors (ERRxxx)
    TECH_DYNAMO_ERROR("ERR001", "Database operation failed", 500),
    TECH_SQS_ERROR("ERR002", "Failed to dispatch message to SQS queue", 500),
    TECH_CIRCUIT_OPEN("ERR003", "Service temporarily unavailable due to circuit breaker", 503),
    TECH_INTERNAL_ERROR("ERR999", "An unexpected technical error occurred", 500);

    private final String code;
    private final String description;
    private final int httpStatus;
}
