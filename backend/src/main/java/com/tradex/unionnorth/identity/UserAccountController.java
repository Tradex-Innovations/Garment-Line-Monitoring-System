package com.tradex.unionnorth.identity;

import com.tradex.unionnorth.identity.dto.ChangeUserStatusRequest;
import com.tradex.unionnorth.identity.dto.CreateUserAccountRequest;
import com.tradex.unionnorth.identity.dto.UpdateUserAccountRequest;
import com.tradex.unionnorth.identity.dto.UserAccountPage;
import com.tradex.unionnorth.identity.dto.UserAccountResponse;
import com.tradex.unionnorth.identity.dto.UserActionResponse;
import com.tradex.unionnorth.security.domain.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAccountController {

    private final UserAccountService userAccountService;

    public UserAccountController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public UserAccountPage listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return userAccountService.listUsers(search, page, size);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public List<Role> listRoles(JwtAuthenticationToken authentication) {
        return userAccountService.listAssignableRoles(canManagePrivilegedAccounts(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<UserActionResponse> createUser(
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest,
            @Valid @RequestBody CreateUserAccountRequest request) {
        UserActionResponse response = userAccountService.createUser(
                actorId(authentication),
                servletRequest.getRemoteAddr(),
                canManagePrivilegedAccounts(authentication),
                request);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + response.user().id())).body(response);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public UserAccountResponse updateUser(
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest,
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserAccountRequest request) {
        return userAccountService.updateUser(
                actorId(authentication),
                servletRequest.getRemoteAddr(),
                userId,
                canManagePrivilegedAccounts(authentication),
                request);
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('USER_STATUS_MANAGE')")
    public UserAccountResponse changeStatus(
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest,
            @PathVariable String userId,
            @Valid @RequestBody ChangeUserStatusRequest request) {
        return userAccountService.changeStatus(
                actorId(authentication),
                servletRequest.getRemoteAddr(),
                userId,
                canManagePrivilegedAccounts(authentication),
                request);
    }

    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasAuthority('USER_STATUS_MANAGE')")
    public UserAccountResponse unlockUser(
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest,
            @PathVariable String userId) {
        return userAccountService.unlockUser(
                actorId(authentication),
                servletRequest.getRemoteAddr(),
                userId,
                canManagePrivilegedAccounts(authentication));
    }

    @PutMapping("/{userId}/roles/{role}")
    @PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
    public UserAccountResponse assignRole(
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest,
            @PathVariable String userId,
            @PathVariable Role role) {
        return userAccountService.assignRole(
                actorId(authentication),
                servletRequest.getRemoteAddr(),
                userId,
                canManagePrivilegedAccounts(authentication),
                role);
    }

    @DeleteMapping("/{userId}/roles/{role}")
    @PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
    public UserAccountResponse revokeRole(
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest,
            @PathVariable String userId,
            @PathVariable Role role) {
        return userAccountService.revokeRole(
                actorId(authentication),
                servletRequest.getRemoteAddr(),
                userId,
                canManagePrivilegedAccounts(authentication),
                role);
    }

    @PostMapping("/{userId}/force-password-change")
    @PreAuthorize("hasAuthority('USER_CREDENTIAL_RESET')")
    public UserActionResponse forcePasswordChange(
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest,
            @PathVariable String userId) {
        return userAccountService.forcePasswordChange(
                actorId(authentication),
                servletRequest.getRemoteAddr(),
                userId,
                canManagePrivilegedAccounts(authentication));
    }

    @PostMapping("/{userId}/invite")
    @PreAuthorize("hasAuthority('USER_CREDENTIAL_RESET')")
    public UserActionResponse resendInvite(
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest,
            @PathVariable String userId) {
        return userAccountService.resendInvite(
                actorId(authentication),
                servletRequest.getRemoteAddr(),
                userId,
                canManagePrivilegedAccounts(authentication));
    }

    private String actorId(JwtAuthenticationToken authentication) {
        return authentication.getToken().getSubject();
    }

    private boolean canManagePrivilegedAccounts(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("PRIVILEGED_ACCOUNT_MANAGE"));
    }
}
