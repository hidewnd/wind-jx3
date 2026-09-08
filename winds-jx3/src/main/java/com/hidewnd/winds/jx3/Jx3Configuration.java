package com.hidewnd.winds.jx3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "winds.jx3", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(Jx3Configuration.Properties.class)
public class Jx3Configuration {
    @Bean public OfficialClient jx3OfficialClient(ObjectMapper mapper) { return new OfficialClient(mapper); }
    @Bean public Jx3Store jx3Store(MongoTemplate mongo, ObjectMapper mapper) { return new Jx3Store(mongo, mapper); }
    @Bean public PatchMonitor jx3PatchMonitor(OfficialClient client, Jx3Store store, ApplicationEventPublisher publisher, ObjectMapper mapper) {
        return new PatchMonitor(client, store, publisher, mapper, Clock.systemUTC());
    }
    @Bean public ServerMonitor jx3ServerMonitor(OfficialClient client, Jx3Store store, ApplicationEventPublisher publisher, ObjectMapper mapper) {
        return new ServerMonitor(client, store, publisher, mapper, Clock.systemUTC(), new ServerMonitor.TcpProbe());
    }
    @Bean public Jx3Polling jx3Polling(OfficialClient client, Jx3Store store, ApplicationEventPublisher publisher, ObjectMapper mapper,
                                      PatchMonitor patches, ServerMonitor servers, Properties properties) {
        return new Jx3Polling(new ArticleMonitor(false, client, store, publisher, mapper, Clock.systemUTC()),
                new ArticleMonitor(true, client, store, publisher, mapper, Clock.systemUTC()), patches, servers, properties);
    }

    @ConfigurationProperties("winds.jx3")
    public record Properties(@DefaultValue("120s") Duration newsInterval, @DefaultValue("120s") Duration maintenanceInterval,
                             @DefaultValue("30s") Duration serverInterval, @DefaultValue("30s") Duration patchInterval) {
        public Properties {
            for (Duration interval : new Duration[]{newsInterval, maintenanceInterval, serverInterval, patchInterval})
                if (interval == null || interval.compareTo(Duration.ofSeconds(1)) < 0) throw new IllegalArgumentException("剑三轮询间隔不得小于一秒");
        }
    }
}
