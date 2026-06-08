package com.garmentline.operations.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class JsonSupport {

  private JsonSupport() {
  }

  public static ObjectNode objectNode(ObjectMapper objectMapper) {
    return objectMapper.createObjectNode();
  }

  public static ArrayNode arrayNode(ObjectMapper objectMapper) {
    return objectMapper.createArrayNode();
  }

  public static List<JsonNode> toList(JsonNode node) {
    List<JsonNode> items = new ArrayList<>();
    if (node == null || node.isNull()) {
      return items;
    }

    if (node.isArray()) {
      node.forEach(items::add);
      return items;
    }

    items.add(node);
    return items;
  }

  public static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  public static Integer integer(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? null : value.asInt();
  }

  public static Long longValue(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? null : value.asLong();
  }

  public static Double decimal(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? null : value.asDouble();
  }

  public static Boolean bool(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? null : value.asBoolean();
  }

  public static ArrayNode arrayField(JsonNode node, String field, ObjectMapper mapper) {
    JsonNode value = node == null ? null : node.get(field);
    if (value instanceof ArrayNode arrayNode) {
      return arrayNode;
    }
    return mapper.createArrayNode();
  }

  public static ObjectNode cloneObject(ObjectMapper mapper, JsonNode node) {
    try {
      return (ObjectNode) mapper.readTree(node.toString());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to clone JSON object.", exception);
    }
  }

  public static String stringify(JsonNode node) {
    return node == null || node.isNull() ? null : node.toString();
  }

  public static void copyFields(JsonNode source, ObjectNode target, String... fieldNames) {
    for (String fieldName : fieldNames) {
      JsonNode value = source.get(fieldName);
      if (value != null) {
        target.set(fieldName, value);
      }
    }
  }

  public static Map<String, Object> toMap(ObjectMapper objectMapper, JsonNode node) {
    return objectMapper.convertValue(node, Map.class);
  }

  public static Iterator<Map.Entry<String, JsonNode>> fields(JsonNode node) {
    return node == null ? Map.<String, JsonNode>of().entrySet().iterator() : node.fields();
  }
}
