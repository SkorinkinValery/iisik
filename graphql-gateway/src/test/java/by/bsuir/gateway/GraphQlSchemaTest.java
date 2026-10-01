package by.bsuir.gateway;

import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.execution.GraphQlSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "services.identity=localhost:19101", "services.device=localhost:19102",
        "services.request=localhost:19103", "services.work-order=localhost:19104",
        "services.catalog=localhost:19105", "services.payment=localhost:19106",
        "services.notification=localhost:19107", "services.analytics=localhost:19108"
})
class GraphQlSchemaTest {
    @Autowired GraphQlSource source;

    @Test void exposesEveryRequiredQueryAndMutation() {
        GraphQLSchema schema = source.schema();
        Set<String> queries = schema.getQueryType().getFieldDefinitions().stream().map(x -> x.getName()).collect(Collectors.toSet());
        Set<String> mutations = schema.getMutationType().getFieldDefinitions().stream().map(x -> x.getName()).collect(Collectors.toSet());
        assertThat(queries).contains("me", "device", "myDevices", "repairRequest", "repairRequests", "workOrder",
                "workOrders", "serviceCatalog", "spareParts", "payments", "notifications", "repairAnalytics", "users");
        assertThat(mutations).contains("register", "login", "logout", "upsertDevice", "createRepairRequest",
                "diagnoseAndCreateWorkOrder", "addServiceToWorkOrder", "addPartToWorkOrder", "recalculateWorkOrder",
                "estimateRepairCost", "changeRepairRequestStatus", "changeWorkOrderStatus", "registerPayment",
                "markNotificationAsRead", "upsertService", "upsertSparePart", "upsertDeviceCategory",
                "changeUserRole", "setUserEnabled");
    }
}
