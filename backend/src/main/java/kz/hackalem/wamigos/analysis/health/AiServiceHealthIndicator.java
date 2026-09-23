package kz.hackalem.wamigos.analysis.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiServiceHealthIndicator implements HealthIndicator {

    private final RestClient restClient;

    public AiServiceHealthIndicator(@Qualifier("aiHealthRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Health health() {
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            return Health.up().build();
        } catch (RestClientException exception) {
            return Health.down()
                    .withDetail("reason", "AI service is unavailable")
                    .build();
        }
    }
}
