package com.tradex.unionnorth.setup;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** The same allowlisted field contract drives API validation and accessible forms. */
@Component
public class SetupCatalog {
    public record Field(
            String key,
            String label,
            String type,
            boolean required,
            String reference,
            List<String> options,
            @JsonInclude(JsonInclude.Include.NON_NULL) String parentKey,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, List<String>> optionsByParent,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> optionLabels) {
        public Field(
                String key,
                String label,
                String type,
                boolean required,
                String reference,
                List<String> options) {
            this(key, label, type, required, reference, options, null, null, null);
        }

        public Field(
                String key,
                String label,
                String type,
                boolean required,
                String reference,
                List<String> options,
                String parentKey,
                Map<String, List<String>> optionsByParent) {
            this(key, label, type, required, reference, options, parentKey, optionsByParent, null);
        }

        List<String> optionsFor(Map<String, Object> data) {
            if (optionsByParent == null) return options;
            return optionsByParent.getOrDefault(
                    Objects.toString(data.get(parentKey), "").trim(), List.of());
        }
    }

    public record Definition(
            String kind, String label, String group, boolean financial, List<Field> fields) {}

    static Field f(String key, String label, String type, boolean required) {
        return new Field(key, label, type, required, null, List.of());
    }

    static Field ref(String key, String label, String kind, boolean required) {
        return new Field(key, label, "reference", required, kind, List.of());
    }

    static Field ref(String key, String label, String kind, boolean required, String parentKey) {
        return new Field(key, label, "reference", required, kind, List.of(), parentKey, null, null);
    }

    static Field choice(String key, String label, boolean required, String... options) {
        return new Field(key, label, "select", required, null, List.of(options));
    }

    static Field labeledChoice(
            String key, String label, boolean required, String... valuesAndLabels) {
        if (valuesAndLabels.length % 2 != 0)
            throw new IllegalArgumentException("Choice values and labels must be paired.");
        List<String> options = new ArrayList<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (int index = 0; index < valuesAndLabels.length; index += 2) {
            options.add(valuesAndLabels[index]);
            labels.put(valuesAndLabels[index], valuesAndLabels[index + 1]);
        }
        return new Field(
                key, label, "select", required, null, List.copyOf(options), null, null, labels);
    }

