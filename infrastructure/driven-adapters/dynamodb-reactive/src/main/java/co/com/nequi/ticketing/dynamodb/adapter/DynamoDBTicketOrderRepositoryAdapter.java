package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.IdempotencyEntity;
import co.com.nequi.ticketing.dynamodb.entity.OrderHistoryEntity;
import co.com.nequi.ticketing.dynamodb.entity.TicketOrderEntity;
import co.com.nequi.ticketing.dynamodb.mapper.IdempotencyEntityMapper;
import co.com.nequi.ticketing.dynamodb.mapper.OrderHistoryEntityMapper;
import co.com.nequi.ticketing.dynamodb.mapper.TicketOrderEntityMapper;
import co.com.nequi.ticketing.model.GeneralMessage;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.ReleaseReason;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsException;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Repository
public class DynamoDBTicketOrderRepositoryAdapter implements TicketOrderRepository {

    @Value("${aws.dynamodb.table-orders:ticketing-orders}")
    private String ordersTableName;

    @Value("${aws.dynamodb.table-inventory:ticketing-inventory}")
    private String inventoryTableName;

    @Value("${aws.dynamodb.table-order-history:ticketing-order-history}")
    private String historyTableName;

    @Value("${aws.dynamodb.table-idempotency:ticketing-idempotency}")
    private String idempotencyTableName;

    private final DynamoDbAsyncClient asyncClient;
    private final DynamoDbAsyncTable<TicketOrderEntity> ordersTable;
    private final TicketOrderEntityMapper orderMapper;
    private final OrderHistoryEntityMapper historyMapper;
    private final IdempotencyEntityMapper idempotencyMapper;

    public DynamoDBTicketOrderRepositoryAdapter(DynamoDbAsyncClient asyncClient, DynamoDbEnhancedAsyncClient enhancedAsyncClient, TicketOrderEntityMapper orderMapper, OrderHistoryEntityMapper historyMapper, IdempotencyEntityMapper idempotencyMapper) {
        this.asyncClient = asyncClient;
        this.ordersTable = enhancedAsyncClient.table("ticketing-orders", TableSchema.fromBean(TicketOrderEntity.class));
        this.orderMapper = orderMapper;
        this.historyMapper = historyMapper;
        this.idempotencyMapper = idempotencyMapper;
    }

    @Override
    public Mono<TicketOrder> createOrderTransaction(TicketOrder order, OrderHistory history, IdempotencyRecord idempotency) {
        TicketOrderEntity orderEntity = orderMapper.toEntity(order);
        OrderHistoryEntity historyEntity = historyMapper.toEntity(history);
        IdempotencyEntity idempotencyEntity = idempotencyMapper.toEntity(idempotency);

        Map<String, AttributeValue> idempotencyItem = new HashMap<>();
        idempotencyItem.put("idempotencyKey", AttributeValue.builder().s(idempotencyEntity.getIdempotencyKey()).build());
        idempotencyItem.put("requestHash", AttributeValue.builder().s(idempotencyEntity.getRequestHash()).build());
        idempotencyItem.put("orderId", AttributeValue.builder().s(idempotencyEntity.getOrderId()).build());
        idempotencyItem.put("ttl", AttributeValue.builder().n(idempotencyEntity.getTtl().toString()).build());

        Map<String, AttributeValue> inventoryKey = new HashMap<>();
        inventoryKey.put("eventId", AttributeValue.builder().s(order.getEventId()).build());

        Map<String, AttributeValue> inventoryValues = new HashMap<>();
        inventoryValues.put(":q", AttributeValue.builder().n(order.getQuantity().toString()).build());
        inventoryValues.put(":now", AttributeValue.builder().s(Instant.now().toString()).build());

        Map<String, AttributeValue> orderItem = buildOrderItemMap(orderEntity);
        Map<String, AttributeValue> historyItem = buildHistoryItemMap(historyEntity);

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().put(Put.builder().tableName(idempotencyTableName).item(idempotencyItem).conditionExpression("attribute_not_exists(idempotencyKey)").build()).build(),
                        TransactWriteItem.builder().update(Update.builder().tableName(inventoryTableName).key(inventoryKey).updateExpression("SET available = available - :q, reserved = reserved + :q, updatedAt = :now").expressionAttributeValues(inventoryValues).conditionExpression("available >= :q").build()).build(),
                        TransactWriteItem.builder().put(Put.builder().tableName(ordersTableName).item(orderItem).conditionExpression("attribute_not_exists(orderId)").build()).build(),
                        TransactWriteItem.builder().put(Put.builder().tableName(historyTableName).item(historyItem).conditionExpression("attribute_not_exists(orderId) AND attribute_not_exists(sequence)").build()).build()
                )
                .build();

