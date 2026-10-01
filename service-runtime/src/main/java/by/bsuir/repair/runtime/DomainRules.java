package by.bsuir.repair.runtime;

import java.math.BigDecimal;
import java.util.*;

public final class DomainRules {
    private static final Map<String, Set<String>> REQUEST_TRANSITIONS = Map.of(
            "NEW", Set.of("DIAGNOSING", "CANCELLED"), "DIAGNOSING", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("READY", "CANCELLED"), "READY", Set.of("COMPLETED"),
            "COMPLETED", Set.of(), "CANCELLED", Set.of());
    private static final Map<String, Set<String>> WORK_TRANSITIONS = Map.of(
            "DIAGNOSED", Set.of("IN_PROGRESS", "CANCELLED"), "IN_PROGRESS", Set.of("READY", "CANCELLED"),
            "READY", Set.of("COMPLETED"), "COMPLETED", Set.of(), "CANCELLED", Set.of());
    private DomainRules() {}
    public static boolean requestTransition(String from, String to) { return REQUEST_TRANSITIONS.getOrDefault(from, Set.of()).contains(to); }
    public static boolean workOrderTransition(String from, String to) { return WORK_TRANSITIONS.getOrDefault(from, Set.of()).contains(to); }
    public static BigDecimal total(Collection<BigDecimal> lines) { return lines.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public static int remainingStock(int available, int requested) {
        if (requested <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (available < requested) throw new IllegalArgumentException("insufficient stock");
        return available - requested;
    }
    public static boolean mayReadOwned(String role, UUID currentUser, UUID owner) { return !"CLIENT".equals(role) || Objects.equals(currentUser, owner); }
    public static String idempotencyKey(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        if (value.length() > 100) throw new IllegalArgumentException("idempotencyKey is too long");
        return value.trim();
    }
}
