package co.com.nequi.ticketing.api.filter;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TraceAndSecurityFilter implements WebFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String ROLE_HEADER = "X-Role";
    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        String finalTraceId = traceId;
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        String role = exchange.getRequest().getHeaders().getFirst(ROLE_HEADER);
        if (role == null || role.isBlank()) {
            String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (auth != null && auth.contains("ADMIN")) {
                role = "ADMIN";
            } else {
                role = "USER";
            }
        }

        String userId = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            userId = "usr-default";
        }

        exchange.getAttributes().put(TRACE_ID_HEADER, finalTraceId);
        exchange.getAttributes().put(ROLE_HEADER, role);
        exchange.getAttributes().put(USER_ID_HEADER, userId);

        return chain.filter(exchange);
    }
}
