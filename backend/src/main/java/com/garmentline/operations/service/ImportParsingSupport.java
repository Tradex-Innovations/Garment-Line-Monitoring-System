package com.garmentline.operations.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImportParsingSupport {

  private static final List<DateTimeFormatter> DATE_PATTERNS =
      List.of(
          DateTimeFormatter.ofPattern("yyyy-MM-dd"),
          DateTimeFormatter.ofPattern("yyyy/MM/dd"),
          DateTimeFormatter.ofPattern("dd/MM/yyyy"),
          DateTimeFormatter.ofPattern("d/M/yyyy"),
          DateTimeFormatter.ofPattern("dd-MM-yyyy"),
          DateTimeFormatter.ofPattern("d-M-yyyy"),
          DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
          DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));

  private static final List<String> FACE_HEADER_SEQUENCE =
      List.of("firstname", "lastname", "id", "department", "date", "weekday", "records");

  private static final Map<String, String> FINGERPRINT_HEADER_ALIASES =
      Map.ofEntries(
          Map.entry("empno", "empNo"),
          Map.entry("epfno", "epfNo"),
          Map.entry("name", "name"),
          Map.entry("designation", "designation"),
          Map.entry("department", "department"),
          Map.entry("date", "dateText"),
          Map.entry("timein", "timeInText"),
          Map.entry("timeout", "timeOutText"),
          Map.entry("lateearly", "lateEarlyText"),
          Map.entry("day", "dayText"),
          Map.entry("ot", "otText"),
          Map.entry("leavetype", "leaveType"),
          Map.entry("leavedaystotal", "leaveDaysTotalText"),
          Map.entry("nopaydaystotal", "nopayDaysTotalText"),
          Map.entry("otherleavedays", "otherLeaveDaysText"));

  private static final List<AnchorColumn> PDF_HEADER_COLUMNS =
      List.of(
          new AnchorColumn("empNo", "EmpNo"),
          new AnchorColumn("epfNo", "EpfNo"),
          new AnchorColumn("name", "Name"),
          new AnchorColumn("designation", "Designation"),
          new AnchorColumn("department", "Department"),
          new AnchorColumn("dateText", "Date"),
          new AnchorColumn("timeInText", "Time In"),
          new AnchorColumn("timeOutText", "Time Out"),
          new AnchorColumn("lateEarlyText", "Late/Early"),
          new AnchorColumn("dayText", "Day"),
          new AnchorColumn("otText", "OT"),
          new AnchorColumn("leaveType", "Leave Type"),
          new AnchorColumn("leaveDaysTotalText", "Leave DaysTotal"),
          new AnchorColumn("nopayDaysTotalText", "Nopay DaysTotal"),
          new AnchorColumn("otherLeaveDaysText", "Other Leave Days"));

  private static final Pattern EMPLOYEE_CATEGORY_PATTERN =
      Pattern.compile("^EMP\\s+Category\\s*-\\s*(.+)$", Pattern.CASE_INSENSITIVE);

  private static final Pattern DATE_FROM_PATTERN =
      Pattern.compile("Date\\s+From\\s*:?\\s*([0-9]{1,4}[/-][0-9]{1,2}[/-][0-9]{1,4})", Pattern.CASE_INSENSITIVE);

  private static final Pattern DATE_TO_PATTERN =
      Pattern.compile("Date\\s+To\\s*:?\\s*([0-9]{1,4}[/-][0-9]{1,2}[/-][0-9]{1,4})", Pattern.CASE_INSENSITIVE);

  private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

  private static final Pattern FINGERPRINT_EMPLOYEE_LINE_PATTERN =
      Pattern.compile("^(?<empNo>\\d+)\\s+(?<epfNo>\\d+)(?:\\s+)?(?<prefix>.+)$");

  private static final Pattern FINGERPRINT_ATTENDANCE_LINE_PATTERN =
      Pattern.compile(
          "^(?<date>\\d{4}-\\d{2}-\\d{2})\\s+"
              + "(?<timeIn>\\d{2}:\\d{2})\\s+"
              + "(?<timeOut>\\d{2}:\\d{2})\\s+"
              + "(?<lateEarly>[^\\s]+)\\s+"
              + "(?<day>[^\\s]+)\\s+"
              + "(?<ot>[^\\s]+)"
              + "(?<rest>.*)$");

  private static final Pattern UPPERCASE_TO_TITLECASE_BOUNDARY =
      Pattern.compile("([A-Z])([A-Z][a-z])");

  private static final Pattern LOWERCASE_TO_UPPERCASE_BOUNDARY =
      Pattern.compile("([a-z])([A-Z])");

  private static final List<String> FINGERPRINT_DEPARTMENT_SUFFIXES =
      List.of(
          "PLANING DEPARTMENT",
          "HUMAN RESOURCES",
          "QUALITY CONTROL",
          "LAB DEPARTMENT",
          "WORK STUDY",
          "ADMINISTRATION",
          "MAINTENANCE",
          "MECHANICAL",
          "PRODUCTION",
          "PACKING",
          "CUTTING",
          "FINANCE",
          "STORES",
          "SAMPLE",
          "CAD",
          "IT");

  private static final List<String> FINGERPRINT_GLUE_BREAK_KEYWORDS =
      List.of(
          "COORDINATOR",
          "MERCHANDISER",
          "SUPERVISOR",
          "EXECUTIVE",
          "INCHARGE",
          "ASSISTANT",
          "MECHANIC",
          "MANAGER",
          "PATTERN",
          "QUALITY",
          "TRAINEE",
          "OFFICER",
          "RECORDER",
          "CHECKER",
          "OPERATOR",
          "CUTTER",
          "LOADER",
          "HELPER",
          "JUNIOR",
          "SENIOR",
          "SAMPLE",
          "HUMAN",
          "NURSE",
          "FINAL",
          "HEAD",
          "LAB",
          "IE",
          "HR");

  private final DataFormatter dataFormatter = new DataFormatter();
  private final ObjectMapper objectMapper;

  public ImportParsingSupport(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public FaceParseResult parseFaceWorkbook(MultipartFile file) throws IOException {
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
      Sheet sheet = pickFirstVisibleSheet(workbook);
      List<List<String>> rows = readSheetRows(sheet);
      int headerRowIndex = detectHeaderRow(rows, FACE_HEADER_SEQUENCE);
      if (headerRowIndex < 0) {
        throw new IllegalArgumentException(
            "Could not find the face workbook header row. Expected columns: First Name, Last Name, ID, Department, Date, Weekday, Records.");
      }

      Map<String, Integer> headerMap = buildHeaderMap(rows.get(headerRowIndex), FACE_HEADER_SEQUENCE);
      List<FaceParsedRow> parsedRows = new ArrayList<>();
      List<String> warnings = new ArrayList<>();

      for (int index = 0; index < headerRowIndex; index += 1) {
        String metadataLine = normalizeWhitespace(String.join(" ", rows.get(index)));
        if (!metadataLine.isBlank()) {
          warnings.add(metadataLine);
        }
      }

      for (int index = headerRowIndex + 1; index < rows.size(); index += 1) {
        List<String> row = rows.get(index);
        int rowNumber = index + 1;
        String firstName = readRowCell(row, headerMap.get("firstname"));
        String lastName = readRowCell(row, headerMap.get("lastname"));
        String employeeId = readRowCell(row, headerMap.get("id"));
        String department = readRowCell(row, headerMap.get("department"));
        String dateText = readRowCell(row, headerMap.get("date"));
        String weekday = readRowCell(row, headerMap.get("weekday"));
        String recordsText = readRowCell(row, headerMap.get("records"));

        if (StreamSupport.values(firstName, lastName, employeeId, department, dateText, weekday, recordsText)
            .stream()
            .noneMatch(Objects::nonNull)) {
          continue;
        }

        List<String> missing = new ArrayList<>();
        if (employeeId == null) {
          missing.add("employeeId");
        }
        if (dateText == null) {
          missing.add("dateText");
        }
        if (recordsText == null) {
          missing.add("recordsText");
        }

        ObjectNode rawPayload = objectMapper.createObjectNode();
        rawPayload.put("worksheetRowNumber", rowNumber);
        rawPayload.put("source", "face_workbook");
        rawPayload.set("cells", objectMapper.valueToTree(row));

        parsedRows.add(
            new FaceParsedRow(
                rowNumber,
                firstName,
                lastName,
                employeeId,
                department,
                dateText,
                weekday,
                recordsText,
                rawPayload,
                missing.isEmpty() ? "parsed" : "failed",
                missing.isEmpty() ? null : "Missing required face fields: " + String.join(", ", missing)));
      }

      return new FaceParseResult(headerRowIndex + 1, parsedRows, warnings);
    }
  }

  public FingerprintParseResult parseFingerprintFile(MultipartFile file) throws IOException {
    String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    if (filename.endsWith(".pdf")) {
      return parseFingerprintPdf(file);
    }
    if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
      return parseFingerprintWorkbook(file, "xlsx");
    }
    if (filename.endsWith(".csv")) {
      return parseFingerprintCsv(file);
    }
    throw new IllegalArgumentException(
        "Unsupported fingerprint file format for \"" + file.getOriginalFilename() + "\". Expected PDF, XLSX, XLS, or CSV.");
  }

  public String normalizeWhitespace(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }

  public String cleanCellValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof LocalDate date) {
      return date.toString();
    }
    if (value instanceof LocalDateTime dateTime) {
      return dateTime.toString();
    }
    String normalized = normalizeWhitespace(String.valueOf(value));
    return normalized.isBlank() ? null : normalized;
  }

  public String parseFlexibleDateText(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = normalizeWhitespace(value);
    for (DateTimeFormatter formatter : DATE_PATTERNS) {
      try {
        return LocalDate.parse(normalized, formatter).toString();
      } catch (DateTimeParseException ignored) {
      }
    }

    try {
      return LocalDate.parse(normalized).toString();
    } catch (DateTimeParseException ignored) {
    }

    try {
      return LocalDateTime.parse(normalized).toLocalDate().toString();
    } catch (DateTimeParseException ignored) {
    }

    return null;
  }

  public boolean isValidTimeToken(String value) {
    return value != null && value.matches("^([01]\\d|2[0-3]):[0-5]\\d$");
  }

  public String toDatabaseTime(String value) {
    return isValidTimeToken(value) ? value + ":00" : null;
  }

  public Double parseDecimalHours(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = normalizeWhitespace(value);
    if (normalized.matches("^-?\\d+(\\.\\d+)?$")) {
      return Double.parseDouble(normalized);
    }

    if (normalized.matches("^-?([01]?\\d|2[0-3]):[0-5]\\d$")) {
      boolean negative = normalized.startsWith("-");
      String[] parts = normalized.replace("-", "").split(":");
      double total = Integer.parseInt(parts[0]) + Integer.parseInt(parts[1]) / 60.0d;
      return Math.round((negative ? -total : total) * 100.0d) / 100.0d;
    }

    return null;
  }

  public boolean isLikelyHumanName(String value) {
    if (value == null) {
      return false;
    }
    String normalized = normalizeWhitespace(value);
    if (normalized.isBlank() || normalized.equals("--")) {
      return false;
    }
    if (normalized.replace(" ", "").matches("^\\d+$")) {
      return false;
    }
    return normalized.matches(".*[A-Za-z].*");
  }

  public String combineSourceName(String firstName, String lastName) {
    List<String> values = new ArrayList<>();
    if (firstName != null && !firstName.equals("--")) {
      values.add(firstName);
    }
    if (lastName != null && !lastName.equals("--")) {
      values.add(lastName);
    }
    String joined = normalizeWhitespace(String.join(" ", values));
    return joined.isBlank() ? null : joined;
  }

  public String buildImportsStoragePath(String sourceType, String batchId, String originalFilename) {
    String safeFilename = originalFilename.replaceAll("[^\\w.\\-()]+", "_");
    LocalDate now = LocalDate.now();
    return "imports/"
        + sourceType
        + "/"
        + now.format(DateTimeFormatter.ofPattern("yyyy"))
        + "/"
        + now.format(DateTimeFormatter.ofPattern("MM"))
        + "/"
        + batchId
        + "/"
        + safeFilename;
  }

  private FingerprintParseResult parseFingerprintWorkbook(MultipartFile file, String fileFormat)
      throws IOException {
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
      Sheet sheet = workbook.getSheetAt(0);
      List<List<String>> rows = readSheetRows(sheet);
      return parseTabularRows(rows, fileFormat);
    }
  }

  private FingerprintParseResult parseFingerprintCsv(MultipartFile file) throws IOException {
    try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        CSVParser parser =
            CSVFormat.DEFAULT.builder().setTrim(true).setIgnoreEmptyLines(true).build().parse(reader)) {
      List<List<String>> rows = new ArrayList<>();
      for (CSVRecord record : parser) {
        List<String> values = new ArrayList<>();
        record.forEach(value -> values.add(cleanCellValue(value)));
        rows.add(values);
      }
      return parseTabularRows(rows, "csv");
    }
  }

  private FingerprintParseResult parseTabularRows(List<List<String>> rows, String fileFormat) {
    int headerRowIndex = detectFingerprintHeaderRow(rows);
    if (headerRowIndex < 0) {
      throw new IllegalArgumentException(
          "Could not find the fingerprint attendance header row in the workbook or CSV.");
    }

    Map<String, Integer> headerMap = buildFingerprintHeaderMap(rows.get(headerRowIndex));
    List<FingerprintParsedRow> parsedRows = new ArrayList<>();

    for (int index = headerRowIndex + 1; index < rows.size(); index += 1) {
      List<String> row = rows.get(index);
      int rowNumber = index + 1;

      String empNo = readRowCell(row, headerMap.get("empNo"));
      String epfNo = readRowCell(row, headerMap.get("epfNo"));
      String name = readRowCell(row, headerMap.get("name"));
      String designation = readRowCell(row, headerMap.get("designation"));
      String department = readRowCell(row, headerMap.get("department"));
      String dateText = readRowCell(row, headerMap.get("dateText"));
      String timeInText = readRowCell(row, headerMap.get("timeInText"));
      String timeOutText = readRowCell(row, headerMap.get("timeOutText"));
      String lateEarlyText = readRowCell(row, headerMap.get("lateEarlyText"));
      String dayText = readRowCell(row, headerMap.get("dayText"));
      String otText = readRowCell(row, headerMap.get("otText"));
      String leaveType = readRowCell(row, headerMap.get("leaveType"));
      String leaveDaysTotalText = readRowCell(row, headerMap.get("leaveDaysTotalText"));
      String nopayDaysTotalText = readRowCell(row, headerMap.get("nopayDaysTotalText"));
      String otherLeaveDaysText = readRowCell(row, headerMap.get("otherLeaveDaysText"));

      if (StreamSupport.values(empNo, epfNo, name, designation, department, dateText, timeInText, timeOutText, leaveType)
          .stream()
          .noneMatch(Objects::nonNull)) {
        continue;
      }

      List<String> missing = new ArrayList<>();
      if (empNo == null) {
        missing.add("empNo");
      }
      if (dateText == null) {
        missing.add("dateText");
      }

      ObjectNode rawPayload = objectMapper.createObjectNode();
      rawPayload.put("worksheetRowNumber", rowNumber);
      rawPayload.put("source", "fingerprint_" + fileFormat);
      rawPayload.set("cells", objectMapper.valueToTree(row));

      parsedRows.add(
          new FingerprintParsedRow(
              rowNumber,
              empNo,
              epfNo,
              name,
              designation,
              department,
              null,
              dateText,
              timeInText,
              timeOutText,
              lateEarlyText,
              dayText,
              otText,
              leaveType,
              leaveDaysTotalText,
              nopayDaysTotalText,
              otherLeaveDaysText,
              rawPayload,
              missing.isEmpty() ? "parsed" : "failed",
              missing.isEmpty() ? null : "Missing required fingerprint fields: " + String.join(", ", missing)));
    }

    return new FingerprintParseResult(fileFormat, parsedRows, List.of(), FingerprintImportMetadata.empty());
  }

  private FingerprintParseResult parseFingerprintPdf(MultipartFile file) throws IOException {
    List<FingerprintParsedRow> rows = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    int nextRowNumber = 1;
    String currentEmployeeCategory = null;
    FingerprintEmployeeContext currentEmployeeContext = null;
    FingerprintImportMetadata metadata = FingerprintImportMetadata.empty();

    try (PDDocument document = Loader.loadPDF(file.getBytes())) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);

      for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber += 1) {
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        List<String> lines = extractFingerprintPdfLines(stripper.getText(document));
        if (lines.isEmpty()) {
          continue;
        }

        int dataStartIndex = 0;
        if (pageNumber == 1) {
          int headerLineIndex = findFingerprintHeaderLineIndex(lines);
          if (headerLineIndex >= 0) {
            metadata = extractFingerprintImportMetadata(lines.subList(0, headerLineIndex));
            dataStartIndex = headerLineIndex + 1;
          } else {
            metadata = extractFingerprintImportMetadata(lines.subList(0, Math.min(lines.size(), 5)));
            addFingerprintWarning(
                warnings,
                "Page 1: fingerprint header row was not detected. Parsing continued from the first data-looking line.");
          }
        }

        for (int index = dataStartIndex; index < lines.size(); index += 1) {
          String rawLine = lines.get(index);
          if (rawLine.isBlank()
              || rawLine.toLowerCase(Locale.ROOT).matches("^page\\s+\\d+.*")
              || looksLikePdfHeaderLine(rawLine)) {
            continue;
          }

          String employeeCategory = extractEmployeeCategory(rawLine);
          if (employeeCategory != null) {
            currentEmployeeCategory = employeeCategory;
            continue;
          }

          String repairedLine = repairFingerprintLine(rawLine);
          if (looksLikePdfHeaderLine(repairedLine)) {
            continue;
          }

          FingerprintParsedRow parsedRow = null;

          if (isFingerprintEmployeeStartLine(repairedLine)) {
            parsedRow =
                parseFingerprintEmployeeStartLine(
                    nextRowNumber, pageNumber, repairedLine, currentEmployeeCategory);
            currentEmployeeContext =
                new FingerprintEmployeeContext(
                    parsedRow.empNo(),
                    parsedRow.epfNo(),
                    parsedRow.name(),
                    parsedRow.designation(),
                    parsedRow.department());
          } else if (isFingerprintContinuationLine(repairedLine)) {
            if (currentEmployeeContext == null) {
              addFingerprintWarning(
                  warnings,
                  "Page " + pageNumber + ": skipped a continuation row before any employee context was available.");
              continue;
            }
            parsedRow =
                parseFingerprintContinuationLine(
                    nextRowNumber,
                    pageNumber,
                    repairedLine,
                    currentEmployeeCategory,
                    currentEmployeeContext);
          } else if (!looksLikeMetadataLine(rawLine)) {
            addFingerprintWarning(
                warnings,
                "Page " + pageNumber + ": skipped an unrecognized fingerprint row: " + rawLine);
          }

          if (parsedRow != null) {
            rows.add(parsedRow);
            nextRowNumber += 1;
          }
        }
      }
    }

    return new FingerprintParseResult("pdf", rows, warnings, metadata);
  }

  private FingerprintParsedRow appendContinuationRow(
      FingerprintParsedRow target, Map<String, List<String>> assignedValues) {
    String name = appendText(target.name(), assignedValues.get("name"));
    String designation = appendText(target.designation(), assignedValues.get("designation"));
    String department = appendText(target.department(), assignedValues.get("department"));

    return new FingerprintParsedRow(
        target.rowNumber(),
        target.empNo(),
        target.epfNo(),
        name,
        designation,
        department,
        target.employeeCategory(),
        target.dateText(),
        target.timeInText(),
        target.timeOutText(),
        target.lateEarlyText(),
        target.dayText(),
        target.otText(),
        target.leaveType(),
        target.leaveDaysTotalText(),
        target.nopayDaysTotalText(),
        target.otherLeaveDaysText(),
        target.rawPayload(),
        target.parseStatus(),
        target.parseError());
  }

  private FingerprintImportMetadata extractFingerprintImportMetadata(List<String> lines) {
    String companyName = lines.size() > 0 ? lines.get(0) : null;
    String companyAddress = lines.size() > 1 ? lines.get(1) : null;
    String companyPhone = lines.size() > 2 ? lines.get(2) : null;
    String reportTitleLine =
        lines.stream()
            .filter(line -> line.toLowerCase(Locale.ROOT).contains("attendance report"))
            .findFirst()
            .orElse(null);
    String dateRangeLine =
        lines.stream()
            .filter(
                line ->
                    line.toLowerCase(Locale.ROOT).contains("date from")
                        && line.toLowerCase(Locale.ROOT).contains("date to"))
            .findFirst()
            .orElse(null);

    String reportTitle = reportTitleLine;
    String reportScope = null;

    if (reportTitleLine != null && reportTitleLine.contains(" - ")) {
      String[] parts = reportTitleLine.split("\\s+-\\s+", 2);
      reportTitle = parts[0];
      reportScope = parts.length > 1 ? parts[1] : null;
    }

    String reportDateFromText = extractPatternGroup(dateRangeLine, DATE_FROM_PATTERN);
    String reportDateToText = extractPatternGroup(dateRangeLine, DATE_TO_PATTERN);

    return new FingerprintImportMetadata(
        companyName,
        companyAddress,
        companyPhone,
        reportTitle,
        reportScope,
        reportDateFromText,
        reportDateToText);
  }

  private List<String> extractFingerprintPdfLines(String pageText) {
    return Arrays.stream(pageText.split("\\R"))
        .map(this::normalizeWhitespace)
        .filter(value -> value != null && !value.isBlank())
        .toList();
  }

  private int findFingerprintHeaderLineIndex(List<String> lines) {
    for (int index = 0; index < lines.size(); index += 1) {
      if (looksLikePdfHeaderLine(lines.get(index))) {
        return index;
      }
    }
    return -1;
  }

  private boolean looksLikePdfHeaderLine(String line) {
    String canonical = canonicalize(line);
    return canonical.contains("empno")
        && canonical.contains("epfno")
        && canonical.contains("timeout")
        && canonical.contains("leavedaystotal");
  }

  private boolean looksLikeMetadataLine(String line) {
    String normalized = normalizeWhitespace(line).toLowerCase(Locale.ROOT);
    return normalized.contains("attendance report")
        || normalized.contains("date from")
        || normalized.contains("union north pvt ltd")
        || normalized.contains("dankotuwa industrial estate")
        || normalized.matches("^0?\\d{9,10}$");
  }

  private boolean isFingerprintEmployeeStartLine(String line) {
    Matcher employeeMatcher = FINGERPRINT_EMPLOYEE_LINE_PATTERN.matcher(line);
    if (!employeeMatcher.matches()) {
      return false;
    }
    String prefix = cleanCellValue(employeeMatcher.group("prefix"));
    if (prefix == null) {
      return false;
    }
    Matcher dateMatcher = ISO_DATE_PATTERN.matcher(prefix);
    if (!dateMatcher.find()) {
      return false;
    }
    String attendanceTail = cleanCellValue(prefix.substring(dateMatcher.start()));
    return attendanceTail != null
        && FINGERPRINT_ATTENDANCE_LINE_PATTERN.matcher(attendanceTail).matches();
  }

  private boolean isFingerprintContinuationLine(String line) {
    return FINGERPRINT_ATTENDANCE_LINE_PATTERN.matcher(line).matches();
  }

  private FingerprintParsedRow parseFingerprintEmployeeStartLine(
      int rowNumber, int pageNumber, String line, String employeeCategory) {
    Matcher employeeMatcher = FINGERPRINT_EMPLOYEE_LINE_PATTERN.matcher(line);
    if (!employeeMatcher.matches()) {
      throw new IllegalArgumentException("Unrecognized fingerprint employee line: " + line);
    }

    String empNo = cleanCellValue(employeeMatcher.group("empNo"));
    String epfNo = cleanCellValue(employeeMatcher.group("epfNo"));
    String prefixWithAttendance = cleanCellValue(employeeMatcher.group("prefix"));
    Matcher dateMatcher = ISO_DATE_PATTERN.matcher(prefixWithAttendance == null ? "" : prefixWithAttendance);
    if (!dateMatcher.find()) {
      throw new IllegalArgumentException("Fingerprint employee line is missing the attendance date: " + line);
    }

    String identityText = cleanCellValue(prefixWithAttendance.substring(0, dateMatcher.start()));
    String attendanceTail = cleanCellValue(prefixWithAttendance.substring(dateMatcher.start()));

    FingerprintIdentitySlice identity = parseFingerprintIdentitySlice(empNo, epfNo, identityText);
    FingerprintAttendanceSlice attendance = parseFingerprintAttendanceSlice(attendanceTail);

    return buildFingerprintParsedRow(
        rowNumber,
        pageNumber,
        line,
        employeeCategory,
        false,
        identity.empNo(),
        identity.epfNo(),
        identity.name(),
        identity.designation(),
        identity.department(),
        attendance);
  }

  private FingerprintParsedRow parseFingerprintContinuationLine(
      int rowNumber,
      int pageNumber,
      String line,
      String employeeCategory,
      FingerprintEmployeeContext currentEmployeeContext) {
    FingerprintAttendanceSlice attendance = parseFingerprintAttendanceSlice(line);

    return buildFingerprintParsedRow(
        rowNumber,
        pageNumber,
        line,
        employeeCategory,
        true,
        currentEmployeeContext.empNo(),
        currentEmployeeContext.epfNo(),
        currentEmployeeContext.name(),
        currentEmployeeContext.designation(),
        currentEmployeeContext.department(),
        attendance);
  }

  private FingerprintParsedRow buildFingerprintParsedRow(
      int rowNumber,
      int pageNumber,
      String rawLine,
      String employeeCategory,
      boolean contextCarriedForward,
      String empNo,
      String epfNo,
      String name,
      String designation,
      String department,
      FingerprintAttendanceSlice attendance) {
    List<String> missing = new ArrayList<>();
    if (empNo == null) {
      missing.add("empNo");
    }
    if (attendance == null || attendance.dateText() == null) {
      missing.add("dateText");
    }

    ObjectNode rawPayload = objectMapper.createObjectNode();
    rawPayload.put("source", "fingerprint_pdf");
    rawPayload.put("pageNumber", pageNumber);
    rawPayload.put("rowText", rawLine);
    rawPayload.put("employeeCategory", employeeCategory);
    rawPayload.put("contextCarriedForward", contextCarriedForward);

    return new FingerprintParsedRow(
        rowNumber,
        empNo,
        epfNo,
        name,
        designation,
        department,
        employeeCategory,
        attendance == null ? null : attendance.dateText(),
        attendance == null ? null : attendance.timeInText(),
        attendance == null ? null : attendance.timeOutText(),
        attendance == null ? null : attendance.lateEarlyText(),
        attendance == null ? null : attendance.dayText(),
        attendance == null ? null : attendance.otText(),
        attendance == null ? null : attendance.leaveType(),
        attendance == null ? null : attendance.leaveDaysTotalText(),
        attendance == null ? null : attendance.nopayDaysTotalText(),
        attendance == null ? null : attendance.otherLeaveDaysText(),
        rawPayload,
        missing.isEmpty() ? "parsed" : "failed",
        missing.isEmpty() ? null : "Missing required fingerprint fields: " + String.join(", ", missing));
  }

  private FingerprintIdentitySlice parseFingerprintIdentitySlice(
      String empNo, String epfNo, String identityText) {
    String repaired = repairFingerprintLine(identityText);
    String department = extractFingerprintDepartment(repaired);
    String withoutDepartment = repaired;
    if (department != null && repaired != null) {
      withoutDepartment =
          cleanCellValue(
              repaired.substring(0, repaired.length() - department.length()));
    }

    List<String> tokens =
        withoutDepartment == null || withoutDepartment.isBlank()
            ? List.of()
            : List.of(withoutDepartment.split("\\s+"));

    if (tokens.isEmpty()) {
      return new FingerprintIdentitySlice(empNo, epfNo, null, null, department);
    }

    int lowercaseIndex = findFirstLowercaseTokenIndex(tokens);
    int nameEndIndex;
    if (lowercaseIndex > 0) {
      nameEndIndex = lowercaseIndex;
    } else {
      int initialsCount = countLeadingInitialTokens(tokens);
      if (initialsCount > 0 && initialsCount < tokens.size()) {
        nameEndIndex = Math.min(tokens.size(), initialsCount + 1);
      } else if (tokens.size() >= 2) {
        nameEndIndex = 2;
      } else {
        nameEndIndex = 1;
      }
    }

    String name = cleanCellValue(String.join(" ", tokens.subList(0, Math.min(nameEndIndex, tokens.size()))));
    String designation =
        nameEndIndex >= tokens.size()
            ? null
            : cleanCellValue(String.join(" ", tokens.subList(nameEndIndex, tokens.size())));

    return new FingerprintIdentitySlice(empNo, epfNo, name, designation, department);
  }

  private FingerprintAttendanceSlice parseFingerprintAttendanceSlice(String attendanceText) {
    Matcher matcher = FINGERPRINT_ATTENDANCE_LINE_PATTERN.matcher(attendanceText == null ? "" : attendanceText);
    if (!matcher.matches()) {
      return null;
    }

    String trailingText = cleanCellValue(matcher.group("rest"));
    List<String> trailingTokens =
        trailingText == null || trailingText.isBlank()
            ? List.of()
            : List.of(trailingText.split("\\s+"));

    return new FingerprintAttendanceSlice(
        cleanCellValue(matcher.group("date")),
        cleanCellValue(matcher.group("timeIn")),
        cleanCellValue(matcher.group("timeOut")),
        cleanCellValue(matcher.group("lateEarly")),
        cleanCellValue(matcher.group("day")),
        cleanCellValue(matcher.group("ot")),
        trailingTokens.size() > 0 ? cleanCellValue(trailingTokens.get(0)) : null,
        trailingTokens.size() > 1 ? cleanCellValue(trailingTokens.get(1)) : null,
        trailingTokens.size() > 2 ? cleanCellValue(trailingTokens.get(2)) : null,
        trailingTokens.size() > 3 ? cleanCellValue(trailingTokens.get(3)) : null);
  }

  private String repairFingerprintLine(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String repaired = normalizeWhitespace(value);
    repaired = UPPERCASE_TO_TITLECASE_BOUNDARY.matcher(repaired).replaceAll("$1 $2");
    repaired = LOWERCASE_TO_UPPERCASE_BOUNDARY.matcher(repaired).replaceAll("$1 $2");

    for (String keyword : FINGERPRINT_GLUE_BREAK_KEYWORDS) {
      repaired =
          repaired.replaceAll(
              "(?i)([A-Z])(" + Pattern.quote(keyword) + ")(?=\\b|\\s|[-/&]|[A-Z])",
              "$1 $2");
    }

    for (String department : FINGERPRINT_DEPARTMENT_SUFFIXES) {
      String departmentPattern = department.replace(" ", "\\\\s+");
      repaired =
          repaired.replaceAll(
              "(?i)(\\S)(" + departmentPattern + ")$",
              "$1 $2");
    }

    return normalizeWhitespace(repaired);
  }

  private String extractFingerprintDepartment(String identityText) {
    if (identityText == null || identityText.isBlank()) {
      return null;
    }

    String normalized = normalizeWhitespace(identityText);
    String uppercase = normalized.toUpperCase(Locale.ROOT);

    for (String candidate : FINGERPRINT_DEPARTMENT_SUFFIXES) {
      String normalizedCandidate = candidate.toUpperCase(Locale.ROOT);
      if (uppercase.endsWith(normalizedCandidate)) {
        return candidate;
      }
    }

    String compacted = uppercase.replaceAll("[^A-Z]", "");
    if (compacted.contains("HUMANRESOURCES") || compacted.endsWith("RESOURCES")) {
      return "HUMAN RESOURCES";
    }
    if (compacted.contains("QUALITYCONTROL") || compacted.endsWith("CONTROL")) {
      return "QUALITY CONTROL";
    }
    if (compacted.contains("LABDEPARTMENT")) {
      return "LAB DEPARTMENT";
    }
    if (compacted.contains("PLANINGDEPARTMENT")) {
      return "PLANING DEPARTMENT";
    }

    List<String> tokens = List.of(normalized.split("\\s+"));
    for (int width = Math.min(2, tokens.size()); width >= 1; width -= 1) {
      String candidate = String.join(" ", tokens.subList(tokens.size() - width, tokens.size()));
      if (candidate.equals(candidate.toUpperCase(Locale.ROOT))) {
        return candidate;
      }
    }

    return null;
  }

  private int findFirstLowercaseTokenIndex(List<String> tokens) {
    for (int index = 0; index < tokens.size(); index += 1) {
      if (tokens.get(index).matches(".*[a-z].*")) {
        return index;
      }
    }
    return -1;
  }

  private int countLeadingInitialTokens(List<String> tokens) {
    int count = 0;
    while (count < tokens.size() && tokens.get(count).matches("^[A-Z]$")) {
      count += 1;
    }
    return count;
  }

  private void addFingerprintWarning(List<String> warnings, String warning) {
    if (warning == null || warning.isBlank()) {
      return;
    }
    if (warnings.size() < 25) {
      warnings.add(warning);
      return;
    }
    if (warnings.size() == 25) {
      warnings.add("Additional fingerprint parser warnings were omitted.");
    }
  }

  private String extractEmployeeCategory(String rowText) {
    if (rowText == null) {
      return null;
    }

    Matcher matcher = EMPLOYEE_CATEGORY_PATTERN.matcher(normalizeWhitespace(rowText));
    if (!matcher.matches()) {
      return null;
    }

    return cleanCellValue(matcher.group(1));
  }

  private String extractPatternGroup(String value, Pattern pattern) {
    if (value == null || value.isBlank()) {
      return null;
    }

    Matcher matcher = pattern.matcher(value);
    return matcher.find() ? cleanCellValue(matcher.group(1)) : null;
  }

  private String appendText(String current, List<String> nextValues) {
    String next = cleanCellValue(String.join(" ", nextValues));
    if (next == null) {
      return current;
    }
    if (current == null || current.isBlank()) {
      return next;
    }
    return normalizeWhitespace(current + " " + next);
  }

  private List<RowGroup> groupRows(List<PositionedText> items, float yTolerance) {
    List<RowGroup> rows = new ArrayList<>();
    List<PositionedText> sorted =
        items.stream()
            .sorted(
                Comparator.comparing(PositionedText::y)
                    .thenComparing(PositionedText::x))
            .toList();

    for (PositionedText item : sorted) {
      RowGroup existing =
          rows.stream()
              .filter(row -> Math.abs(row.y() - item.y()) <= yTolerance)
              .findFirst()
              .orElse(null);

      if (existing == null) {
        List<PositionedText> nextItems = new ArrayList<>();
        nextItems.add(item);
        rows.add(new RowGroup(item.y(), nextItems));
      } else {
        existing.items().add(item);
      }
    }

    rows.forEach(group -> group.items().sort(Comparator.comparing(PositionedText::x)));
    return rows;
  }

  private int findPdfHeaderRowIndex(List<RowGroup> rows) {
    for (int index = 0; index < rows.size(); index += 1) {
      if (looksLikePdfHeaderRow(rows.get(index).items())) {
        return index;
      }
    }
    return -1;
  }

  private boolean looksLikePdfHeaderRow(List<PositionedText> row) {
    String text =
        canonicalize(
            row.stream().map(PositionedText::text).collect(Collectors.joining(" ")));
    return text.contains("empno") && text.contains("epfno") && text.contains("timeout");
  }

  private List<Anchor> findHeaderAnchors(List<PositionedText> headerRow) {
    List<Anchor> anchors = new ArrayList<>();
    List<String> canonicalItems =
        headerRow.stream().map(item -> canonicalize(item.text())).toList();

    for (AnchorColumn column : PDF_HEADER_COLUMNS) {
      String label = canonicalize(column.label());
      for (int start = 0; start < canonicalItems.size(); start += 1) {
        StringBuilder builder = new StringBuilder();
        for (int end = start; end < canonicalItems.size(); end += 1) {
          builder.append(canonicalItems.get(end));
          if (builder.toString().equals(label)) {
            anchors.add(new Anchor(column.key(), column.label(), headerRow.get(start).x()));
            start = canonicalItems.size();
            break;
          }
        }
      }
    }

    anchors.sort(Comparator.comparing(Anchor::x));
    return anchors;
  }

  private Map<String, List<String>> assignToAnchors(List<PositionedText> row, List<Anchor> anchors) {
    Map<String, List<String>> values = new LinkedHashMap<>();
    PDF_HEADER_COLUMNS.forEach(column -> values.put(column.key(), new ArrayList<>()));

    if (row.isEmpty() || anchors.isEmpty()) {
      return values;
    }

    for (PositionedText item : row) {
      Anchor target = anchors.get(anchors.size() - 1);
      for (int index = 0; index < anchors.size(); index += 1) {
        Anchor anchor = anchors.get(index);
        Anchor nextAnchor = index + 1 < anchors.size() ? anchors.get(index + 1) : null;
        if (nextAnchor == null || item.x() < nextAnchor.x()) {
          target = anchor;
          break;
        }
      }
      values.get(target.key()).add(item.text());
    }

    values.replaceAll(
        (key, current) ->
            current.stream().map(this::normalizeWhitespace).filter(value -> !value.isBlank()).toList());

    return values;
  }

  private int detectFingerprintHeaderRow(List<List<String>> rows) {
    List<String> required = List.of("empno", "epfno", "name", "designation", "department", "date");
    return detectHeaderRow(rows, required);
  }

  private int detectHeaderRow(List<List<String>> rows, List<String> requiredHeaders) {
    for (int index = 0; index < rows.size(); index += 1) {
      List<String> canonicalHeaders =
          rows.get(index).stream().map(this::canonicalize).toList();
      if (requiredHeaders.stream().allMatch(canonicalHeaders::contains)) {
        return index;
      }
    }
    return -1;
  }

  private Map<String, Integer> buildHeaderMap(List<String> row, List<String> keys) {
    Map<String, Integer> headerMap = new HashMap<>();
    for (int index = 0; index < row.size(); index += 1) {
      String canonical = canonicalize(row.get(index));
      if (keys.contains(canonical)) {
        headerMap.put(canonical, index);
      }
    }
    return headerMap;
  }

  private Map<String, Integer> buildFingerprintHeaderMap(List<String> row) {
    Map<String, Integer> headerMap = new HashMap<>();
    for (int index = 0; index < row.size(); index += 1) {
      String alias = FINGERPRINT_HEADER_ALIASES.get(canonicalize(row.get(index)));
      if (alias != null) {
        headerMap.put(alias, index);
      }
    }
    return headerMap;
  }

  private Sheet pickFirstVisibleSheet(Workbook workbook) {
    for (int index = 0; index < workbook.getNumberOfSheets(); index += 1) {
      if (!workbook.isSheetHidden(index) && !workbook.isSheetVeryHidden(index)) {
        return workbook.getSheetAt(index);
      }
    }
    return workbook.getSheetAt(0);
  }

  private List<List<String>> readSheetRows(Sheet sheet) {
    List<List<String>> rows = new ArrayList<>();
    for (Row row : sheet) {
      List<String> values = new ArrayList<>();
      short lastCellNum = row.getLastCellNum() <= 0 ? 0 : row.getLastCellNum();
      for (int index = 0; index < lastCellNum; index += 1) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        values.add(readCell(cell));
      }
      rows.add(values);
    }
    return rows;
  }

  private String readCell(Cell cell) {
    if (cell == null) {
      return null;
    }
    if (cell.getCellType() == CellType.FORMULA) {
      return cleanCellValue(dataFormatter.formatCellValue(cell, cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator()));
    }
    return cleanCellValue(dataFormatter.formatCellValue(cell));
  }

  private String readRowCell(List<String> row, Integer columnIndex) {
    if (columnIndex == null || columnIndex < 0 || columnIndex >= row.size()) {
      return null;
    }
    return cleanCellValue(row.get(columnIndex));
  }

  private String canonicalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
  }

  public record FaceParseResult(
      int headerRowIndex,
      List<FaceParsedRow> rows,
      List<String> warnings) {
  }

  public record FaceParsedRow(
      int rowNumber,
      String firstName,
      String lastName,
      String employeeId,
      String department,
      String dateText,
      String weekday,
      String recordsText,
      ObjectNode rawPayload,
      String parseStatus,
      String parseError) {
  }

  public record FingerprintParseResult(
      String fileFormat,
      List<FingerprintParsedRow> rows,
      List<String> warnings,
      FingerprintImportMetadata metadata) {
  }

  public record FingerprintParsedRow(
      int rowNumber,
      String empNo,
      String epfNo,
      String name,
      String designation,
      String department,
      String employeeCategory,
      String dateText,
      String timeInText,
      String timeOutText,
      String lateEarlyText,
      String dayText,
      String otText,
      String leaveType,
      String leaveDaysTotalText,
      String nopayDaysTotalText,
      String otherLeaveDaysText,
      ObjectNode rawPayload,
      String parseStatus,
      String parseError) {
  }

  public record FingerprintImportMetadata(
      String companyName,
      String companyAddress,
      String companyPhone,
      String reportTitle,
      String reportScope,
      String reportDateFromText,
      String reportDateToText) {

    public static FingerprintImportMetadata empty() {
      return new FingerprintImportMetadata(null, null, null, null, null, null, null);
    }
  }

  private record FingerprintEmployeeContext(
      String empNo,
      String epfNo,
      String name,
      String designation,
      String department) {
  }

  private record FingerprintIdentitySlice(
      String empNo,
      String epfNo,
      String name,
      String designation,
      String department) {
  }

  private record FingerprintAttendanceSlice(
      String dateText,
      String timeInText,
      String timeOutText,
      String lateEarlyText,
      String dayText,
      String otText,
      String leaveType,
      String leaveDaysTotalText,
      String nopayDaysTotalText,
      String otherLeaveDaysText) {
  }

  private record AnchorColumn(String key, String label) {
  }

  private record Anchor(String key, String label, float x) {
  }

  private record PositionedText(String text, float x, float y) {
  }

  private record RowGroup(float y, List<PositionedText> items) {
  }

  private static final class PositionAwareStripper extends PDFTextStripper {

    private final List<PositionedText> items = new ArrayList<>();

    private PositionAwareStripper() throws IOException {
      super();
      setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
      String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
      if (normalized.isBlank() || textPositions == null || textPositions.isEmpty()) {
        return;
      }
      TextPosition anchor = textPositions.get(0);
      items.add(new PositionedText(normalized, anchor.getXDirAdj(), anchor.getYDirAdj()));
    }

    private List<PositionedText> getItems() {
      return items;
    }
  }

  private static final class StreamSupport {

    private StreamSupport() {
    }

    private static List<String> values(String... values) {
      return values == null ? List.of() : Arrays.asList(values);
    }
  }
}
