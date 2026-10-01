package by.bsuir.repair.runtime;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        Instant occurredAt,
        int version,
        String correlationId,
        String payload) {
}
