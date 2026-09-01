package auth.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import auth.dto.UpdateUserRequest;
import auth.dto.UserResponse;
import auth.model.UserType;
import auth.security.AuthPrincipal;
import auth.service.UserService;
import jakarta.validation.Valid;

/** Every self-or-admin operation: an ADMIN may act on any user, a REGULAR user only on themselves. */
@RestController
@RequestMapping("/users")
public class UserController {

    private static final String SELF_OR_ADMIN = "hasRole('ADMIN') or #id == authentication.principal.userId()";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** The caller's own profile — no id needed, since the caller already knows who they are. */
    @GetMapping("/me")
    public UserResponse me() {
        var principal = (AuthPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return UserResponse.from(userService.getById(principal.userId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(SELF_OR_ADMIN)
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.from(userService.getById(id));
    }

    /**
     * Admin-only listing. Simplifies the Python reference's rule (a
     * non-admin may also list, but only filtered to type=regular) —
     * that reads as defense-in-depth against building a general
     * directory, not a capability worth the extra complexity here.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> list(
            @RequestParam(required = false) UserType type,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return userService.list(type, firstName, lastName, limit, offset).stream()
                .map(UserResponse::from)
                .toList();
    }

    @PatchMapping("/{id}")
    @PreAuthorize(SELF_OR_ADMIN)
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SELF_OR_ADMIN)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
