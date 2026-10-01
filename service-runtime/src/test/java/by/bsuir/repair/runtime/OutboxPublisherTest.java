package by.bsuir.repair.runtime;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    @Test void marksEventPublishedOnlyAfterKafkaAcknowledgement() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:outbox;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table outbox_events(event_id uuid primary key,topic varchar(120),aggregate_id uuid,payload text,created_at timestamp with time zone,published boolean,published_at timestamp with time zone)");
        UUID event = UUID.randomUUID();
        jdbc.update("insert into outbox_events values(?,?,?,?,?,false,null)", event, EventTopics.REQUEST, UUID.randomUUID(), "{}", Instant.now());
        @SuppressWarnings("unchecked") KafkaTemplate<String,String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        new OutboxPublisher(jdbc, kafka).publish();

        assertThat(jdbc.queryForObject("select published from outbox_events where event_id=?", Boolean.class, event)).isTrue();
        verify(kafka).send(eq(EventTopics.REQUEST), anyString(), eq("{}"));
    }
}
