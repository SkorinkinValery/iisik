package by.bsuir.notification;

import by.bsuir.repair.runtime.EventEnvelope;
import by.bsuir.repair.runtime.Jsons;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeduplicationTest {
    @Test void ignoresRepeatedKafkaEventAfterRestartSafeDatabaseCheck() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:notifications-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__notifications.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Jsons jsons = new Jsons(new ObjectMapper().findAndRegisterModules());
        NotificationGrpcService service = new NotificationGrpcService(jdbc, jsons);
        UUID eventId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        String payload = jsons.writeString(Map.of("clientId", clientId, "currentStatus", "READY"));
        String event = jsons.writeString(new EventEnvelope(eventId, "RepairRequestStatusChanged", UUID.randomUUID(), Instant.now(), 1, "test-correlation", payload));

        service.consume(event);
        service.consume(event);

        assertThat(jdbc.queryForObject("select count(*) from notifications", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from processed_events", Integer.class)).isEqualTo(1);
    }
}
