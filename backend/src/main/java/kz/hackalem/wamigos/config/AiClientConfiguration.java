package kz.hackalem.wamigos.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiClientConfiguration {

    @Bean
    @Qualifier("aiAnalysisRestClient")
    public RestClient aiAnalysisRestClient(AiProperties properties) {
        return createClient(properties, properties.connectTimeout(), properties.responseTimeout());
    }

    @Bean
    @Qualifier("aiHealthRestClient")
    public RestClient aiHealthRestClient(AiProperties properties) {
        return createClient(properties, properties.healthTimeout(), properties.healthTimeout());
    }

    private RestClient createClient(AiProperties properties, Duration connectTimeout, Duration responseTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(responseTimeout);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
