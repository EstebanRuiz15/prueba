package co.com.nequi.ticketing.api.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityDTO {
    private Integer totalCapacity;
    private Integer available;
    private Integer reserved;
    private Integer pendingConfirmation;
    private Integer sold;
    private Integer complimentary;
    private String asOf;
}