    private final List<Definition> definitions =
            List.of(
                    new Definition(
                            "COMPANY",
                            "Companies",
                            "Organization",
                            false,
                            List.of(f("registrationNumber", "Registration number", "text", false))),
                    new Definition(
                            "LOCATION",
                            "Locations",
                            "Organization",
                            false,
                            List.of(
                                    ref("companyId", "Company", "COMPANY", true),
                                    f("addressLine", "Address", "text", false))),
                    new Definition(
                            "DEPARTMENT",
                            "Departments",
                            "Organization",
                            false,
                            List.of(
                                    ref("companyId", "Company", "COMPANY", true),
                                    ref("locationId", "Location", "LOCATION", false))),
                    new Definition(
                            "SECTION",
                            "Sections",
                            "Organization",
                            false,
                            List.of(ref("companyId", "Company", "COMPANY", true))),
                    new Definition(
                            "TEAM",
                            "Teams",
                            "Organization",
                            false,
                            List.of(ref("companyId", "Company", "COMPANY", true))),
                    new Definition(
                            "LINE",
                            "Production lines",
                            "Organization",
                            false,
                            List.of(ref("departmentId", "Department", "DEPARTMENT", true))),
                    new Definition(
                            "DESIGNATION",
                            "Designations",
                            "Organization",
                            false,
                            List.of(ref("companyId", "Company", "COMPANY", true))),
                    new Definition(
                            "COST_CENTRE",
                            "Cost centres",
                            "Organization",
                            false,
                            List.of(ref("companyId", "Company", "COMPANY", true))),
                    new Definition(
                            "GRADE",
                            "Grades",
                            "Organization",
                            false,
                            List.of(ref("companyId", "Company", "COMPANY", true))),
                    new Definition(
                            "SHIFT",
                            "Shift references",
                            "Organization",
                            false,
                            List.of(
                                    f("sourceName", "LineMatrix shift name", "text", true),
                                    f("startTime", "Start time", "time", true),
                                    f("endTime", "End time", "time", true),
                                    f("breakMinutes", "Unpaid break (minutes)", "number", true),
                                    f("graceMinutes", "Grace period (minutes)", "number", true),
                                    f("overnight", "Ends the following day", "boolean", false),
                                    f("notApplicable", "Not applicable shift", "boolean", false),
                                    f(
                                            "scheduleVerified",
                                            "Schedule reviewed for payroll",
                                            "boolean",
                                            false))),
                    new Definition(
                            "COMPONENT",
                            "Salary components",
                            "Salary",
                            true,
                            List.of(
                                    choice(
                                            "category",
                                            "Category",
                                            true,
                                            "EARNING",
                                            "DEDUCTION",
                                            "EMPLOYER_CONTRIBUTION"),
                                    choice(
                                            "method",
                                            "Calculation method",
                                            true,
                                            "FIXED",
                                            "PERCENTAGE",
                                            "INPUT",
                                            "FORMULA"),
                                    f("value", "Default amount / percentage", "number", true),
                                    f(
                                            "formula",
                                            "Formula (FORMULA method only)",
                                            "textarea",
                                            false),
                                    choice(
                                            "eligibility",
                                            "Employee eligibility",
                                            false,
                                            "ALL",
                                            "OVERTIME",
                                            "HOLIDAY",
                                            "ATTENDANCE_BONUS",
                                            "OVERTIME_ALLOWANCE"),
                                    choice(
                                            "basis",
                                            "Percentage basis",
                                            false,
                                            "BASIC",
                                            "GROSS",
                                            "TAXABLE",
                                            "STATUTORY"),
                                    f("taxable", "Taxable earning", "boolean", false),
                                    f(
                                            "statutoryEligible",
                                            "Included in statutory earnings basis",
                                            "boolean",
                                            false))),
                    new Definition(
                            "CALCULATION_POLICY",
                            "Calculation policies",
                            "Salary",
                            true,
                            List.of(
                                    ref("companyId", "Company", "COMPANY", true),
                                    choice(
                                            "monthlyProration",
                                            "Monthly basic salary",
                                            true,
                                            "FULL",
                                            "PAID_DAYS"),
                                    choice(
                                            "dayDivisor",
                                            "Monthly day divisor",
                                            true,
                                            "CALENDAR_DAYS",
                                            "FIXED"),
                                    f(
                                            "fixedDays",
                                            "Fixed day divisor (when FIXED)",
                                            "number",
                                            false),
                                    choice(
                                            "braTreatment",
                                            "BRA amounts",
                                            true,
                                            "FIXED",
                                            "PRORATE_WITH_BASIC"),
                                    choice(
                                            "rounding",
                                            "Round each line to two decimals",
                                            true,
                                            "HALF_UP",
                                            "HALF_EVEN",
                                            "DOWN"),
                                    f(
                                            "basicTaxable",
                                            "Basic pay and BRA are taxable",
                                            "boolean",
                                            false),
                                    f(
                                            "basicStatutoryEligible",
                                            "Basic pay and BRA enter statutory earnings",
                                            "boolean",
                                            false),
                                    f(
                                            "authorityReference",
                                            "Approved company policy reference",
                                            "text",
                                            true))),
                    new Definition(
                            "SALARY_STRUCTURE",
                            "Salary structures",
                            "Salary",
                            true,
                            List.of(
                                    ref("companyId", "Company", "COMPANY", true),
                                    choice(
                                            "payBasis",
                                            "Pay basis",
                                            true,
                                            "MONTHLY",
                                            "DAILY",
                                            "HOURLY"),
                                    f("components", "Recurring components", "components", false))),
                    new Definition(
                            "ALLOWANCE",
                            "Allowance types",
                            "Salary",
                            true,
                            List.of(
                                    ref("componentId", "Earning component", "COMPONENT", true),
                                    f(
                                            "eligibility",
                                            "Eligibility / company policy",
                                            "textarea",
                                            true))),
                    new Definition(
                            "DEDUCTION",
                            "Deduction types",
                            "Salary",
                            true,
                            List.of(
                                    ref("componentId", "Deduction component", "COMPONENT", true),
                                    f(
                                            "authorization",
                                            "Authorization / company policy",
                                            "textarea",
                                            true))),
                    new Definition(
                            "LOAN_TYPE",
                            "Loan types",
                            "Salary",
                            true,
                            List.of(
                                    choice(
                                            "interestMethod",
                                            "Interest method",
                                            true,
                                            "NONE",
                                            "FLAT",
                                            "REDUCING_BALANCE"),
                                    f("annualRate", "Annual interest rate (%)", "number", true),
                                    f("maxAmount", "Maximum principal", "number", true),
                                    f(
                                            "maxInstallments",
                                            "Maximum monthly installments",
                                            "number",
                                            true),
                                    f(
                                            "policy",
                                            "Eligibility / recovery policy",
                                            "textarea",
                                            true))),
                    new Definition(
                            "STATUTORY_RULE",
                            "Statutory rules",
                            "Rules & payment",
                            true,
                            List.of(
                                    f(
                                            "authorityReference",
                                            "Approved policy / authority reference",
                                            "text",
                                            true),
                                    choice(
                                            "method",
                                            "Method",
                                            true,
                                            "FIXED",
                                            "PERCENTAGE",
                                            "TIERED"),
                                    choice(
                                            "basis",
                                            "Earnings basis",
                                            true,
                                            "BASIC",
                                            "GROSS",
                                            "TAXABLE",
                                            "STATUTORY"),
                                    f(
                                            "taxRule",
                                            "Tax rule (skip for tax-exempt employees)",
                                            "boolean",
                                            false),
                                    f("employeeRate", "Employee amount / rate", "number", true),
                                    f("employerRate", "Employer amount / rate", "number", true),
                                    f(
                                            "requiresMemberNumber",
                                            "Member number required",
                                            "boolean",
                                            false),
                                    f(
                                            "bands",
                                            "Progressive bands (upper bound, rate %)",
                                            "bands",
                                            false))),
                    new Definition(
                            "BANK",
                            "Banks",
                            "Rules & payment",
                            false,
                            List.of(
                                    f("bankCode", "Bank clearing code", "text", true),
                                    f(
                                            "branchOptional",
                                            "Allow bank payment without a branch",
                                            "boolean",
                                            false))),
                    new Definition(
                            "BANK_BRANCH",
                            "Bank branches",
                            "Rules & payment",
                            false,
                            List.of(
                                    ref("bankId", "Bank", "BANK", true),
                                    f("branchCode", "Branch code", "text", true))),
                    new Definition(
                            "PAYMENT_METHOD",
                            "Payment methods",
                            "Rules & payment",
                            false,
                            List.of(choice("mode", "Payment mode", true, "BANK", "CASH"))),
                    new Definition(
                            "PAY_PERIOD",
                            "Payroll periods",
                            "Rules & payment",
                            true,
                            List.of(
                                    ref("companyId", "Company", "COMPANY", true),
                                    f("startDate", "Period starts", "date", true),
                                    f("endDate", "Period ends", "date", true),
                                    f("cutoffDate", "Input cutoff", "date", true),
                                    f("paymentDate", "Payment date", "date", true))));

