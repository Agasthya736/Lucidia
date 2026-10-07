package com.lucidia.backend.responsibleai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class ResponsibleAiException extends RuntimeException {
    public ResponsibleAiException(String message) {
        super(message);
    }
}
