package com.darcode.day02.web;

import com.darcode.day02.exception.BusinessException;
import com.darcode.day02.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(statusFor(ex.getErrorCode()), ex.getMessage());
        problem.setProperty("code", ex.getErrorCode().name());
        return problem;
    }

    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INSUFFICIENT_FUNDS -> HttpStatus.UNPROCESSABLE_CONTENT;
            case CONCURRENT_MODIFICATION -> HttpStatus.CONFLICT;
        };
    }
}
