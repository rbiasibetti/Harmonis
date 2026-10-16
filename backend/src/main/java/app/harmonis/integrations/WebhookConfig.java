package app.harmonis.integrations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebhookConfig {

    /** Executa entregas fora da requisicao e agenda as retentativas (nao bloqueia o fluxo de aprovacao). */
    @Bean(name = "webhookScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler webhookScheduler(@Value("${harmonis.webhooks.pool-size:4}") int poolSize) {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(Math.max(1, poolSize));
        s.setThreadNamePrefix("webhook-");
        s.setRemoveOnCancelPolicy(true);
        s.setWaitForTasksToCompleteOnShutdown(false);
        s.initialize();
        return s;
    }

    /** Esperas (segundos) antes de cada retentativa; a ultima se repete se faltar. */
    @Bean(name = "webhookRetryDelays")
    public List<Integer> webhookRetryDelays(@Value("${harmonis.webhooks.retry-delays-seconds:5,30,120,300,600}") String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty())
                .map(Integer::parseInt).toList();
    }
}
