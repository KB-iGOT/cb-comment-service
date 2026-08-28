package com.tarento.commenthub.exception;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class CommentException extends RuntimeException{
    private String code;
    private String message;
    private Integer httpStatusCode;
    private Map<String, String> errors;

    public CommentException() {
    }

    public CommentException(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public CommentException(String code, String message, Integer httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }

    public CommentException(String code, String message, Throwable cause) {
        super(cause);
        this.code = code;
        this.message = message;
    }

    public CommentException(Map<String, String> errors) {
        this.message = errors.toString();
        this.errors = errors;
    }
}