package com.garmentline.operations.hikvision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.garmentline.operations.config.HikvisionProperties;
import com.garmentline.operations.hikvision.model.HikvisionCameraConfig;
import com.garmentline.operations.hikvision.model.HikvisionConfigRequest;
import com.garmentline.operations.hikvision.model.HikvisionDeviceInfo;
import com.garmentline.operations.hikvision.model.HikvisionEventListResponse;
import com.garmentline.operations.hikvision.model.HikvisionRecognitionEvent;
import com.garmentline.operations.hikvision.model.HikvisionStatus;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.RoleGuard;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.garmentline.operations.support.ApiException;
import com.garmentline.operations.support.JsonSupport;
import jakarta.annotation.PreDestroy;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

@Service
public class HikvisionService {

  private static final int MAX_EVENTS = 500;
  private static final int DEFAULT_POLL_INTERVAL_SECONDS = 3;
  private static final int DEFAULT_LOOKBACK_MINUTES = 60;
  private static final DateTimeFormatter CAMERA_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

  private final HikvisionIsapiClient isapiClient;
  private final SupabaseAdminClient supabaseAdminClient;
  private final RoleGuard roleGuard;
  private final ObjectMapper objectMapper;
  private final AtomicReference<HikvisionCameraConfig> activeConfig;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final Object eventLock = new Object();
  private final LinkedList<HikvisionRecognitionEvent> events = new LinkedList<>();
  private final Set<String> seenEventIds = ConcurrentHashMap.newKeySet();
  private final Map<String, EmployeeMatch> employeeCache = new ConcurrentHashMap<>();

  private volatile ScheduledFuture<?> pollingTask;
  private volatile OffsetDateTime lastPollAt;
  private volatile OffsetDateTime lastSuccessAt;
  private volatile String lastError;
  private volatile HikvisionDeviceInfo deviceInfo;

  public HikvisionService(
      HikvisionIsapiClient isapiClient,
      SupabaseAdminClient supabaseAdminClient,
      RoleGuard roleGuard,
      ObjectMapper objectMapper,
      HikvisionProperties properties) {
    this.isapiClient = isapiClient;
    this.supabaseAdminClient = supabaseAdminClient;
    this.roleGuard = roleGuard;
    this.objectMapper = objectMapper;
    this.activeConfig = new AtomicReference<>(configFromProperties(properties));
  }

  public HikvisionStatus getStatus(AuthenticatedUser user) {
    requireAccess(user);
    return status();
  }

  public HikvisionStatus configure(AuthenticatedUser user, HikvisionConfigRequest request) {
    requireManage(user);
    HikvisionCameraConfig existing = activeConfig.get();
    String password =
        request.password() == null || request.password().isBlank()
            ? existing == null ? "" : existing.password()
            : request.password();

    HikvisionCameraConfig nextConfig =
        new HikvisionCameraConfig(
            normalizeBaseUrl(request.baseUrl()),
            request.username().trim(),
            password,
            request.pollIntervalSeconds() == null
                ? DEFAULT_POLL_INTERVAL_SECONDS
                : request.pollIntervalSeconds(),
            request.lookbackMinutes() == null ? DEFAULT_LOOKBACK_MINUTES : request.lookbackMinutes());

    activeConfig.set(nextConfig);
    deviceInfo = null;
    lastError = null;
    return testConnection(user);
  }

  public HikvisionStatus testConnection(AuthenticatedUser user) {
    requireAccess(user);
    HikvisionCameraConfig config = requireConfig();
    try {
      deviceInfo = fetchDeviceInfo(config);
      lastError = null;
      lastSuccessAt = OffsetDateTime.now();
    } catch (RuntimeException exception) {
      lastError = exception.getMessage();
      throw exception;
    }
    return status();
  }

  public HikvisionStatus startPolling(AuthenticatedUser user) {
    requireManage(user);
    HikvisionCameraConfig config = requireConfig();
    stopPollingInternal();
    pollingTask =
        scheduler.scheduleWithFixedDelay(
            this::safePoll,
            0,
            Math.max(1, config.pollIntervalSeconds()),
            TimeUnit.SECONDS);
    return status();
  }

  public HikvisionStatus stopPolling(AuthenticatedUser user) {
    requireManage(user);
    stopPollingInternal();
    return status();
  }

  public HikvisionEventListResponse pollNow(AuthenticatedUser user) {
    requireManage(user);
    safePoll();
    return listEvents(user, 80);
  }

