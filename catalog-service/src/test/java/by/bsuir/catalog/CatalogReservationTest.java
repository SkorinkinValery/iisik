package by.bsuir.catalog;

import by.bsuir.repair.contract.JsonMessage;
import by.bsuir.repair.runtime.Jsons;
import by.bsuir.repair.runtime.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CatalogReservationTest {
    @Test void reservesStockAtomicallyAndReturnsSameResultForRepeatedKey() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:catalog-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__catalog.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Jsons jsons = new Jsons(new ObjectMapper().findAndRegisterModules());
        CatalogGrpcService service = new CatalogGrpcService(jdbc, mock(StringRedisTemplate.class), jsons, mock(OutboxService.class));
        UUID part = UUID.fromString("70000000-0000-0000-0000-000000000001");
        JsonMessage request = jsons.write(Map.of("workOrderId", UUID.randomUUID(), "idempotencyKey", "reserve-1",
                "items", List.of(Map.of("partId", part, "quantity", 2))));

        service.reserveParts(request, new CapturingObserver());
        service.reserveParts(request, new CapturingObserver());

        assertThat(jdbc.queryForObject("select stock_qty from spare_parts where id=?", Integer.class, part)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from part_reservations", Integer.class)).isEqualTo(1);
    }

    private static class CapturingObserver implements StreamObserver<JsonMessage> {
        @Override public void onNext(JsonMessage value) {}
        @Override public void onError(Throwable error) { throw new AssertionError(error); }
        @Override public void onCompleted() {}
    }
}
