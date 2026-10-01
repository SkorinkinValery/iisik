package by.bsuir.repair.runtime;

import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private static final Logger log=LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc; private final KafkaTemplate<String,String> kafka;
    public OutboxPublisher(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka){this.jdbc=jdbc;this.kafka=kafka;}
    @Scheduled(fixedDelayString="${integration.outbox.delay:1000}")
    public void publish(){
        try {
            var rows=jdbc.query("select event_id,topic,aggregate_id,payload from outbox_events where published=false order by created_at limit 100",(rs,n)->new Row(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getString(4)));
            for(var row:rows){ try { kafka.send(row.topic,row.aggregate.toString(),row.payload).get(3, TimeUnit.SECONDS); jdbc.update("update outbox_events set published=true,published_at=? where event_id=?", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC),row.id); log.info("outbox_published eventId={} topic={}",row.id,row.topic); } catch(Exception e){ log.warn("outbox_publish_failed eventId={} topic={} error={}",row.id,row.topic,e.getClass().getSimpleName()); } }
        } catch(Exception ignored) { }
    }
    private record Row(UUID id,String topic,UUID aggregate,String payload){}
}

