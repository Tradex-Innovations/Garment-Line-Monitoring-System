package com.tradex.unionnorth.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OvertimeSetupValidationTest {
    private final SetupCatalog catalog = new SetupCatalog();
    private final SetupStore store = mock(SetupStore.class);
    private final MasterDataService masters = new MasterDataService(store, catalog);
    private final LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);

    @Test
    void validatesAnApprovedOvertimeTypeWithoutSeedingAnyRate() {
        var definition = catalog.definition("COMPONENT");
        var valid =
                catalog.validate(
                        definition.fields(),
                        Map.of(
                                "category", "EARNING",
                                "method", "OVERTIME",
                                "eligibility", "OVERTIME",
                                "value", "1.5",
                                "hoursDivisor", "200"),
                        true);
        masters.validateRules("COMPONENT", valid, effectiveFrom);
        assertThat(valid).containsKeys("value", "hoursDivisor");

        assertThatThrownBy(
                        () ->
                                masters.validateRules(
                                        "COMPONENT",
                                        catalog.validate(
                                                definition.fields(),
                                                Map.of(
                                                        "category", "EARNING",
                                                        "method", "OVERTIME",
                                                        "eligibility", "OVERTIME",
                                                        "value", "0",
                                                        "hoursDivisor", "200"),
                                                true),
                                        effectiveFrom))
                .hasMessageContaining("positive multiplier");
        assertThatThrownBy(
                        () ->
                                masters.validateRules(
                                        "COMPONENT",
                                        catalog.validate(
                                                definition.fields(),
                                                Map.of(
                                                        "category", "DEDUCTION",
                                                        "method", "OVERTIME",
                                                        "eligibility", "OVERTIME",
                                                        "value", "1.5",
                                                        "hoursDivisor", "200"),
                                                true),
                                        effectiveFrom))
                .hasMessageContaining("must be earnings");
    }

    @Test
    void rejectsRecurringValueOverridesForOvertimeTypes() {
        var id = UUID.randomUUID();
        when(store.get("COMPONENT", id, effectiveFrom))
                .thenReturn(
                        new SetupStore.Item(
                                id,
                                "COMPONENT",
                                "TEST-OT",
                                "Synthetic overtime",
                                true,
                                0,
                                effectiveFrom,
                                Map.of(
                                        "category", "EARNING",
                                        "method", "OVERTIME",
                                        "eligibility", "OVERTIME",
                                        "value", "1.5",
                                        "hoursDivisor", "200")));
        assertThatThrownBy(
                        () ->
                                masters.validateComponents(
                                        List.of(Map.of("componentId", id.toString(), "value", "2")),
                                        effectiveFrom,
                                        true))
                .hasMessageContaining("overtime components cannot have recurring value overrides");
    }
}
