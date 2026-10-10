package co.com.nequi.ticketing.api.dto.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PurchaseOrderResponseDTO {
    private String orderId;
    private String eventId;
    private String orderType;
    private Integer quantity;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private String expiresAt;
    private String releaseReason;
    private OrderFailureDTO failure;
    private String createdAt;
    private String updatedAt;
}
