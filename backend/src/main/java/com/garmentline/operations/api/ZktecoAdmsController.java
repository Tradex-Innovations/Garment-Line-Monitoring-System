package com.garmentline.operations.api;

import com.garmentline.operations.zkteco.ZktecoAdmsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iclock")
public class ZktecoAdmsController {

  private final ZktecoAdmsService zktecoAdmsService;

  public ZktecoAdmsController(ZktecoAdmsService zktecoAdmsService) {
    this.zktecoAdmsService = zktecoAdmsService;
  }

  @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
  public String cdataOptions(
      @RequestParam MultiValueMap<String, String> query,
      @RequestHeader HttpHeaders headers,
      HttpServletRequest request) {
    return zktecoAdmsService.options(query, headers, clientIp(request));
  }

  @PostMapping(value = "/cdata", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
  public String cdata(
      @RequestParam MultiValueMap<String, String> query,
      @RequestHeader HttpHeaders headers,
      @RequestBody(required = false) String body,
      HttpServletRequest request) {
    return zktecoAdmsService.receiveCdata(query, headers, body, clientIp(request)).acknowledgement();
  }

  @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
  public String getRequest(
      @RequestParam MultiValueMap<String, String> query,
      @RequestHeader HttpHeaders headers,
      HttpServletRequest request) {
    return zktecoAdmsService.getRequest(query, headers, clientIp(request));
  }

  @PostMapping(value = "/devicecmd", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
  public String deviceCommandAck(
      @RequestParam MultiValueMap<String, String> query,
      @RequestHeader HttpHeaders headers,
      @RequestBody(required = false) String body,
      HttpServletRequest request) {
    return zktecoAdmsService.deviceCommandAck(query, headers, body, clientIp(request));
  }

  @PostMapping(value = "/registry", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
  public String registry(
      @RequestParam MultiValueMap<String, String> query,
      @RequestHeader HttpHeaders headers,
      @RequestBody(required = false) String body,
      HttpServletRequest request) {
    zktecoAdmsService.registry(query, headers, body, clientIp(request));
    return "OK";
  }

  @GetMapping(value = "/registry", produces = MediaType.TEXT_PLAIN_VALUE)
  public String registryGet(
      @RequestParam MultiValueMap<String, String> query,
      @RequestHeader HttpHeaders headers,
      HttpServletRequest request) {
    zktecoAdmsService.registry(query, headers, null, clientIp(request));
    return "OK";
  }

  @PostMapping(value = "/push", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
  public String pushAlias(
      @RequestParam MultiValueMap<String, String> query,
      @RequestHeader HttpHeaders headers,
      @RequestBody(required = false) String body,
      HttpServletRequest request) {
    return zktecoAdmsService.receiveCdata(query, headers, body, clientIp(request)).acknowledgement();
  }

  @GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> ping() {
    return Map.of("ok", true, "mode", "zkteco-adms-push");
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",", 2)[0].trim();
    }
    return request.getRemoteAddr();
  }
}
