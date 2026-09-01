package auth.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import auth.dto.UpdateUserRequest;
import auth.dto.UserResponse;
import auth.service.UserService;
import jakarta.validation.Valid;

/** Every operation is self-or-admin: an ADMIN may act on any user, a REGULAR user only on themselves. */
@RestController
@RequestMapping("/users")
public class UserController {

    private static final String SELF_OR_ADMIN = "hasRole('ADMIN') or #id == authentication.principal.userId()";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @PreAuthorize(SELF_OR_ADMIN)
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.from(userService.getById(id));
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
