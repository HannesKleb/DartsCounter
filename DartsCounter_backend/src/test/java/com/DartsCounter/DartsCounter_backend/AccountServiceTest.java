package com.DartsCounter.DartsCounter_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

class AccountServiceTest {

    // Uhr, die der Test vorstellen kann
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TempDir
    Path dataDir;

    TestClock clock;
    AccountService service;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        service = new AccountService(dataDir, 4, clock);
    }

    private static HttpStatus status(Runnable action) {
        return assertThrows(ApiException.class, action::run).getStatus();
    }

    @Test
    void codeFormat() {
        String code = AccessCodes.generate();
        assertTrue(code.matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}"), code);
        assertEquals("ABCDEFGHJKMN", AccessCodes.normalize(" abcd-efgh jkmn "));
    }

    @Test
    void setupOnlyOnce() {
        assertTrue(service.setupNeeded());
        AccountService.Created admin = service.setup("Hannes");
        assertEquals("admin", admin.account().role());
        assertFalse(service.setupNeeded());
        assertEquals(HttpStatus.CONFLICT, status(() -> service.setup("Zweiter")));
    }

    @Test
    void loginRightAndWrong() {
        String code = service.setup("Hannes").code();

        // Groß-/Kleinschreibung beim Namen und Format beim Code egal
        AccountService.LoginResult result = service.login("hannes", code.toLowerCase().replace("-", " "));
        assertEquals("Hannes", result.account().name());
        assertEquals("Hannes", service.authenticate(result.token()).orElseThrow().name());

        ApiException wrongCode = assertThrows(ApiException.class, () -> service.login("Hannes", "AAAA-AAAA-AAAA"));
        ApiException wrongName = assertThrows(ApiException.class, () -> service.login("Niemand", code));
        assertEquals(HttpStatus.UNAUTHORIZED, wrongCode.getStatus());
        assertEquals(wrongCode.getStatus(), wrongName.getStatus());
        assertEquals(wrongCode.getMessage(), wrongName.getMessage());
    }

    @Test
    void logoutAndExpiry() {
        String code = service.setup("Hannes").code();
        String token = service.login("Hannes", code).token();
        service.logout(token);
        assertTrue(service.authenticate(token).isEmpty());

        String token2 = service.login("Hannes", code).token();
        clock.advance(Duration.ofDays(29));
        assertTrue(service.authenticate(token2).isPresent());
        clock.advance(Duration.ofDays(2));
        assertTrue(service.authenticate(token2).isEmpty());
    }

    @Test
    void resetCodeInvalidatesOldCodeAndSessions() {
        service.setup("Hannes");
        AccountService.Created player = service.create("Max", "player");
        String token = service.login("Max", player.code()).token();

        String newCode = service.resetCode(player.account().id());

        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> service.login("Max", player.code())));
        assertTrue(service.authenticate(token).isEmpty());
        assertEquals("Max", service.login("Max", newCode).account().name());
    }

    @Test
    void adminRules() {
        AccountService.Created admin = service.setup("Hannes");
        String adminId = admin.account().id();

        // letzter Admin: nicht herabstufen, nicht löschen (auch nicht durch einen zweiten Admin)
        assertEquals(HttpStatus.CONFLICT, status(() -> service.setRole(adminId, "player")));
        assertEquals(HttpStatus.CONFLICT, status(() -> service.delete(adminId, adminId)));

        AccountService.Created second = service.create("Lena", "player");
        assertEquals(HttpStatus.CONFLICT, status(() -> service.delete(second.account().id(), adminId)));

        // eigenes Konto nie löschbar
        service.setRole(second.account().id(), "admin");
        assertEquals(HttpStatus.CONFLICT, status(() -> service.delete(adminId, adminId)));

        // mit zwei Admins geht Herabstufen und Löschen des anderen
        service.setRole(adminId, "player");
        service.delete(second.account().id(), adminId);
        assertEquals(1, service.list().size());
    }

    @Test
    void nameRules() {
        service.setup("Hannes");
        assertEquals(HttpStatus.CONFLICT, status(() -> service.create("HANNES", "player")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create("12345678901234567", "player")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create("  ", "player")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create("Max", "chef")));
    }

    @Test
    void rateLimit() {
        String code = service.setup("Hannes").code();
        for (int i = 0; i < 5; i++) {
            assertEquals(HttpStatus.UNAUTHORIZED, status(() -> service.login("Hannes", "AAAA-AAAA-AAAA")));
        }
        // gesperrt, auch mit richtigem Code
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, status(() -> service.login("Hannes", code)));
        // unbekannte Namen werden genauso gesperrt
        for (int i = 0; i < 5; i++) status(() -> service.login("Niemand", code));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, status(() -> service.login("niemand", code)));

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertEquals("Hannes", service.login("Hannes", code).account().name());
    }

    @Test
    void storesOnlyHashes() throws Exception {
        String code = service.setup("Hannes").code();
        String token = service.login("Hannes", code).token();

        String accounts = Files.readString(dataDir.resolve("accounts.json"));
        String sessions = Files.readString(dataDir.resolve("sessions.json"));
        assertFalse(accounts.contains(code) || accounts.contains(AccessCodes.normalize(code)));
        assertFalse(sessions.contains(token));
        assertTrue(sessions.contains(AccountService.sha256(token)));

        // nach Neustart noch da
        AccountService restarted = new AccountService(dataDir, 4, clock);
        assertTrue(restarted.authenticate(token).isPresent());
        assertEquals("Hannes", restarted.login("Hannes", code).account().name());
    }
}
