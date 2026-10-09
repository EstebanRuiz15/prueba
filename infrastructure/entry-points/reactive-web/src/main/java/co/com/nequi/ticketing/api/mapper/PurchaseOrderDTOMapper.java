package co.com.nequi.ticketing.api.mapper;

import co.com.nequi.ticketing.api.dto.OrderFailureDTO;
import co.com.nequi.ticketing.api.dto.PurchaseOrderResponseDTO;
import co.com.nequi.ticketing.model.OrderType;
import co.com.nequi.ticketing.model.ReleaseReason;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.TicketStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface PurchaseOrderDTOMapper {

    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "orderType", expression = "java(mapOrderType(order.getOrderType()))")
    @Mapping(target = "status", expression = "java(mapStatus(order.getStatus()))")
    @Mapping(target = "releaseReason", expression = "java(mapReleaseReason(order.getReleaseReason()))")
    @Mapping(target = "expiresAt", expression = "java(mapExpiresAt(order))")
    @Mapping(target = "failure", expression = "java(mapFailure(order))")
    @Mapping(target = "createdAt", expression = "java(mapInstant(order.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(mapInstant(order.getUpdatedAt()))")
    PurchaseOrderResponseDTO toResponseDTO(TicketOrder order);

    default String mapOrderType(OrderType type) {
        return type != null ? type.name() : null;
    }

    default String mapStatus(TicketStatus status) {
        return status != null ? status.name() : null;
    }

    default String mapReleaseReason(ReleaseReason reason) {
        return reason != null ? reason.name() : null;
    }

    default String mapExpiresAt(TicketOrder order) {
        if (order.getStatus() == TicketStatus.RESERVED || order.getStatus() == TicketStatus.PENDING_CONFIRMATION) {
            return order.getExpiresAt() != null ? order.getExpiresAt().toString() : (order.getDeadlineAt() != null ? Instant.ofEpochSecond(order.getDeadlineAt()).toString() : null);
        }
        return null;
    }

    default OrderFailureDTO mapFailure(TicketOrder order) {
        if (order.getFailureCode() != null) {
            return OrderFailureDTO.builder()
                    .code(order.getFailureCode())
                    .description(order.getFailureDescription())
                    .build();
        }
        return null;
    }

    default String mapInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }
}
