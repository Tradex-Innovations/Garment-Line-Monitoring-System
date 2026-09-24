package com.tradex.unionnorth.employee.controller;

import com.tradex.unionnorth.employee.domain.CadreStatus;
import com.tradex.unionnorth.employee.dto.CreateEmployeeRequest;
import com.tradex.unionnorth.employee.dto.EmployeeResponse;
import com.tradex.unionnorth.employee.dto.EmployeePhotoResponse;
import com.tradex.unionnorth.employee.dto.EmployeeSearchCriteria;
import com.tradex.unionnorth.employee.dto.UpdateEmployeeRequest;
import com.tradex.unionnorth.employee.service.EmployeeService;
import com.tradex.unionnorth.employee.service.EmployeePhotoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeePhotoService employeePhotoService;

    public EmployeeController(EmployeeService employeeService, EmployeePhotoService employeePhotoService) {
        this.employeeService = employeeService;
        this.employeePhotoService = employeePhotoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL') or hasRole('SYSTEM_ADMIN')")
    public Page<EmployeeResponse> listEmployees(
            @RequestParam(required = false) CadreStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "employeeNumber") Pageable pageable) {
        return employeeService.listEmployees(new EmployeeSearchCriteria(status, search), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL') or hasRole('SYSTEM_ADMIN')")
    public EmployeeResponse getEmployee(@PathVariable UUID id) {
        return employeeService.getEmployee(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_CREATE') or hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse response = employeeService.createEmployee(request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_UPDATE') or hasRole('SYSTEM_ADMIN')")
    public EmployeeResponse updateEmployee(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeService.updateEmployee(id, request);
    }

    @PostMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EMPLOYEE_UPDATE') or hasRole('SYSTEM_ADMIN')")
    public EmployeePhotoResponse uploadPhoto(
            @PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return employeePhotoService.upload(id, file);
    }

    @GetMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL') or hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Resource> getPhoto(@PathVariable UUID id) {
        var photo = employeePhotoService.load(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(photo.metadata().contentType()))
                .contentLength(photo.metadata().sizeBytes())
                .body(photo.resource());
    }
}
