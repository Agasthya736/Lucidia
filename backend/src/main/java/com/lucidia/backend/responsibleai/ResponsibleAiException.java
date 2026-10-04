package com.lucidia.backend.responsibleai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ResponsibleAiException extends RuntimeException {
    public ResponsibleAiException(String message) {
        super(message);
    }
}
