package com.garmentline.operations.api;

import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.UserContextService;
import com.garmentline.operations.service.ImportService;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import-batches")
@Validated
public class ImportController {

  private final ImportService importService;
  private final UserContextService userContextService;

  public ImportController(ImportService importService, UserContextService userContextService) {
    this.importService = importService;
    this.userContextService = userContextService;
  }

  @GetMapping
  public List<Map<String, Object>> listImportBatches(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return importService.listImportBatches(user);
  }

  @PostMapping("/upload")
  public Map<String, Object> uploadImportBatch(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam("sourceType") @NotBlank String sourceType,
      @RequestParam("file") MultipartFile file)
      throws IOException {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return importService.uploadAndProcess(sourceType, file, user);
  }

  @PostMapping("/{batchId}/normalize")
  public Map<String, Object> rerunNormalization(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String batchId) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return importService.rerunNormalization(batchId, user);
  }
}
