package com.DartsCounter.DartsCounter_backend;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// Konten und Sitzungen. Gespeichert wird nur: id, Spielername, Rolle, Datum und der BCrypt-Hash des Codes.
// Sitzungen enthalten nur den SHA-256 des Tokens, nie das Token selbst.
@Service
public class AccountService {
    public static final int NAME_MAX = 16;
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_PLAYER = "player";
    static final Duration SESSION_TTL = Duration.ofDays(30);
    static final String LOGIN_FAILED = "Spielername oder Zugangscode stimmt nicht.";

    // nach außen: nie Hash oder Salt
    public record Account(String id, String name, String role, String createdAt) {}

    public record Created(Account account, String code) {}

    public record LoginResult(String token, Account account) {}

    record StoredAccount(String id, String name, String role, String createdAt, String codeHash) {
        Account toPublic() {
            return new Account(id, name, role, createdAt);
        }

        StoredAccount withRole(String newRole) {
            return new StoredAccount(id, name, newRole, createdAt, codeHash);
        }

        StoredAccount withCodeHash(String newHash) {
            return new StoredAccount(id, name, role, createdAt, newHash);
        }
    }

    record Session(String tokenHash, String accountId, long expiresAt) {}

    private final Path accountsFile;
    private final Path sessionsFile;
    private final BCryptPasswordEncoder encoder;
    private final Clock clock;
    private final LoginRateLimiter rateLimiter;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SecureRandom random = new SecureRandom();
    // Vergleichs-Hash für unbekannte Namen, damit die Antwortzeit gleich bleibt
    private final String dummyHash;

    private final List<StoredAccount> accounts;
    private final List<Session> sessions;

    @Autowired
    public AccountService(@Value("${dartscounter.data-dir:${user.home}/dartscounter-data}") String dataDir,
                          @Value("${dartscounter.auth.bcrypt-strength:10}") int bcryptStrength) {
        this(Path.of(dataDir), bcryptStrength, Clock.systemUTC());
    }

    AccountService(Path dataDir, int bcryptStrength, Clock clock) {
        this.accountsFile = dataDir.resolve("accounts.json");
        this.sessionsFile = dataDir.resolve("sessions.json");
        this.encoder = new BCryptPasswordEncoder(bcryptStrength, random);
        this.clock = clock;
        this.rateLimiter = new LoginRateLimiter(clock);
        this.dummyHash = encoder.encode(AccessCodes.normalize(AccessCodes.generate()));
        this.accounts = load(accountsFile, new TypeReference<List<StoredAccount>>() {});
        this.sessions = load(sessionsFile, new TypeReference<List<Session>>() {});
    }

    /********* erster Start *********/

    public synchronized boolean setupNeeded() {
        return accounts.isEmpty();
    }

    public synchronized Created setup(String name) {
        if (!accounts.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "Es gibt schon Konten.");
        return createLocked(name, ROLE_ADMIN);
    }

    /********* Anmeldung *********/

