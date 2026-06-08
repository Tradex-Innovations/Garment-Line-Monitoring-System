package com.garmentline.operations.zkteco.model;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.OffsetDateTime;

public record ZktecoStatus(
    ArrayNode devices,
    int deviceCount,
    OffsetDateTime serverTime,
    String mode
) {
}