    public List<Definition> definitions() {
        return definitions;
    }

    public Definition definition(String kind) {
        return definitions.stream()
                .filter(d -> d.kind().equals(kind))
                .findFirst()
                .orElseThrow(SetupException::missing);
    }

    public boolean organization(String kind) {
        return Set.of("COMPANY", "LOCATION", "DEPARTMENT", "LINE", "DESIGNATION").contains(kind);
    }

    public List<Field> generalFields() {
        return List.of(
                f("firstName", "First name", "text", true),
                f("middleName", "Middle name", "text", false),
                f("lastName", "Last name", "text", true),
                f("fullName", "Full name", "text", false),
                f("nameWithInitials", "Name with initials", "text", false),
                f("initials", "Initials", "text", false),
                labeledChoice(
                        "title",
                        "Title",
                        false,
                        "NOT_APPLICABLE",
                        "N/A",
                        "MR",
                        "Mr.",
                        "MRS",
                        "Mrs.",
                        "MISS",
                        "Miss.",
                        "MS",
                        "Ms.",
                        "MX",
                        "Mx.",
                        "DR",
                        "Dr.",
                        "REV",
                        "Rev."),
                f("displayName", "Call / display name", "text", true),
                f("identityNumber", "NIC / passport", "text", true),
                f("passportNumber", "Passport number", "text", false),
                f("barcode", "Barcode / card number", "text", false),
                f("email", "Email", "email", false),
                f("phone", "Phone", "text", false),
                f("epfNumber", "EPF / member number", "text", false),
                f("dateOfBirth", "Date of birth", "date", false),
                labeledChoice("gender", "Gender", false, "FEMALE", "Female", "MALE", "Male"),
                labeledChoice(
                        "maritalStatus",
                        "Marital status",
                        false,
                        "UNKNOWN",
                        "Unknown",
                        "UNMARRIED",
                        "Unmarried",
                        "MARRIED",
                        "Married",
                        "DIVORCED",
                        "Divorced",
                        "WIDOWED",
                        "Widowed"),
                labeledChoice(
                        "bloodGroup",
                        "Blood group",
                        false,
                        "UNKNOWN",
                        "Unknown",
                        "A_POSITIVE",
                        "A+",
                        "A_NEGATIVE",
                        "A-",
                        "B_POSITIVE",
                        "B+",
                        "B_NEGATIVE",
                        "B-",
                        "O_POSITIVE",
                        "O+",
                        "O_NEGATIVE",
                        "O-",
                        "AB_POSITIVE",
                        "AB+",
                        "AB_NEGATIVE",
                        "AB-"),
                choice(
                        "religion",
                        "Religion",
                        false,
                        "Buddhism",
                        "Christianity",
                        "Hinduism",
                        "Islam"),
                choice(
                        "race",
                        "Race / ethnicity",
                        false,
                        "N/A",
                        "Sinhalese",
                        "Sri Lankan Tamils",
                        "Sri Lankan Moors",
                        "Indian Tamils",
                        "Sri Lanka Malays",
                        "Burghers",
                        "Indian Moors",
                        "Others"),
                f("residentialAddress", "Residential address", "textarea", false),
                f("permanentAddress", "Permanent address", "textarea", false),
                choice(
                        "district",
                        "District",
                        false,
                        "Ampara",
                        "Anuradhapura",
                        "Badulla",
                        "Batticaloa",
                        "Colombo",
                        "Galle",
                        "Gampaha",
                        "Hambantota",
                        "Jaffna",
                        "Kalutara",
                        "Kandy",
                        "Kegalle",
                        "Kilinochchi",
                        "Kurunegala",
                        "Mannar",
                        "Matale",
                        "Matara",
                        "Monaragala",
                        "Mullaitivu",
                        "Nuwara Eliya",
                        "Polonnaruwa",
                        "Puttalam",
                        "Ratnapura",
                        "Trincomalee",
                        "Vavuniya"),
                new Field(
                        "electorate",
                        "Electorate",
                        "select",
                        false,
                        null,
                        SriLankaElectorates.ALL,
                        "district",
                        SriLankaElectorates.BY_DISTRICT),
                f("busRoute", "Bus route", "text", false),
                f("distanceKm", "Travel distance (km)", "number", false),
                ref("companyId", "Company", "COMPANY", true),
                ref("locationId", "Location", "LOCATION", true),
                ref("departmentId", "Department", "DEPARTMENT", true),
                ref("designationId", "Designation", "DESIGNATION", true),
                ref("productionLineId", "Production line", "LINE", false),
                ref("costCentreId", "Cost centre", "COST_CENTRE", false),
                ref("gradeId", "Grade", "GRADE", false),
                ref("sectionId", "Section", "SECTION", false),
                ref("teamId", "Team", "TEAM", false),
                labeledChoice(
                        "level",
                        "Employee level",
                        false,
                        "NOT_APPLICABLE",
                        "N/A",
                        "LEVEL_1",
                        "Level 1",
                        "LEVEL_2",
                        "Level 2",
                        "LEVEL_3",
                        "Level 3",
                        "LEVEL_4",
                        "Level 4",
                        "LEVEL_5",
                        "Level 5",
                        "LEVEL_6",
                        "Level 6",
                        "LEVEL_7",
                        "Level 7",
                        "LEVEL_8",
                        "Level 8"),
                ref("shiftId", "Shift reference", "SHIFT", true),
                choice(
                        "employmentType",
                        "Employment type",
                        true,
                        "PERMANENT",
                        "CONTRACT",
                        "TEMPORARY",
                        "INTERN"),
                labeledChoice(
                        "employeeCategory",
                        "Payroll category",
                        true,
                        "WORKER",
                        "Team Member",
                        "STAFF",
                        "Staff",
                        "MANAGEMENT",
                        "Manager",
                        "EXECUTIVE",
                        "Executive"),
                choice("employeeStatus", "Employee status", false, "DIRECT", "INDIRECT"),
                choice(
                        "recruitmentType",
                        "Recruitment type / scheme",
                        false,
                        "MITHURU SAVIYA",
                        "LTO Replacement",
                        "DIRECT RECRUITMENT",
                        "OTHERS"),
                f("recruitmentReference", "Recruitment reference number", "text", false),
                f("contractEmployee", "Contract", "boolean", false),
                f("onProbation", "Probation", "boolean", false),
                f("joinedDate", "Joined date", "date", true),
                f("groupJoinedDate", "Group joined date", "date", false),
                f("effectiveFrom", "Setup effective from", "date", true),
                f("contractEndDate", "Contract end date", "date", false),
                f("probationEndDate", "Probation end date", "date", false),
                choice(
                        "contractDurationMonths",
                        "Contract duration (months)",
                        false,
                        "0",
                        "1",
                        "2",
                        "3",
                        "4",
                        "5",
                        "6",
                        "7",
                        "8",
                        "9",
                        "10",
                        "11",
                        "12"),
                f("occupationCode", "Occupation code", "text", false),
                f("drivingLicenseNumber", "Driving licence number", "text", false),
                f("drivingLicenseExpiry", "Driving licence expiry", "date", false),
                f("emergencyName", "Primary emergency contact name", "text", false),
                f("emergencyPhone", "Primary emergency contact phone", "text", false),
                f("emergencyRelationship", "Primary emergency relationship", "text", false),
                f(
                        "emergencyResidentialAddress",
                        "Primary emergency residential address",
                        "textarea",
                        false),
                f(
                        "emergencyResidentialPhone",
                        "Primary emergency residential phone",
                        "text",
                        false),
                f("emergencyOfficeAddress", "Primary emergency office address", "textarea", false),
                f("emergencyOfficePhone", "Primary emergency office phone", "text", false),
                f("secondaryEmergencyName", "Secondary emergency contact name", "text", false),
                f("secondaryEmergencyPhone", "Secondary emergency contact phone", "text", false),
                f(
                        "secondaryEmergencyRelationship",
                        "Secondary emergency relationship",
                        "text",
                        false),
                f(
                        "secondaryEmergencyResidentialAddress",
                        "Secondary emergency residential address",
                        "textarea",
                        false),
                f(
                        "secondaryEmergencyResidentialPhone",
                        "Secondary emergency residential phone",
                        "text",
                        false),
                f(
                        "secondaryEmergencyOfficeAddress",
                        "Secondary emergency office address",
                        "textarea",
                        false),
                f(
                        "secondaryEmergencyOfficePhone",
                        "Secondary emergency office phone",
                        "text",
                        false),
                f("hodApprovalRequired", "HOD approval required", "boolean", false),
                f("hodEmployeeNumber", "Head of department employee number", "text", false),
                f(
                        "alternateHod1EmployeeNumber",
                        "Alternative HOD 1 employee number",
                        "text",
                        false),
                f(
                        "alternateHod2EmployeeNumber",
                        "Alternative HOD 2 employee number",
                        "text",
                        false),
                f("previousEmploymentDetails", "Previous employment details", "textarea", false));
    }

