package com.tarento.commenthub.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Getter
@Setter
@Component
public class CommentException extends RuntimeException{
    private final String code;
    private final String message;
    private final Integer httpStatusCode;
    private final Map<String, String> errors;

    @Autowired
    public CommentException() {
        this.code = null;
        this.message = null;
        this.httpStatusCode = null;
        this.errors = null;
    }

    public CommentException(String code, String message) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = null;
        this.errors = null;
    }

    public CommentException(String code, String message, Integer httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
        this.errors = null;
    }

    public CommentException(Map<String, String> errors) {
        this.code = null;
        this.message = errors.toString();
        this.httpStatusCode = null;
        this.errors = errors;
    }

    public CommentException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
        this.httpStatusCode = null;
        this.errors = null;
    }
}