package com.tradex.unionnorth.employee.linematrix;

import java.util.Map;

public record LineMatrixEmployee(
        String employeeNumber, String displayName, String phone,
        String epfNumber, String department, String designation,
        String employmentStatus, boolean active,
        String sourceId, String joinedDate, String shiftName, String sourceCategory,
        String photoUrl, String productionLineCode, String productionLineName,
        String team, String grade, Map<String, Object> payrollDetails) {
    public boolean isPermanent() {
        return sourceCategory != null && "permanent".equalsIgnoreCase(sourceCategory.trim());
    }

    public LineMatrixEmployee(String employeeNumber, String displayName, String phone, String epfNumber,
            String department, String designation, String employmentStatus, boolean active,
            String sourceId, String joinedDate, String shiftName, String sourceCategory,
            String photoUrl, String productionLineCode, String productionLineName) {
        this(employeeNumber, displayName, phone, epfNumber, department, designation, employmentStatus,
                active, sourceId, joinedDate, shiftName, sourceCategory, photoUrl, productionLineCode,
                productionLineName, null, null, Map.of());
    }
    public LineMatrixEmployee(String employeeNumber, String displayName, String phone, String epfNumber,
            String department, String designation, String employmentStatus, boolean active,
            String sourceId, String joinedDate, String shiftName, String sourceCategory) {
        this(employeeNumber, displayName, phone, epfNumber, department, designation, employmentStatus,
                active, sourceId, joinedDate, shiftName, sourceCategory, null, null, null, null, null, Map.of());
    }
    public LineMatrixEmployee(String employeeNumber, String displayName, String phone, String epfNumber,
            String department, String designation, String employmentStatus, boolean active) {
        this(employeeNumber, displayName, phone, epfNumber, department, designation, employmentStatus,
                active, null, null, null, null, null, null, null, null, null, Map.of());
    }
}
