package com.DartsCounter.DartsCounter_backend;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Konten verwalten, alles nur für Admins
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    record CreateRequest(String name, String role) {}

    record RoleRequest(String role) {}

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountService.Account> list(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        accountService.requireAdmin(auth);
        return accountService.list();
    }

    @PostMapping
    public ResponseEntity<AccountService.Created> create(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                                         @RequestBody CreateRequest request) {
        accountService.requireAdmin(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request.name(), request.role()));
    }

    @PostMapping("/{id}/code")
    public Map<String, String> resetCode(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                         @PathVariable("id") String id) {
        accountService.requireAdmin(auth);
        return Map.of("code", accountService.resetCode(id));
    }

    @PatchMapping("/{id}")
    public AccountService.Account setRole(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                          @PathVariable("id") String id, @RequestBody RoleRequest request) {
        accountService.requireAdmin(auth);
        return accountService.setRole(id, request.role());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                       @PathVariable("id") String id) {
        AccountService.Account admin = accountService.requireAdmin(auth);
        accountService.delete(admin.id(), id);
        return ResponseEntity.noContent().build();
    }
}
