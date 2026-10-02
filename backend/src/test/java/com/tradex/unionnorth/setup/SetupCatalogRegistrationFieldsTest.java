package com.tradex.unionnorth.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SetupCatalogRegistrationFieldsTest {

    private final SetupCatalog catalog = new SetupCatalog();

    @Test
    void religionAndRaceUseTheProvidedOptionalDropdownChoices() {
        var expected = Map.of(
                "religion", List.of("Buddhism", "Christianity", "Hinduism", "Islam"),
                "race", List.of("N/A", "Sinhalese", "Sri Lankan Tamils", "Sri Lankan Moors",
                        "Indian Tamils", "Sri Lanka Malays", "Burghers", "Indian Moors", "Others"));
        expected.forEach((key, choices) -> {
            var field = catalog.generalFields().stream()
                    .filter(item -> item.key().equals(key)).findFirst().orElseThrow();
            assertThat(field.type()).isEqualTo("select");
            assertThat(field.required()).isFalse();
            assertThat(field.options()).containsExactlyElementsOf(choices);
            for (String choice : choices) {
                assertThat(catalog.validate(List.of(field), Map.of(key, choice), false))
                        .containsEntry(key, choice);
            }
            assertThat(catalog.validate(List.of(field), Map.of(key, ""), true)).isEmpty();
            assertThatThrownBy(() -> catalog.validate(List.of(field), Map.of(key, "Unlisted value"), false))
                    .isInstanceOf(SetupException.class);
        });
    }

    @Test
    void electorateChoicesCoverAllDistrictsAndValidateTheSelectedDistrict() {
        var electorate = catalog.generalFields().stream()
                .filter(field -> field.key().equals("electorate")).findFirst().orElseThrow();
        var district = catalog.generalFields().stream()
                .filter(field -> field.key().equals("district")).findFirst().orElseThrow();
        assertThat(electorate.type()).isEqualTo("select");
        assertThat(electorate.required()).isFalse();
        assertThat(electorate.parentKey()).isEqualTo("district");
        assertThat(electorate.options()).hasSize(160).doesNotHaveDuplicates().isSorted();
        assertThat(electorate.optionsByParent()).containsOnlyKeys(district.options());
        electorate.optionsByParent().forEach((name, choices) -> {
            assertThat(choices).isNotEmpty().doesNotHaveDuplicates().isSorted();
            for (String choice : choices) {
                assertThat(catalog.validate(List.of(district, electorate),
                        Map.of("district", name, "electorate", choice), false))
                        .containsEntry("electorate", choice);
            }
        });
        assertThat(electorate.optionsByParent().get("Kilinochchi")).containsExactly("Kilinochchi");
        assertThat(electorate.optionsByParent().get("Jaffna")).hasSize(10).doesNotContain("Kilinochchi");
        for (String northernDistrict : List.of("Mannar", "Mullaitivu", "Vavuniya")) {
            assertThat(electorate.optionsByParent().get(northernDistrict)).containsExactly(northernDistrict);
        }
        assertThat(catalog.validate(List.of(district, electorate), Map.of(), false)).isEmpty();
        for (var invalid : List.of(
                Map.<String, Object>of("electorate", "Negombo"),
                Map.<String, Object>of("district", "Colombo", "electorate", "Negombo"),
                Map.<String, Object>of("district", "Gampaha", "electorate", "Unknown electorate"))) {
            assertThatThrownBy(() -> catalog.validate(List.of(district, electorate), invalid, false))
                    .isInstanceOf(SetupException.class).hasMessage("Enter a valid electorate.");
        }
    }

    @Test
    void districtIsAnOptionalDropdownWithAllSriLankanDistricts() {
        var district = catalog.generalFields().stream()
                .filter(field -> field.key().equals("district")).findFirst().orElseThrow();
        assertThat(district.type()).isEqualTo("select");
        assertThat(district.required()).isFalse();
        assertThat(district.options()).containsExactly(
                "Ampara", "Anuradhapura", "Badulla", "Batticaloa", "Colombo",
                "Galle", "Gampaha", "Hambantota", "Jaffna", "Kalutara", "Kandy",
                "Kegalle", "Kilinochchi", "Kurunegala", "Mannar", "Matale", "Matara",
                "Monaragala", "Mullaitivu", "Nuwara Eliya", "Polonnaruwa", "Puttalam",
                "Ratnapura", "Trincomalee", "Vavuniya");
        for (String name : district.options()) {
            assertThat(catalog.validate(List.of(district), Map.of("district", name), false))
                    .containsEntry("district", name);
        }
        assertThat(catalog.validate(List.of(district), Map.of("district", ""), true)).isEmpty();
        assertThatThrownBy(() -> catalog.validate(List.of(district), Map.of("district", "Invalid district"), false))
                .isInstanceOf(SetupException.class).hasMessage("Enter a valid district.");
    }

    @Test
    void exposesTheCompleteEmployeeRegistrationDataset() {
        Set<String> general = catalog.generalFields().stream()
                .map(SetupCatalog.Field::key)
                .collect(Collectors.toSet());
        Set<String> financial = catalog.financialFields().stream()
                .map(SetupCatalog.Field::key)
                .collect(Collectors.toSet());

        assertThat(general)
                .contains(
                        "fullName",
                        "nameWithInitials",
                        "barcode",
                        "identityNumber",
                        "bloodGroup",
                        "residentialAddress",
                        "permanentAddress",
                        "district",
                        "companyId",
                        "locationId",
                        "costCentreId",
                        "departmentId",
                        "sectionId",
                        "designationId",
                        "teamId",
                        "gradeId",
                        "shiftId",
                        "joinedDate",
                        "groupJoinedDate",
                        "recruitmentType",
                        "employeeStatus",
                        "contractEmployee",
                        "onProbation",
                        "occupationCode",
                        "drivingLicenseNumber",
                        "emergencyName",
                        "secondaryEmergencyName",
                        "hodApprovalRequired",
                        "hodEmployeeNumber",
                        "previousEmploymentDetails");
        assertThat(financial)
                .contains(
                        "basicSalary",
                        "bra1",
                        "bra2",
                        "totalBasicSalary",
                        "payBasis",
                        "paymentMethodId",
                        "bankId",
                        "branchId",
                        "accountNumber",
                        "taxExempted",
                        "holidayPaymentEligible",
                        "overtimePaid",
                        "overtimeAllowanceEligible",
                        "attendanceBonusEligible",
                        "statutoryRules");
    }

    @Test
    void exposesCollectedLegacyChoicesWithStableStoredValuesAndLabels() {
        assertLabeledChoices(
                "title",
                List.of("NOT_APPLICABLE", "MR", "MRS", "MISS", "MS", "MX", "DR", "REV"),
                List.of("N/A", "Mr.", "Mrs.", "Miss.", "Ms.", "Mx.", "Dr.", "Rev."));
        assertLabeledChoices(
                "maritalStatus",
                List.of("UNKNOWN", "UNMARRIED", "MARRIED", "DIVORCED", "WIDOWED"),
                List.of("Unknown", "Unmarried", "Married", "Divorced", "Widowed"));
        assertLabeledChoices(
                "employeeCategory",
                List.of("WORKER", "STAFF", "MANAGEMENT", "EXECUTIVE"),
                List.of("Team Member", "Staff", "Manager", "Executive"));
        assertLabeledChoices(
                "salaryAct",
                List.of("NOT_APPLICABLE", "SHOP_AND_OFFICE", "WAGES_BOARD"),
                List.of("N/A", "Shop and Office", "Wages Board"));

        var recruitment = field("recruitmentType");
        assertThat(recruitment.options())
                .containsExactly(
                        "MITHURU SAVIYA", "LTO Replacement", "DIRECT RECRUITMENT", "OTHERS");
        assertThat(field("contractDurationMonths").options())
                .containsExactly(
                        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        assertThat(financialField("employeeBonusCategory").options())
                .containsExactly("6000", "10000", "8000");
    }

    @Test
    void modelsCollectedSectionsTeamsAndDependentBankBranchesAsReferences() {
        assertThat(catalog.definitions().stream().map(SetupCatalog.Definition::kind))
                .contains("SECTION", "TEAM", "BANK", "BANK_BRANCH");
        assertThat(field("sectionId").reference()).isEqualTo("SECTION");
        assertThat(field("teamId").reference()).isEqualTo("TEAM");
        var branch = financialField("branchId");
        assertThat(branch.reference()).isEqualTo("BANK_BRANCH");
        assertThat(branch.parentKey()).isEqualTo("bankId");
        var bank = catalog.definition("BANK");
        assertThat(bank.fields().stream().map(SetupCatalog.Field::key))
                .contains("bankCode", "branchOptional");
    }

    private SetupCatalog.Field field(String key) {
        return catalog.generalFields().stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private SetupCatalog.Field financialField(String key) {
        return catalog.financialFields().stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private void assertLabeledChoices(String key, List<String> values, List<String> labels) {
        var field = catalog.generalFields().stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElseGet(() -> financialField(key));
        assertThat(field.options()).containsExactlyElementsOf(values);
        assertThat(values.stream().map(field.optionLabels()::get).toList())
                .containsExactlyElementsOf(labels);
    }
}
