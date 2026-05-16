package com.garmentline.operations.hikvision.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record HikvisionRecognitionEvent(
    String id,
    String serialNo,
    String employeeNo,
    String devicePersonName,
    String matchedEmployeeId,
    String matchedEmployeeName,
    String matchedDepartment,
    String matchStatus,
    OffsetDateTime eventTime,
    OffsetDateTime receivedAt,
    String verifyMode,
    String attendanceStatus,
    String accessDecision,
    String pictureUrl,
    String visibleLightPicUrl,
    String thermalPicUrl,
    Double temperature,
    String mask,
    Integer major,
    Integer minor,
    Map<String, Object> rawPayload
) {
}