        return Mono.fromFuture(() -> asyncClient.transactWriteItems(request))
                .thenReturn(order)
                .onErrorResume(TransactWriteItemsException.class, e -> {
                    if (e.cancellationReasons() != null && e.cancellationReasons().size() > 1) {
                        CancellationReason invReason = e.cancellationReasons().get(1);
                        if ("ConditionalCheckFailed".equals(invReason.code())) {
                            return Mono.error(new BusinessException(GeneralMessage.BUS_INSUFFICIENT_INVENTORY));
                        }
                    }
                    return Mono.error(new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
                })
                .onErrorMap(e -> !(e instanceof BusinessException), e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> createComplimentaryTransaction(TicketOrder order, OrderHistory history, IdempotencyRecord idempotency) {
        TicketOrderEntity orderEntity = orderMapper.toEntity(order);
        OrderHistoryEntity historyEntity = historyMapper.toEntity(history);
        IdempotencyEntity idempotencyEntity = idempotencyMapper.toEntity(idempotency);

        Map<String, AttributeValue> idempotencyItem = new HashMap<>();
        idempotencyItem.put("idempotencyKey", AttributeValue.builder().s(idempotencyEntity.getIdempotencyKey()).build());
        idempotencyItem.put("requestHash", AttributeValue.builder().s(idempotencyEntity.getRequestHash()).build());
        idempotencyItem.put("orderId", AttributeValue.builder().s(idempotencyEntity.getOrderId()).build());
        idempotencyItem.put("ttl", AttributeValue.builder().n(idempotencyEntity.getTtl().toString()).build());

        Map<String, AttributeValue> inventoryKey = new HashMap<>();
        inventoryKey.put("eventId", AttributeValue.builder().s(order.getEventId()).build());

        Map<String, AttributeValue> inventoryValues = new HashMap<>();
        inventoryValues.put(":q", AttributeValue.builder().n(order.getQuantity().toString()).build());
        inventoryValues.put(":now", AttributeValue.builder().s(Instant.now().toString()).build());

        Map<String, AttributeValue> orderItem = buildOrderItemMap(orderEntity);
        Map<String, AttributeValue> historyItem = buildHistoryItemMap(historyEntity);

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().put(Put.builder().tableName(idempotencyTableName).item(idempotencyItem).conditionExpression("attribute_not_exists(idempotencyKey)").build()).build(),
                        TransactWriteItem.builder().update(Update.builder().tableName(inventoryTableName).key(inventoryKey).updateExpression("SET available = available - :q, complimentary = complimentary + :q, updatedAt = :now").expressionAttributeValues(inventoryValues).conditionExpression("available >= :q").build()).build(),
                        TransactWriteItem.builder().put(Put.builder().tableName(ordersTableName).item(orderItem).conditionExpression("attribute_not_exists(orderId)").build()).build(),
                        TransactWriteItem.builder().put(Put.builder().tableName(historyTableName).item(historyItem).conditionExpression("attribute_not_exists(orderId) AND attribute_not_exists(sequence)").build()).build()
                )
                .build();

        return Mono.fromFuture(() -> asyncClient.transactWriteItems(request))
                .thenReturn(order)
                .onErrorResume(TransactWriteItemsException.class, e -> {
                    if (e.cancellationReasons() != null && e.cancellationReasons().size() > 1 && "ConditionalCheckFailed".equals(e.cancellationReasons().get(1).code())) {
                        return Mono.error(new BusinessException(GeneralMessage.BUS_INSUFFICIENT_INVENTORY));
                    }
                    return Mono.error(new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
                })
                .onErrorMap(e -> !(e instanceof BusinessException), e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> findById(String id) {
        return Mono.fromFuture(() -> ordersTable.getItem(Key.builder().partitionValue(id).build()))
                .map(orderMapper::toDomain)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> confirmOrderTransaction(TicketOrder order, String paymentRef, String traceId) {
        long newDeadline = Instant.now().getEpochSecond() + 300L;
        TicketOrder updated = order.toBuilder()
                .status(TicketStatus.PENDING_CONFIRMATION)
                .deadlineAt(newDeadline)
                .paymentReference(paymentRef)
                .version(order.getVersion() + 1)
                .updatedAt(Instant.now())
                .build();

        TicketOrderEntity entity = orderMapper.toEntity(updated);
        return Mono.fromFuture(() -> ordersTable.putItem(entity))
                .thenReturn(updated)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> cancelOrderTransaction(TicketOrder order, String reason, String traceId) {
        TicketOrder updated = order.toBuilder()
                .status(TicketStatus.AVAILABLE)
                .releaseReason(ReleaseReason.CANCELLED_BY_USER)
                .deadlineAt(null)
                .expiryShard(null)
                .version(order.getVersion() + 1)
                .updatedAt(Instant.now())
                .build();

        TicketOrderEntity entity = orderMapper.toEntity(updated);
        return Mono.fromFuture(() -> ordersTable.putItem(entity))
                .thenReturn(updated)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> finalizeOrderSaleTransaction(TicketOrder order, String traceId, String sqsMessageId) {
        TicketOrder updated = order.toBuilder()
                .status(TicketStatus.SOLD)
                .deadlineAt(null)
                .expiryShard(null)
                .version(order.getVersion() + 1)
                .updatedAt(Instant.now())
                .build();

        TicketOrderEntity entity = orderMapper.toEntity(updated);
        return Mono.fromFuture(() -> ordersTable.putItem(entity))
                .thenReturn(updated)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> rejectOrderPaymentTransaction(TicketOrder order, String failureCode, String failureDesc, String traceId) {
        TicketOrder updated = order.toBuilder()
                .status(TicketStatus.AVAILABLE)
                .releaseReason(ReleaseReason.PAYMENT_REJECTED)
                .failureCode(failureCode)
                .failureDescription(failureDesc)
                .deadlineAt(null)
                .expiryShard(null)
                .version(order.getVersion() + 1)
                .updatedAt(Instant.now())
                .build();

        TicketOrderEntity entity = orderMapper.toEntity(updated);
        return Mono.fromFuture(() -> ordersTable.putItem(entity))
                .thenReturn(updated)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> expireOrderTransaction(TicketOrder order, String traceId) {
        TicketOrder updated = order.toBuilder()
                .status(TicketStatus.AVAILABLE)
                .releaseReason(ReleaseReason.EXPIRED)
                .deadlineAt(null)
                .expiryShard(null)
                .version(order.getVersion() + 1)
                .updatedAt(Instant.now())
                .build();

        TicketOrderEntity entity = orderMapper.toEntity(updated);
        return Mono.fromFuture(() -> ordersTable.putItem(entity))
                .thenReturn(updated)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Flux<TicketOrder> findExpiredOrdersByShard(String shard, long nowEpoch) {
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(shard).build()))
                .build();

        return Flux.from(ordersTable.index("gsi-orders-deadline").query(queryRequest))
                .flatMapIterable(page -> page.items())
                .map(orderMapper::toDomain)
                .filter(o -> o.getDeadlineAt() != null && o.getDeadlineAt() <= nowEpoch)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    private Map<String, AttributeValue> buildOrderItemMap(TicketOrderEntity entity) {
        Map<String, AttributeValue> map = new HashMap<>();
        if (entity.getOrderId() != null) map.put("orderId", AttributeValue.builder().s(entity.getOrderId()).build());
        if (entity.getEventId() != null) map.put("eventId", AttributeValue.builder().s(entity.getEventId()).build());
        if (entity.getUserId() != null) map.put("userId", AttributeValue.builder().s(entity.getUserId()).build());
        if (entity.getOrderType() != null) map.put("orderType", AttributeValue.builder().s(entity.getOrderType()).build());
        if (entity.getQuantity() != null) map.put("quantity", AttributeValue.builder().n(entity.getQuantity().toString()).build());
        if (entity.getUnitPrice() != null) map.put("unitPrice", AttributeValue.builder().n(entity.getUnitPrice().toString()).build());
        if (entity.getTotalAmount() != null) map.put("totalAmount", AttributeValue.builder().n(entity.getTotalAmount().toString()).build());
        if (entity.getCurrency() != null) map.put("currency", AttributeValue.builder().s(entity.getCurrency()).build());
        if (entity.getStatus() != null) map.put("status", AttributeValue.builder().s(entity.getStatus()).build());
        if (entity.getDeadlineAt() != null) map.put("deadlineAt", AttributeValue.builder().n(entity.getDeadlineAt().toString()).build());
        if (entity.getExpiryShard() != null) map.put("expiryShard", AttributeValue.builder().s(entity.getExpiryShard()).build());
        if (entity.getTraceId() != null) map.put("traceId", AttributeValue.builder().s(entity.getTraceId()).build());
        if (entity.getVersion() != null) map.put("version", AttributeValue.builder().n(entity.getVersion().toString()).build());
        if (entity.getCreatedAt() != null) map.put("createdAt", AttributeValue.builder().s(entity.getCreatedAt()).build());
        if (entity.getUpdatedAt() != null) map.put("updatedAt", AttributeValue.builder().s(entity.getUpdatedAt()).build());
        return map;
    }

    private Map<String, AttributeValue> buildHistoryItemMap(OrderHistoryEntity entity) {
        Map<String, AttributeValue> map = new HashMap<>();
        if (entity.getOrderId() != null) map.put("orderId", AttributeValue.builder().s(entity.getOrderId()).build());
        if (entity.getSequence() != null) map.put("sequence", AttributeValue.builder().n(entity.getSequence().toString()).build());
        if (entity.getFromStatus() != null) map.put("fromStatus", AttributeValue.builder().s(entity.getFromStatus()).build());
        if (entity.getToStatus() != null) map.put("toStatus", AttributeValue.builder().s(entity.getToStatus()).build());
        if (entity.getReasonCode() != null) map.put("reasonCode", AttributeValue.builder().s(entity.getReasonCode()).build());
        if (entity.getActor() != null) map.put("actor", AttributeValue.builder().s(entity.getActor()).build());
        if (entity.getQuantity() != null) map.put("quantity", AttributeValue.builder().n(entity.getQuantity().toString()).build());
        if (entity.getTraceId() != null) map.put("traceId", AttributeValue.builder().s(entity.getTraceId()).build());
        if (entity.getOccurredAt() != null) map.put("occurredAt", AttributeValue.builder().s(entity.getOccurredAt()).build());
        return map;
    }
}
