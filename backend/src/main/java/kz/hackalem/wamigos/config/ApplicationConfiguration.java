package kz.hackalem.wamigos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.tika.Tika;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableConfigurationProperties({
        StorageProperties.class,
        UploadProperties.class,
        QueueProperties.class,
        ProcessingProperties.class,
        CleanupProperties.class,
        AiProperties.class,
        CorsProperties.class,
        ExportProperties.class
})
public class ApplicationConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Tika tika() {
        return new Tika();
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("meeting-scheduler-");
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    @Bean
    public OpenAPI meetingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Wamigos Meeting API")
                .version("v1")
                .description("Одноразовая обработка совещаний без аккаунтов"));
    }
}
