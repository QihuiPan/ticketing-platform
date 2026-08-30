package com.portfolio.ticketing.service;

import com.portfolio.ticketing.api.ApiException;
import com.portfolio.ticketing.api.ApiModels;
import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.UserAccount;
import com.portfolio.ticketing.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional
    public ApiModels.TokenResponse register(ApiModels.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "An account already uses this email");
        }
        UserAccount user = users.save(new UserAccount(
                email,
                passwordEncoder.encode(request.password()),
                DomainTypes.Role.BUYER));
        return response(user);
    }

    @Transactional(readOnly = true)
    public ApiModels.TokenResponse login(ApiModels.LoginRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return response(user);
    }

    private ApiModels.TokenResponse response(UserAccount user) {
        TokenService.IssuedToken issued = tokens.issue(user);
        return new ApiModels.TokenResponse(
                issued.value(),
                issued.expiresAt(),
                new ApiModels.UserResponse(user.getId(), user.getEmail(), user.getRole()));
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
    }
}
