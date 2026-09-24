package com.tradex.unionnorth.organization.controller;

import com.tradex.unionnorth.organization.dto.DepartmentResponse;
import com.tradex.unionnorth.organization.service.DepartmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL') or hasRole('SYSTEM_ADMIN')")
    public Page<DepartmentResponse> listDepartments(@PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return departmentService.listDepartments(pageable);
    }
}
