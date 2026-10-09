package co.com.nequi.ticketing.api.helper;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ResponseEnvelope<T> {
    private String statusCode;
    private String description;
    private Object data;

    public static <T> ResponseEnvelope<T> success(String statusCode, String description, T data) {
        return ResponseEnvelope.<T>builder()
                .statusCode(statusCode)
                .description(description)
                .data(data != null ? data : Collections.emptyMap())
                .build();
    }

    public static <T> ResponseEnvelope<T> error(String statusCode, String description) {
        return ResponseEnvelope.<T>builder()
                .statusCode(statusCode)
                .description(description)
                .data(Collections.emptyMap())
                .build();
    }
}