    public List<Field> financialFields() {
        return List.of(
                choice("payBasis", "Pay basis", true, "MONTHLY", "DAILY", "HOURLY"),
                f("basicSalary", "Basic salary / daily or hourly rate (LKR)", "number", true),
                f("bra1", "Budgetary relief allowance 1 (LKR)", "number", false),
                f("bra2", "Budgetary relief allowance 2 (LKR)", "number", false),
                f("totalBasicSalary", "Total basic salary (LKR)", "number", false),
                labeledChoice(
                        "salaryAct",
                        "Salary act / wage board",
                        false,
                        "NOT_APPLICABLE",
                        "N/A",
                        "SHOP_AND_OFFICE",
                        "Shop and Office",
                        "WAGES_BOARD",
                        "Wages Board"),
                ref("salaryStructureId", "Salary structure", "SALARY_STRUCTURE", true),
                ref("payPeriodId", "Initial payroll period", "PAY_PERIOD", true),
                f("components", "Employee component overrides", "components", false),
                ref("paymentMethodId", "Payment method", "PAYMENT_METHOD", true),
                ref("bankId", "Bank", "BANK", false),
                ref("branchId", "Branch", "BANK_BRANCH", false, "bankId"),
                f("accountHolder", "Account holder", "text", false),
                f("accountNumber", "Bank account number", "text", false),
                f("taxExempted", "Tax exempted", "boolean", false),
                f("holidayPaymentEligible", "Holiday payment eligible", "boolean", false),
                f("collectiveAgreementSigned", "Collective agreement signed", "boolean", false),
                f("overtimePaid", "Overtime paid", "boolean", false),
                f("overtimeAllowanceEligible", "Overtime allowance eligible", "boolean", false),
                f("attendanceBonusEligible", "Attendance bonus eligible", "boolean", false),
                choice(
                        "employeeBonusCategory",
                        "Employee bonus category",
                        false,
                        "6000",
                        "10000",
                        "8000"),
                f("statutoryRules", "Applicable statutory rules", "rules", false),
                f(
                        "statutoryExemptionReason",
                        "Reason if no statutory rules apply",
                        "textarea",
                        false),
                f(
                        "statutoryConfirmed",
                        "Statutory applicability checked against approved company rules",
                        "boolean",
                        true));
    }

