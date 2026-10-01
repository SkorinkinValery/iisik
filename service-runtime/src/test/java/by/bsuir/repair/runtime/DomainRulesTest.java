package by.bsuir.repair.runtime;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class DomainRulesTest {
    @Test void validatesRequestStatusTransitions() {
        assertThat(DomainRules.requestTransition("NEW", "DIAGNOSING")).isTrue();
        assertThat(DomainRules.requestTransition("NEW", "COMPLETED")).isFalse();
    }
    @Test void validatesWorkOrderStatusTransitions() {
        assertThat(DomainRules.workOrderTransition("DIAGNOSED", "IN_PROGRESS")).isTrue();
        assertThat(DomainRules.workOrderTransition("COMPLETED", "IN_PROGRESS")).isFalse();
    }
    @Test void calculatesCostFromActualLines() {
        assertThat(DomainRules.total(List.of(new BigDecimal("25.00"), new BigDecimal("240.00")))).isEqualByComparingTo("265.00");
    }
    @Test void preventsNegativeStock() {
        assertThat(DomainRules.remainingStock(10, 3)).isEqualTo(7);
        assertThatThrownBy(() -> DomainRules.remainingStock(2, 3)).hasMessage("insufficient stock");
    }
    @Test void restrictsClientToOwnedDataAndValidatesIdempotency() {
        UUID owner = UUID.randomUUID();
        assertThat(DomainRules.mayReadOwned("CLIENT", owner, owner)).isTrue();
        assertThat(DomainRules.mayReadOwned("CLIENT", UUID.randomUUID(), owner)).isFalse();
        assertThat(DomainRules.mayReadOwned("ADMIN", UUID.randomUUID(), owner)).isTrue();
        assertThat(DomainRules.idempotencyKey(" payment-1 ")).isEqualTo("payment-1");
        assertThatThrownBy(() -> DomainRules.idempotencyKey(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
