package com.garmentline.operations.hikvision.model;

public record HikvisionDeviceInfo(
    String deviceName,
    String deviceId,
    String model,
    String serialNumber,
    String macAddress,
    String firmwareVersion
) {
}
