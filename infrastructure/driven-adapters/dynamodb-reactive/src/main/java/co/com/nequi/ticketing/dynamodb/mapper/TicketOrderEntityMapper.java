package co.com.nequi.ticketing.dynamodb.mapper;

import co.com.nequi.ticketing.dynamodb.entity.TicketOrderEntity;
import co.com.nequi.ticketing.model.OrderType;
import co.com.nequi.ticketing.model.ReleaseReason;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface TicketOrderEntityMapper {

    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "orderType", expression = "java(mapOrderType(order.getOrderType()))")
    @Mapping(target = "status", expression = "java(mapStatus(order.getStatus()))")
    @Mapping(target = "releaseReason", expression = "java(mapReleaseReason(order.getReleaseReason()))")
    @Mapping(target = "createdAt", expression = "java(mapInstant(order.getCreatedAt()))")
    @Mapping(target = "expiresAt", expression = "java(mapInstant(order.getExpiresAt()))")
    @Mapping(target = "updatedAt", expression = "java(mapInstant(order.getUpdatedAt()))")
    TicketOrderEntity toEntity(TicketOrder order);

    @Mapping(target = "id", source = "orderId")
    @Mapping(target = "orderType", expression = "java(parseOrderType(entity.getOrderType()))")
    @Mapping(target = "status", expression = "java(parseStatus(entity.getStatus()))")
    @Mapping(target = "releaseReason", expression = "java(parseReleaseReason(entity.getReleaseReason()))")
    @Mapping(target = "createdAt", expression = "java(parseInstant(entity.getCreatedAt()))")
    @Mapping(target = "expiresAt", expression = "java(parseInstant(entity.getExpiresAt()))")
    @Mapping(target = "updatedAt", expression = "java(parseInstant(entity.getUpdatedAt()))")
    TicketOrder toDomain(TicketOrderEntity entity);

    default String mapOrderType(OrderType type) {
        return type != null ? type.name() : null;
    }

    default OrderType parseOrderType(String typeStr) {
        return typeStr != null ? OrderType.valueOf(typeStr) : null;
    }

    default String mapStatus(TicketStatus status) {
        return status != null ? status.name() : null;
    }

    default TicketStatus parseStatus(String statusStr) {
        return statusStr != null ? TicketStatus.valueOf(statusStr) : null;
    }

    default String mapReleaseReason(ReleaseReason reason) {
        return reason != null ? reason.name() : null;
    }

    default ReleaseReason parseReleaseReason(String reasonStr) {
        return reasonStr != null ? ReleaseReason.valueOf(reasonStr) : null;
    }

    default String mapInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    default Instant parseInstant(String instantStr) {
        return instantStr != null ? Instant.parse(instantStr) : null;
    }
}
