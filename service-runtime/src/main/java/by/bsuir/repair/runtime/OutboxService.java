package by.bsuir.repair.runtime;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxService {
    private final JdbcTemplate jdbc; private final Jsons jsons;
    public OutboxService(JdbcTemplate jdbc, Jsons jsons){this.jdbc=jdbc;this.jsons=jsons;}
    public UUID enqueue(String topic,String type,UUID aggregateId,Object payload){
        UUID eventId=UUID.randomUUID(); String correlation=MDC.get("correlationId"); if(correlation==null) correlation=UUID.randomUUID().toString();
        var envelope=new EventEnvelope(eventId,type,aggregateId,Instant.now(),1,correlation,jsons.writeString(payload));
        jdbc.update("insert into outbox_events(event_id,topic,aggregate_id,event_type,payload,created_at,published) values(?,?,?,?,?,?,false)",eventId,topic,aggregateId,type,jsons.writeString(envelope),java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        return eventId;
    }
}

