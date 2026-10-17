package com.DartsCounter.DartsCounter_backend;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    record SetupRequest(String name) {}

    record LoginRequest(String name, String code) {}

    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    // Frontend fragt beim ersten Start, ob schon ein Konto existiert
    @GetMapping("/setup")
    public Map<String, Boolean> setupNeeded() {
        return Map.of("needed", accountService.setupNeeded());
    }

    @PostMapping("/setup")
    public ResponseEntity<AccountService.Created> setup(@RequestBody SetupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.setup(request.name()));
    }

    @PostMapping("/login")
    public AccountService.LoginResult login(@RequestBody LoginRequest request) {
        return accountService.login(request.name(), request.code());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        accountService.logout(AccountService.bearerToken(authorization));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AccountService.Account me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return accountService.requireAccount(authorization);
    }
}
