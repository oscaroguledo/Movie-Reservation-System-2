package auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import auth.dto.LoginRequest;
import auth.dto.LoginResponse;
import auth.dto.RegisterRequest;
import auth.dto.UserResponse;
import auth.model.User;
import auth.security.AuthPrincipal;
import auth.security.JwtProvider;
import auth.service.TokenService;
import auth.service.UserService;
import auth.web.InvalidCredentialsException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserService userService, TokenService tokenService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    /**
     * Admin-only: create another admin account. Unlike {@link #register},
     * there is deliberately no client-controlled "type" field anywhere in
     * {@link RegisterRequest} — the Python reference's plain /register
     * endpoint accepts a client-supplied type and never overrides it,
     * which lets anyone self-register as admin. This endpoint is the only
     * way to grant admin here, and only an existing admin can call it.
     */
    @PostMapping("/register/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> registerAdmin(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService
                .findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        JwtProvider.IssuedToken issued = tokenService.issueAccessToken(user);
        return new LoginResponse(issued.token(), issued.expiresAt());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        var principal = (AuthPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        tokenService.revoke(principal.jti(), principal.expiresAt());
        return ResponseEntity.noContent().build();
    }
}
