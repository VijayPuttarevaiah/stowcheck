package dev.vijay.stowcheck.api;

import dev.vijay.stowcheck.edifact.EdifactSyntaxException;
import dev.vijay.stowcheck.plan.RunNotFoundException;
import dev.vijay.stowcheck.review.ReviewUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiErrors {

    /** The file is not EDIFACT at all; nothing could be validated. */
    @ExceptionHandler(EdifactSyntaxException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    Dtos.Problem unreadable(EdifactSyntaxException e) {
        return new Dtos.Problem(e.getMessage());
    }

    /** AI review is off, failed, or had nothing to review. The rule findings are unaffected. */
    @ExceptionHandler(ReviewUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Dtos.Problem reviewUnavailable(ReviewUnavailableException e) {
        return new Dtos.Problem(e.getMessage());
    }

    @ExceptionHandler(RunNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Dtos.Problem notFound(RunNotFoundException e) {
        return new Dtos.Problem(e.getMessage());
    }
}