    public Map<String, Object> validate(
            List<Field> fields, Map<String, Object> raw, boolean required) {
        if (raw == null) throw new SetupException("Form data is required.");
        Set<String> allowed = new HashSet<>();
        fields.forEach(f -> allowed.add(f.key()));
        if (!allowed.containsAll(raw.keySet()))
            throw new SetupException("The form contains unsupported fields.");
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Field field : fields) {
            Object value = raw.get(field.key());
            if (value instanceof String text) value = text.trim();
            if (value == null || "".equals(value)) {
                if (required && field.required())
                    throw new SetupException(field.label() + " is required.");
                continue;
            }
            try {
                switch (field.type()) {
                    case "number" -> {
                        BigDecimal n = new BigDecimal(value.toString());
                        if (n.signum() < 0
                                || n.compareTo(new BigDecimal("9999999999")) > 0
                                || n.scale() > 4) throw new IllegalArgumentException();
                        value = n;
                    }
                    case "date" -> value = LocalDate.parse(value.toString()).toString();
                    case "time" -> value = LocalTime.parse(value.toString()).toString();
                    case "reference" -> value = UUID.fromString(value.toString()).toString();
                    case "select" -> {
                        if (!field.optionsFor(raw).contains(value))
                            throw new IllegalArgumentException();
                    }
                    case "boolean" -> {
                        if (!(value instanceof Boolean)) throw new IllegalArgumentException();
                    }
                    case "components", "rules", "bands" -> {
                        if (!(value instanceof List<?> list) || list.size() > 100)
                            throw new IllegalArgumentException();
                    }
                    default -> {
                        int max =
                                field.type().equals("textarea")
                                        ? 500
                                        : switch (field.key()) {
                                            case "displayName", "fullName", "nameWithInitials" ->
                                                    240;
                                            case "firstName", "middleName", "lastName" -> 120;
                                            case "identityNumber",
                                                            "passportNumber",
                                                            "registrationNumber" ->
                                                    80;
                                            case "email" -> 180;
                                            case "phone",
                                                            "emergencyPhone",
                                                            "emergencyResidentialPhone",
                                                            "emergencyOfficePhone",
                                                            "secondaryEmergencyPhone",
                                                            "secondaryEmergencyResidentialPhone",
                                                            "secondaryEmergencyOfficePhone",
                                                            "accountNumber" ->
                                                    40;
                                            case "epfNumber" -> 50;
                                            default -> 160;
                                        };
                        if (!(value instanceof String s) || s.length() > max)
                            throw new IllegalArgumentException();
                        if (field.type().equals("email")
                                && !s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                            throw new IllegalArgumentException();
                    }
                }
            } catch (RuntimeException ex) {
                throw new SetupException("Enter a valid " + field.label().toLowerCase() + ".");
            }
            clean.put(field.key(), value);
        }
        return clean;
    }
}
