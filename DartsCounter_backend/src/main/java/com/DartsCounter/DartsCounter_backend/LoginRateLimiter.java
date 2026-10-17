package com.DartsCounter.DartsCounter_backend;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// Begrenzt Fehlversuche pro Spielername (nicht pro IP, IP-Adressen werden nicht gespeichert).
// Liegt nur im Speicher: nach einem Neustart beginnt die Zählung neu.
class LoginRateLimiter {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_NAMES = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new HashMap<>();

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    synchronized boolean isBlocked(String nameKey) {
        Deque<Instant> list = failures.get(nameKey);
        if (list == null) return false;
        prune(list);
        if (list.isEmpty()) failures.remove(nameKey);
        return list.size() >= MAX_FAILURES;
    }

    synchronized void recordFailure(String nameKey) {
        // Schutz gegen sehr viele ausgedachte Namen: alte Einträge aufräumen
        if (failures.size() >= MAX_TRACKED_NAMES) {
            failures.values().forEach(this::prune);
            failures.values().removeIf(Deque::isEmpty);
        }
        failures.computeIfAbsent(nameKey, k -> new ArrayDeque<>()).addLast(clock.instant());
    }

    synchronized void reset(String nameKey) {
        failures.remove(nameKey);
    }

    private void prune(Deque<Instant> list) {
        Instant limit = clock.instant().minus(WINDOW);
        while (!list.isEmpty() && !list.peekFirst().isAfter(limit)) list.removeFirst();
    }
}