  public HikvisionEventListResponse listEvents(AuthenticatedUser user, int limit) {
    requireAccess(user);
    int safeLimit = Math.max(1, Math.min(limit, MAX_EVENTS));
    List<HikvisionRecognitionEvent> snapshot;
    synchronized (eventLock) {
      snapshot = events.stream().limit(safeLimit).toList();
    }
    return new HikvisionEventListResponse(snapshot, status());
  }

  @PreDestroy
  public void shutdown() {
    stopPollingInternal();
    scheduler.shutdownNow();
  }

  private void safePoll() {
    try {
      pollInternal();
    } catch (RuntimeException exception) {
      lastError = exception.getMessage();
    }
  }

  private void pollInternal() {
    HikvisionCameraConfig config = requireConfig();
    lastPollAt = OffsetDateTime.now();
    List<HikvisionRecognitionEvent> nextEvents = fetchAccessEvents(config);
    List<HikvisionRecognitionEvent> newEvents = new ArrayList<>();

    synchronized (eventLock) {
      for (HikvisionRecognitionEvent event :
          nextEvents.stream()
              .sorted(Comparator.comparing(HikvisionRecognitionEvent::eventTime).reversed())
              .toList()) {
        if (seenEventIds.add(event.id())) {
          events.addFirst(event);
          newEvents.add(event);
        }
      }

      while (events.size() > MAX_EVENTS) {
        HikvisionRecognitionEvent removed = events.removeLast();
        seenEventIds.remove(removed.id());
      }
    }

    persistEvents(newEvents);

    if (!nextEvents.isEmpty() || newEvents.isEmpty()) {
      lastSuccessAt = OffsetDateTime.now();
      lastError = null;
    }
  }

  private void persistEvents(List<HikvisionRecognitionEvent> newEvents) {
    if (newEvents.isEmpty()) {
      return;
    }

    List<Map<String, Object>> rows =
        newEvents.stream().map(this::toPersistenceRow).toList();

    try {
      supabaseAdminClient.upsertMany("hikvision_face_events", rows, "camera_event_id");
    } catch (RuntimeException ignored) {
      // The live feed should still work before the optional Supabase migration is applied.
    }
  }

