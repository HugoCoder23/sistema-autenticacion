package com.portafolio.auth.exception;

import org.springframework.http.HttpStatus;

/** Límite de intentos superado. Se aplica por email exista o no la cuenta, así que no revela nada. */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                "Demasiados intentos fallidos. Intenta de nuevo en " + Math.max(1, (retryAfterSeconds + 59) / 60)
                        + " minuto(s)");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
