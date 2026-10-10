package co.com.nequi.ticketing.api.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistoryResponseDTO {
    private Long sequence;
    private String fromStatus;
    private String toStatus;
    private String reasonCode;
    private String actor;
    private String traceId;
    private String occurredAt;
}
