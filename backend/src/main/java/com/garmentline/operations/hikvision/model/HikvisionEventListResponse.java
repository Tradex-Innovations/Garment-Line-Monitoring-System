package com.garmentline.operations.hikvision.model;

import java.util.List;

public record HikvisionEventListResponse(
    List<HikvisionRecognitionEvent> events,
    HikvisionStatus status
) {
}
