package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.EventEntity;
import co.com.nequi.ticketing.dynamodb.entity.InventoryEntity;
import co.com.nequi.ticketing.dynamodb.mapper.EventEntityMapper;
import co.com.nequi.ticketing.dynamodb.mapper.InventoryEntityMapper;
import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.GeneralMessage;
import co.com.nequi.ticketing.model.Inventory;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
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
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

import java.util.HashMap;
import java.util.Map;

@Repository
public class DynamoDBEventRepositoryAdapter implements EventRepository {

    @Value("${aws.dynamodb.table-events:ticketing-events}")
    private String eventsTableName;

    @Value("${aws.dynamodb.table-inventory:ticketing-inventory}")
    private String inventoryTableName;

    private final DynamoDbAsyncClient asyncClient;
    private final DynamoDbAsyncTable<EventEntity> eventsTable;
    private final EventEntityMapper eventMapper;
    private final InventoryEntityMapper inventoryMapper;

    public DynamoDBEventRepositoryAdapter(DynamoDbAsyncClient asyncClient, DynamoDbEnhancedAsyncClient enhancedAsyncClient, EventEntityMapper eventMapper, InventoryEntityMapper inventoryMapper) {
        this.asyncClient = asyncClient;
        this.eventsTable = enhancedAsyncClient.table("ticketing-events", TableSchema.fromBean(EventEntity.class));
        this.eventMapper = eventMapper;
        this.inventoryMapper = inventoryMapper;
    }

    @Override
    public Mono<Event> createWithInventory(Event event, Inventory inventory) {
        EventEntity eventEntity = eventMapper.toEntity(event);
        InventoryEntity inventoryEntity = inventoryMapper.toEntity(inventory);

        Map<String, AttributeValue> eventItem = new HashMap<>();
        eventItem.put("eventId", AttributeValue.builder().s(eventEntity.getEventId()).build());
        eventItem.put("name", AttributeValue.builder().s(eventEntity.getName()).build());
        eventItem.put("venue", AttributeValue.builder().s(eventEntity.getVenue()).build());
        eventItem.put("category", AttributeValue.builder().s(eventEntity.getCategory()).build());
        eventItem.put("eventDate", AttributeValue.builder().s(eventEntity.getEventDate()).build());
        eventItem.put("totalCapacity", AttributeValue.builder().n(eventEntity.getTotalCapacity().toString()).build());
        eventItem.put("ticketPrice", AttributeValue.builder().n(eventEntity.getTicketPrice().toString()).build());
        eventItem.put("currency", AttributeValue.builder().s(eventEntity.getCurrency()).build());
        eventItem.put("listingKey", AttributeValue.builder().s("EVENT").build());
        eventItem.put("createdAt", AttributeValue.builder().s(eventEntity.getCreatedAt()).build());
        eventItem.put("updatedAt", AttributeValue.builder().s(eventEntity.getUpdatedAt()).build());

        Map<String, AttributeValue> inventoryItem = new HashMap<>();
        inventoryItem.put("eventId", AttributeValue.builder().s(inventoryEntity.getEventId()).build());
        inventoryItem.put("available", AttributeValue.builder().n(inventoryEntity.getAvailable().toString()).build());
        inventoryItem.put("reserved", AttributeValue.builder().n(inventoryEntity.getReserved().toString()).build());
        inventoryItem.put("pendingConfirmation", AttributeValue.builder().n(inventoryEntity.getPendingConfirmation().toString()).build());
        inventoryItem.put("sold", AttributeValue.builder().n(inventoryEntity.getSold().toString()).build());
        inventoryItem.put("complimentary", AttributeValue.builder().n(inventoryEntity.getComplimentary().toString()).build());
        inventoryItem.put("totalCapacity", AttributeValue.builder().n(inventoryEntity.getTotalCapacity().toString()).build());
        inventoryItem.put("updatedAt", AttributeValue.builder().s(inventoryEntity.getUpdatedAt()).build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder()
                                .put(Put.builder().tableName(eventsTableName).item(eventItem).conditionExpression("attribute_not_exists(eventId)").build())
                                .build(),
                        TransactWriteItem.builder()
                                .put(Put.builder().tableName(inventoryTableName).item(inventoryItem).conditionExpression("attribute_not_exists(eventId)").build())
                                .build()
                )
                .build();

        return Mono.fromFuture(() -> asyncClient.transactWriteItems(request))
                .thenReturn(event)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<Event> findById(String id) {
        return Mono.fromFuture(() -> eventsTable.getItem(Key.builder().partitionValue(id).build()))
                .map(eventMapper::toDomain)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Flux<Event> findAllPaginated(int limit, String cursor) {
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue("EVENT").build()))
                .limit(limit)
                .build();

        return Flux.from(eventsTable.index("gsi-events-by-date").query(queryRequest))
                .flatMapIterable(page -> page.items())
                .map(eventMapper::toDomain)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }
}
