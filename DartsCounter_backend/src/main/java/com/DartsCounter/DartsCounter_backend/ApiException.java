package com.DartsCounter.DartsCounter_backend;

import org.springframework.http.HttpStatus;

// Fehler mit HTTP-Status und kurzer deutscher Meldung, wird als {"message": "..."} zurückgegeben
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