  private Map<String, Object> toPersistenceRow(HikvisionRecognitionEvent event) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("camera_event_id", event.id());
    row.put("camera_serial_no", event.serialNo());
    row.put("employee_code", event.employeeNo());
    row.put("employee_id", event.matchedEmployeeId());
    row.put("device_person_name", event.devicePersonName());
    row.put("matched_employee_name", event.matchedEmployeeName());
    row.put("matched_department", event.matchedDepartment());
    row.put("match_status", event.matchStatus());
    row.put("event_time", event.eventTime());
    row.put("received_at", event.receivedAt());
    row.put("verify_mode", event.verifyMode());
    row.put("attendance_status", event.attendanceStatus());
    row.put("access_decision", event.accessDecision());
    row.put("picture_url", event.pictureUrl());
    row.put("visible_light_pic_url", event.visibleLightPicUrl());
    row.put("thermal_pic_url", event.thermalPicUrl());
    row.put("temperature", event.temperature());
    row.put("mask_status", event.mask());
    row.put("major", event.major());
    row.put("minor", event.minor());
    row.put("raw_payload", event.rawPayload());
    return row;
  }

  private List<HikvisionRecognitionEvent> fetchAccessEvents(HikvisionCameraConfig config) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime start = now.minusMinutes(Math.max(1, config.lookbackMinutes()));

    Map<String, Object> condition = new LinkedHashMap<>();
    condition.put("searchID", "garmentline-live-face");
    condition.put("searchResultPosition", 0);
    condition.put("maxResults", 30);
    condition.put("startTime", CAMERA_TIME_FORMATTER.format(start));
    condition.put("endTime", CAMERA_TIME_FORMATTER.format(now));
    condition.put("picEnable", true);
    condition.put("timeReverseOrder", true);
    condition.put("isAttendanceInfo", true);

    Map<String, Object> payload = Map.of("AcsEventCond", condition);
    JsonNode response = isapiClient.postJson(config, "/ISAPI/AccessControl/AcsEvent?format=json", payload);
    JsonNode infoList = response.path("AcsEvent").path("InfoList");
    if (!(infoList instanceof ArrayNode arrayNode)) {
      return List.of();
    }

    List<HikvisionRecognitionEvent> normalized = new ArrayList<>();
    arrayNode.forEach(
        node -> {
          Optional<HikvisionRecognitionEvent> event = normalizeEvent(node);
          event.ifPresent(normalized::add);
        });
    return normalized;
  }

  private Optional<HikvisionRecognitionEvent> normalizeEvent(JsonNode node) {
    String employeeNo = firstText(node, "employeeNoString", "employeeNo", "employeeNoString");
    String verifyMode = JsonSupport.text(node, "currentVerifyMode");
    String pictureUrl = JsonSupport.text(node, "pictureURL");
    String visibleLightPicUrl = firstText(node, "visibleLightPicUrl", "visibleLightURL");
    String devicePersonName = JsonSupport.text(node, "name");
    boolean likelyFaceEvent =
        hasText(employeeNo)
            || hasText(devicePersonName)
            || hasText(pictureUrl)
            || hasText(visibleLightPicUrl)
            || (verifyMode != null && verifyMode.toLowerCase(Locale.ROOT).contains("face"));

    if (!likelyFaceEvent) {
      return Optional.empty();
    }

    OffsetDateTime eventTime = parseCameraTime(firstText(node, "time", "dateTime")).orElse(OffsetDateTime.now());
    String serialNo = firstText(node, "serialNo", "SerialNo");
    Integer major = intValue(node, "major");
    Integer minor = intValue(node, "minor");
    EmployeeMatch match = hasText(employeeNo) ? findEmployee(employeeNo) : EmployeeMatch.unmatched();
    String id = eventId(serialNo, employeeNo, eventTime, major, minor);
    String attendanceStatus = JsonSupport.text(node, "attendanceStatus");
    String accessDecision = hasText(employeeNo) ? "recognized" : "unknown";

    return Optional.of(
        new HikvisionRecognitionEvent(
            id,
            serialNo,
            employeeNo,
            devicePersonName,
            match.employeeId(),
            match.employeeName(),
            match.department(),
            match.status(),
            eventTime,
            OffsetDateTime.now(),
            verifyMode,
            attendanceStatus,
            accessDecision,
            pictureUrl,
            visibleLightPicUrl,
            JsonSupport.text(node, "thermalPicUrl"),
            doubleValue(node, "currTemperature"),
            JsonSupport.text(node, "mask"),
            major,
            minor,
            objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {})));
  }

  private EmployeeMatch findEmployee(String employeeNo) {
    return employeeCache.computeIfAbsent(
        employeeNo,
        key -> {
          try {
            MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
            query.add("select", "id,employee_code,display_name,department_name");
            query.add("employee_code", "eq." + key);
            query.add("limit", "1");
            ArrayNode rows = supabaseAdminClient.select("employees", query);
            if (rows.isEmpty()) {
              return EmployeeMatch.unmatched();
            }
            JsonNode row = rows.get(0);
            String displayName = JsonSupport.text(row, "display_name");
            return new EmployeeMatch(
                JsonSupport.text(row, "id"),
                displayName == null || displayName.isBlank() ? key : displayName,
                JsonSupport.text(row, "department_name"),
                "matched");
          } catch (RuntimeException exception) {
            return EmployeeMatch.unmatched();
          }
        });
  }

  private HikvisionDeviceInfo fetchDeviceInfo(HikvisionCameraConfig config) {
    String body;
    try {
      body = isapiClient.getText(config, "/ISAPI/System/deviceInfo?format=json");
    } catch (RuntimeException exception) {
      body = isapiClient.getText(config, "/ISAPI/System/deviceInfo");
    }
    return parseDeviceInfo(body);
  }

  private HikvisionDeviceInfo parseDeviceInfo(String body) {
    String trimmed = body == null ? "" : body.trim();
    if (trimmed.startsWith("{")) {
      try {
        JsonNode root = objectMapper.readTree(trimmed);
        JsonNode info = root.has("DeviceInfo") ? root.path("DeviceInfo") : root;
        return new HikvisionDeviceInfo(
            firstText(info, "deviceName", "deviceDescription"),
            firstText(info, "deviceID", "deviceId"),
            JsonSupport.text(info, "model"),
            firstText(info, "serialNumber", "serialNo"),
            firstText(info, "macAddress", "MACAddress"),
            firstText(info, "firmwareVersion", "firmwareReleasedDate"));
      } catch (Exception exception) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse camera deviceInfo JSON.");
      }
    }

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(trimmed)));
      return new HikvisionDeviceInfo(
          xmlText(document, "deviceName", "deviceDescription"),
          xmlText(document, "deviceID", "deviceId"),
          xmlText(document, "model"),
          xmlText(document, "serialNumber", "serialNo"),
          xmlText(document, "macAddress", "MACAddress"),
          xmlText(document, "firmwareVersion", "firmwareReleasedDate"));
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse camera deviceInfo XML.");
    }
  }

  private String xmlText(Document document, String... names) {
    for (String name : names) {
      var nodes = document.getElementsByTagNameNS("*", name);
      if (nodes.getLength() == 0) {
        nodes = document.getElementsByTagName(name);
      }
      if (nodes.getLength() > 0) {
        String text = nodes.item(0).getTextContent();
        if (text != null && !text.isBlank()) {
          return text.trim();
        }
      }
    }
    return null;
  }

  private HikvisionStatus status() {
    HikvisionCameraConfig config = activeConfig.get();
    List<HikvisionRecognitionEvent> snapshot;
    synchronized (eventLock) {
      snapshot = List.copyOf(events);
    }
    int matched = (int) snapshot.stream().filter(event -> "matched".equals(event.matchStatus())).count();

    return new HikvisionStatus(
        config != null,
        pollingTask != null && !pollingTask.isCancelled(),
        config == null ? null : config.baseUrl(),
        config == null ? null : config.username(),
        config == null ? DEFAULT_POLL_INTERVAL_SECONDS : config.pollIntervalSeconds(),
        config == null ? DEFAULT_LOOKBACK_MINUTES : config.lookbackMinutes(),
        lastPollAt,
        lastSuccessAt,
        lastError,
        deviceInfo,
        snapshot.size(),
        matched);
  }

  private HikvisionCameraConfig configFromProperties(HikvisionProperties properties) {
    if (properties == null || properties.baseUrl() == null || properties.baseUrl().isBlank()) {
      return null;
    }

    return new HikvisionCameraConfig(
        normalizeBaseUrl(properties.baseUrl()),
        Objects.requireNonNullElse(properties.username(), "admin"),
        Objects.requireNonNullElse(properties.password(), ""),
        properties.pollIntervalSeconds() == null
            ? DEFAULT_POLL_INTERVAL_SECONDS
            : properties.pollIntervalSeconds(),
        properties.lookbackMinutes() == null ? DEFAULT_LOOKBACK_MINUTES : properties.lookbackMinutes());
  }

  private HikvisionCameraConfig requireConfig() {
    HikvisionCameraConfig config = activeConfig.get();
    if (config == null || config.baseUrl() == null || config.baseUrl().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Configure the Hikvision camera connection first.");
    }
    return config;
  }

  private String normalizeBaseUrl(String baseUrl) {
    String value = baseUrl == null ? "" : baseUrl.trim();
    if (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    if (!value.startsWith("http://") && !value.startsWith("https://")) {
      value = "http://" + value;
    }
    return value;
  }

  private Optional<OffsetDateTime> parseCameraTime(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(OffsetDateTime.parse(value));
    } catch (DateTimeParseException ignored) {
      try {
        return Optional.of(
            LocalDateTime.parse(value)
                .atOffset(ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now())));
      } catch (DateTimeParseException ignoredAgain) {
        return Optional.empty();
      }
    }
  }

  private String firstText(JsonNode node, String... names) {
    for (String name : names) {
      String value = JsonSupport.text(node, name);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private Integer intValue(JsonNode node, String name) {
    JsonNode value = node.path(name);
    return value.isInt() ? value.asInt() : null;
  }

  private Double doubleValue(JsonNode node, String name) {
    JsonNode value = node.path(name);
    return value.isNumber() ? value.asDouble() : null;
  }

  private String eventId(
      String serialNo, String employeeNo, OffsetDateTime eventTime, Integer major, Integer minor) {
    if (serialNo != null && !serialNo.isBlank()) {
      return "hikvision-" + serialNo;
    }
    return "hikvision-"
        + UUID.nameUUIDFromBytes(
            (employeeNo + "|" + eventTime + "|" + major + "|" + minor).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private void stopPollingInternal() {
    if (pollingTask != null) {
      pollingTask.cancel(true);
      pollingTask = null;
    }
  }

  private void requireAccess(AuthenticatedUser user) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "viewer");
  }

  private void requireManage(AuthenticatedUser user) {
    roleGuard.requireAnyRole(user, "admin", "supervisor");
  }

  private record EmployeeMatch(
      String employeeId,
      String employeeName,
      String department,
      String status
  ) {
    static EmployeeMatch unmatched() {
      return new EmployeeMatch(null, null, null, "unmatched");
    }
  }
}
