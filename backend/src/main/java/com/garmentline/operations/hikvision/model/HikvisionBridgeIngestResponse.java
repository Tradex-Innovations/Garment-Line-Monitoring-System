package com.garmentline.operations.hikvision.model;

public record HikvisionBridgeIngestResponse(
    int receivedEvents,
    int acceptedEvents,
    HikvisionStatus status
) {
}
