package io.otelsandbox.orders.web;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.otelsandbox.orders.pricing.PricingUnavailableException;
import io.otelsandbox.orders.pricing.UnknownSkuException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler
    ProblemDetail unknownSku(UnknownSkuException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail pricingUnavailable(PricingUnavailableException e) {
        Span.current().recordException(e).setStatus(StatusCode.ERROR, e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
