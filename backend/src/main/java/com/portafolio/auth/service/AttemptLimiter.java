package com.portafolio.auth.service;

import com.portafolio.auth.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos con ventana deslizante, en memoria. Suficiente para una instancia; con varias réplicas
 * se reemplazaría por Redis (misma interfaz). Las claves son por email (exista o no la cuenta) y por IP.
 */
@Component
public class AttemptLimiter {

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration window;

    public AttemptLimiter(Clock clock, AppProperties props) {
        this.clock = clock;
        this.window = Duration.ofMinutes(props.rateLimit().windowMinutes());
    }

    /** Segundos que faltan para poder intentar de nuevo, o 0 si está permitido. */
    public long retryAfterSeconds(String key, int max) {
        Instant now = clock.instant();
        Deque<Instant> deque = attempts.get(key);
        if (deque == null) return 0;
        synchronized (deque) {
            prune(deque, now);
            if (deque.size() < max) return 0;
            Instant oldest = deque.peekFirst();
            return Math.max(1, Duration.between(now, oldest.plus(window)).toSeconds());
        }
    }

    public void record(String key) {
        Instant now = clock.instant();
        Deque<Instant> deque = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque, now);
            deque.addLast(now);
        }
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    /** Elimina entradas vencidas para que el mapa no crezca indefinidamente. */
    public void purgeExpired() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }

    public void clear() {
        attempts.clear();
    }

    private void prune(Deque<Instant> deque, Instant now) {
        Instant limit = now.minus(window);
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(limit)) deque.pollFirst();
    }
}
