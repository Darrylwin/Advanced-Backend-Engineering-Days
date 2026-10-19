package com.darcode.day01.web;

import com.darcode.day01.domain.Payment;
import com.darcode.day01.exception.BusinessException;
import com.darcode.day01.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException exception) {
        return problem(statusFor(exception.getErrorCode()), exception.getErrorCode(), exception.getMessage());
    }

    /**
     * Last line of defense: the unique constraint on the idempotency key fired, meaning another
     * request with the same key committed first. The client should simply retry.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrityViolation(DataIntegrityViolationException exception) {
        if (isIdempotencyKeyViolation(exception)) {
            return problem(HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_KEY_IN_USE,
                    "A request with this Idempotency-Key is being processed. Retry shortly.");
        }
        return problem(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT,
                "The request conflicts with the current state of the data.");
    }

    private static boolean isIdempotencyKeyViolation(DataIntegrityViolationException ex) {
        return ex.getCause() instanceof ConstraintViolationException violation
                && Payment.UNIQUE_IDEMPOTENCY_KEY.equals(violation.getConstraintName());
    }

    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case IDEMPOTENCY_KEY_REQUIRED -> HttpStatus.BAD_REQUEST;
            case INSUFFICIENT_FUNDS, IDEMPOTENCY_KEY_REFUSED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case IDEMPOTENCY_KEY_IN_USE, DATA_CONFLICT -> HttpStatus.CONFLICT;
        };
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return problem;
    }
}