    public LoginResult login(String name, String code) {
        String nameKey = nameKey(name);
        if (rateLimiter.isBlocked(nameKey)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Zu viele Fehlversuche. Bitte in 15 Minuten erneut versuchen.");
        }

        StoredAccount account;
        synchronized (this) {
            account = findByName(nameKey).orElse(null);
        }
        // BCrypt außerhalb der Sperre, damit langsame Anmeldungen andere Anfragen nicht blockieren.
        // Auch bei unbekanntem Namen wird ein Hash geprüft: gleiche Antwortzeit, gleiche Meldung.
        boolean codeOk = encoder.matches(AccessCodes.normalize(code), account != null ? account.codeHash() : dummyHash);
        if (account == null || !codeOk) {
            rateLimiter.recordFailure(nameKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED);
        }
        rateLimiter.reset(nameKey);

        synchronized (this) {
            // Konto könnte inzwischen gelöscht oder der Code neu gesetzt worden sein
            StoredAccount current = findById(account.id()).orElse(null);
            if (current == null || !current.codeHash().equals(account.codeHash())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED);
            }
            String token = newToken();
            long expiresAt = clock.millis() + SESSION_TTL.toMillis();
            sessions.add(new Session(sha256(token), current.id(), expiresAt));
            saveSessions();
            return new LoginResult(token, current.toPublic());
        }
    }

    public synchronized void logout(String token) {
        if (token == null) return;
        String hash = sha256(token);
        if (sessions.removeIf(s -> s.tokenHash().equals(hash))) saveSessions();
    }

    // Konto zum Token oder leer, wenn ungültig oder abgelaufen
    public synchronized Optional<Account> authenticate(String token) {
        if (token == null || token.isEmpty()) return Optional.empty();
        String hash = sha256(token);
        long now = clock.millis();
        return sessions.stream()
                .filter(s -> s.tokenHash().equals(hash) && s.expiresAt() > now)
                .findFirst()
                .flatMap(s -> findById(s.accountId()))
                .map(StoredAccount::toPublic);
    }

    // liest "Authorization: Bearer <token>"
    public Account requireAccount(String authorizationHeader) {
        return authenticate(bearerToken(authorizationHeader))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Bitte anmelden."));
    }

    public Account requireAdmin(String authorizationHeader) {
        Account account = requireAccount(authorizationHeader);
        if (!ROLE_ADMIN.equals(account.role())) throw new ApiException(HttpStatus.FORBIDDEN, "Nur für Admins.");
        return account;
    }

    public static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return authorizationHeader.substring(7).trim();
    }

    /********* Verwaltung (nur Admin, geprüft im Controller) *********/

    public synchronized List<Account> list() {
        return accounts.stream()
                .map(StoredAccount::toPublic)
                .sorted(Comparator.comparing(Account::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public synchronized Created create(String name, String role) {
        return createLocked(name, role == null ? ROLE_PLAYER : role);
    }

    // neuer Code: alter Code sofort ungültig, alle Sitzungen des Kontos enden
    public synchronized String resetCode(String id) {
        StoredAccount account = require(id);
        String code = AccessCodes.generate();
        replace(account.withCodeHash(encoder.encode(AccessCodes.normalize(code))));
        saveAccounts();
        if (sessions.removeIf(s -> s.accountId().equals(id))) saveSessions();
        return code;
    }

    public synchronized Account setRole(String id, String role) {
        validateRole(role);
        StoredAccount account = require(id);
        if (ROLE_ADMIN.equals(account.role()) && !ROLE_ADMIN.equals(role) && adminCount() == 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Es muss mindestens ein Admin bleiben.");
        }
        StoredAccount updated = account.withRole(role);
        replace(updated);
        saveAccounts();
        return updated.toPublic();
    }

    public synchronized void delete(String actorId, String id) {
        StoredAccount account = require(id);
        if (account.id().equals(actorId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Du kannst dein eigenes Konto nicht löschen.");
        }
        if (ROLE_ADMIN.equals(account.role()) && adminCount() == 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Der letzte Admin kann nicht gelöscht werden.");
        }
        accounts.removeIf(a -> a.id().equals(id));
        saveAccounts();
        if (sessions.removeIf(s -> s.accountId().equals(id))) saveSessions();
    }

    /********* intern *********/

    private Created createLocked(String name, String role) {
        String trimmed = validateName(name);
        validateRole(role);
        if (findByName(nameKey(trimmed)).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Diesen Spielernamen gibt es schon.");
        }
        String code = AccessCodes.generate();
        StoredAccount account = new StoredAccount(UUID.randomUUID().toString(), trimmed, role,
                clock.instant().toString(), encoder.encode(AccessCodes.normalize(code)));
        accounts.add(account);
        saveAccounts();
        return new Created(account.toPublic(), code);
    }

    private static String validateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Bitte einen Spielernamen eingeben.");
        if (trimmed.length() > NAME_MAX) throw new ApiException(HttpStatus.BAD_REQUEST, "Höchstens " + NAME_MAX + " Zeichen.");
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ungültige Zeichen im Spielernamen.");
        }
        return trimmed;
    }

    private static void validateRole(String role) {
        if (!ROLE_ADMIN.equals(role) && !ROLE_PLAYER.equals(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unbekannte Rolle.");
        }
    }

    // eindeutig ohne Groß-/Kleinschreibung
    private static String nameKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private Optional<StoredAccount> findByName(String nameKey) {
        return accounts.stream().filter(a -> nameKey(a.name()).equals(nameKey)).findFirst();
    }

    private Optional<StoredAccount> findById(String id) {
        return accounts.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    private StoredAccount require(String id) {
        return findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Konto nicht gefunden."));
    }

    private void replace(StoredAccount updated) {
        accounts.replaceAll(a -> a.id().equals(updated.id()) ? updated : a);
    }

    private long adminCount() {
        return accounts.stream().filter(a -> ROLE_ADMIN.equals(a.role())).count();
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // Eine kaputte Datei wird nicht einfach überschrieben, sonst wären alle Konten weg
    private <T> List<T> load(Path file, TypeReference<List<T>> type) {
        try {
            if (!Files.exists(file)) return new ArrayList<>();
            List<T> list = jsonMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), type);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (IOException e) {
            throw new UncheckedIOException("Kann " + file + " nicht lesen", e);
        }
    }

    private void saveAccounts() {
        save(accountsFile, accounts);
    }

    private void saveSessions() {
        long now = clock.millis();
        sessions.removeIf(s -> s.expiresAt() <= now); // abgelaufene gleich mit aufräumen
        save(sessionsFile, sessions);
    }

    private void save(Path file, Object value) {
        try {
            DataStore.writeAtomically(file, jsonMapper.writeValueAsString(value));
            restrictPermissions(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Kann " + file + " nicht schreiben", e);
        }
    }

    // nur der Benutzer, unter dem das Backend läuft, darf die Datei lesen (auf Linux)
    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows: egal
        }
    }
}
