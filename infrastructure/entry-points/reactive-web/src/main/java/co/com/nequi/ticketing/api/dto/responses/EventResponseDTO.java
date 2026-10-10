package co.com.nequi.ticketing.api.dto;

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
public class EventResponseDTO {
    private String eventId;
    private String name;
    private String eventDate;
    private String venue;
    private String category;
    private BigDecimal ticketPrice;
    private String currency;
    private Integer totalCapacity;
    private AvailabilityDTO availability;
}
