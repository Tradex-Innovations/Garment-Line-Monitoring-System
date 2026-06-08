package com.garmentline.operations.zkteco.model;

public record ZktecoAdmsResponse(
    String deviceSerialNo,
    String tableName,
    int receivedRows,
    int acceptedRows
) {
  public String acknowledgement() {
    return acceptedRows > 0 ? "OK: " + acceptedRows : "OK";
  }
}
