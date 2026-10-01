package by.bsuir.gateway;

import by.bsuir.repair.contract.SessionRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.*;

@Controller
public class GraphQlController {
    private final GrpcClients grpc;
    private final CurrentUser current;

    public GraphQlController(GrpcClients grpc, CurrentUser current) {
        this.grpc = grpc;
        this.current = current;
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public Map<String,Object> me() {
        return grpc.map(grpc.call(() -> grpc.deadline(grpc.identity).getUserAccess(grpc.message(Map.of("id", current.get().userId())))));
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public Map<String,Object> device(@Argument String id) {
        return grpc.map(grpc.call(() -> grpc.deadline(grpc.device).getDevice(grpc.message(current.enrich(Map.of("id", id))))));
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> myDevices() {
        return grpc.list(grpc.call(() -> grpc.deadline(grpc.device).listClientDevices(grpc.message(current.enrich(Map.of())))));
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public Map<String,Object> repairRequest(@Argument String id) {
        return grpc.map(grpc.call(() -> grpc.deadline(grpc.requests).getRequest(grpc.message(current.enrich(Map.of("id", id))))));
    }

    @SchemaMapping(typeName = "RepairRequest", field = "device") @PreAuthorize("isAuthenticated()")
    public Map<String,Object> requestDevice(Map<String,Object> request) {
        return device(Objects.toString(request.get("deviceId")));
    }

    @SchemaMapping(typeName = "RepairRequest", field = "workOrder") @PreAuthorize("isAuthenticated()")
    public Map<String,Object> requestWorkOrder(Map<String,Object> request) {
        var page = workOrders(null);
        @SuppressWarnings("unchecked") var orders = (List<Map<String,Object>>) page.getOrDefault("items", List.of());
        return orders.stream().filter(x -> Objects.equals(Objects.toString(x.get("repairRequestId")), Objects.toString(request.get("id"))))
                .findFirst().orElse(null);
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public Map<String,Object> repairRequests(@Argument Map<String,Object> filter, @Argument Map<String,Object> sort,
                                             @Argument Integer page, @Argument Integer size) {
        var in = new LinkedHashMap<String,Object>();
        if (filter != null) in.putAll(filter);
        if (sort != null) {
            in.put("sortField", sort.get("field"));
            in.put("sortDirection", sort.get("direction"));
        }
        in.put("page", page == null ? 0 : page);
        in.put("size", size == null ? 20 : size);
        return grpc.map(grpc.call(() -> grpc.deadline(grpc.requests).listRequests(grpc.message(current.enrich(in)))));
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public Map<String,Object> workOrder(@Argument String id) {
        return grpc.map(grpc.call(() -> grpc.deadline(grpc.work).getWorkOrder(grpc.message(current.enrich(Map.of("id", id))))));
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public Map<String,Object> workOrders(@Argument Map<String,Object> sort) {
        var in = new LinkedHashMap<String,Object>();
        if (sort != null) {
            in.put("sortField", sort.get("field"));
            in.put("sortDirection", sort.get("direction"));
        }
        return grpc.map(grpc.call(() -> grpc.deadline(grpc.work).listWorkOrders(grpc.message(current.enrich(in)))));
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> serviceCatalog(@Argument Boolean activeOnly) {
        return grpc.list(grpc.call(() -> grpc.deadline(grpc.catalog).listServices(grpc.message(Map.of("activeOnly", activeOnly == null || activeOnly)))));
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> spareParts(@Argument Boolean inStockOnly) {
        return grpc.list(grpc.call(() -> grpc.deadline(grpc.catalog).listParts(grpc.message(Map.of("inStockOnly", inStockOnly == null || inStockOnly)))));
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> payments(@Argument String workOrderId) {
        return grpc.list(grpc.call(() -> grpc.deadline(grpc.payment).listPayments(grpc.message(current.enrich(Map.of("workOrderId", workOrderId))))));
    }

    @QueryMapping @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> notifications(@Argument Boolean unreadOnly) {
        return grpc.list(grpc.call(() -> grpc.deadline(grpc.notification).listNotifications(grpc.message(current.enrich(Map.of("unreadOnly", Boolean.TRUE.equals(unreadOnly)))))));
    }

    @QueryMapping @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> repairAnalytics(@Argument Map<String,Object> period) {
        return grpc.map(grpc.call(() -> grpc.deadline(grpc.analytics).getAnalytics(grpc.message(period))));
    }

    @QueryMapping @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> users() {
        return grpc.list(grpc.call(() -> grpc.deadline(grpc.identity).listUsers(grpc.message(Map.of()))));
    }

    @MutationMapping @PreAuthorize("permitAll()")
    public Map<String,Object> register(@Argument Map<String,Object> input) {
        return grpc.map(grpc.call(() -> grpc.deadline(grpc.identity).register(grpc.message(input))));
    }

    @MutationMapping @PreAuthorize("permitAll()")
    public Map<String,Object> login(@Argument Map<String,Object> input) {
        return grpc.map(grpc.call(() -> grpc.deadline(grpc.identity).login(grpc.message(input))));
    }

    @MutationMapping @PreAuthorize("isAuthenticated()")
    public boolean logout() {
        grpc.call(() -> grpc.deadline(grpc.identity).logout(SessionRequest.newBuilder().setSessionId(current.get().sessionId()).build()));
        return true;
    }

    @MutationMapping @PreAuthorize("hasAnyRole('CLIENT','ADMIN')")
    public Map<String,Object> upsertDevice(@Argument Map<String,Object> input) { return callMap(grpc.device, "upsertDevice", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasRole('CLIENT')")
    public Map<String,Object> createRepairRequest(@Argument Map<String,Object> input) { return callMap(grpc.requests, "createRequest", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public Map<String,Object> diagnoseAndCreateWorkOrder(@Argument Map<String,Object> input) { return callMap(grpc.work, "diagnoseAndCreate", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public Map<String,Object> addServiceToWorkOrder(@Argument Map<String,Object> input) { return callMap(grpc.work, "addService", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public Map<String,Object> addPartToWorkOrder(@Argument Map<String,Object> input) { return callMap(grpc.work, "addPart", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public Map<String,Object> recalculateWorkOrder(@Argument String workOrderId) { return callMap(grpc.work, "recalculate", current.enrich(Map.of("workOrderId", workOrderId))); }

    @MutationMapping @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public Map<String,Object> estimateRepairCost(@Argument Map<String,Object> input) {
        String requestId = input.get("requestId").toString();
        var page = callMap(grpc.work, "listWorkOrders", current.enrich(Map.of()));
        @SuppressWarnings("unchecked") var orders = (List<Map<String,Object>>) page.getOrDefault("items", List.of());
        var order = orders.stream().filter(x -> requestId.equals(Objects.toString(x.get("repairRequestId")))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Work order for repair request was not found"));
        return recalculateWorkOrder(order.get("id").toString());
    }

    @MutationMapping @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public Map<String,Object> changeRepairRequestStatus(@Argument Map<String,Object> input) { return callMap(grpc.requests, "changeStatus", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public Map<String,Object> changeWorkOrderStatus(@Argument Map<String,Object> input) { return callMap(grpc.work, "changeStatus", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasAnyRole('CLIENT','ADMIN')")
    public Map<String,Object> registerPayment(@Argument Map<String,Object> input) { return callMap(grpc.payment, "registerPayment", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasRole('CLIENT')")
    public Map<String,Object> markNotificationAsRead(@Argument String id) { return callMap(grpc.notification, "markRead", current.enrich(Map.of("id", id))); }

    @MutationMapping @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> upsertService(@Argument Map<String,Object> input) { return callMap(grpc.catalog, "upsertService", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> upsertSparePart(@Argument Map<String,Object> input) { return callMap(grpc.catalog, "upsertPart", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> upsertDeviceCategory(@Argument Map<String,Object> input) { return callMap(grpc.device, "upsertCategory", current.enrich(input)); }

    @MutationMapping @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> changeUserRole(@Argument String userId, @Argument String role) { return callMap(grpc.identity, "changeRole", Map.of("userId", userId, "role", role)); }

    @MutationMapping @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> setUserEnabled(@Argument String userId, @Argument boolean enabled) { return callMap(grpc.identity, "setEnabled", Map.of("userId", userId, "enabled", enabled)); }

    private Map<String,Object> callMap(Object unused, String operation, Map<String,Object> input) {
        var request = grpc.message(input);
        return switch (operation) {
            case "upsertDevice" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.device).upsertDevice(request)));
            case "createRequest" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.requests).createRequest(request)));
            case "diagnoseAndCreate" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.work).diagnoseAndCreate(request)));
            case "addService" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.work).addService(request)));
            case "addPart" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.work).addPart(request)));
            case "recalculate" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.work).recalculate(request)));
            case "listWorkOrders" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.work).listWorkOrders(request)));
            case "changeStatus" -> input.containsKey("requestId")
                    ? grpc.map(grpc.call(() -> grpc.deadline(grpc.requests).changeStatus(request)))
                    : grpc.map(grpc.call(() -> grpc.deadline(grpc.work).changeStatus(request)));
            case "registerPayment" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.payment).registerPayment(request)));
            case "markRead" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.notification).markRead(request)));
            case "upsertService" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.catalog).upsertService(request)));
            case "upsertPart" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.catalog).upsertPart(request)));
            case "upsertCategory" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.device).upsertCategory(request)));
            case "changeRole" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.identity).changeRole(request)));
            case "setEnabled" -> grpc.map(grpc.call(() -> grpc.deadline(grpc.identity).setEnabled(request)));
            default -> throw new IllegalArgumentException("Unsupported operation " + operation);
        };
    }
}
