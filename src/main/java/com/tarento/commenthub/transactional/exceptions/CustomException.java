package com.tarento.commenthub.transactional.exceptions;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
public class CustomException extends RuntimeException {
    private String code;
    private String message;
    private HttpStatus httpStatusCode;

    public CustomException() {
    }

    public CustomException(String code, String message, HttpStatus httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }


}
