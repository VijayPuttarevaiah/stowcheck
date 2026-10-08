package dev.vijay.stowcheck.api;

import dev.vijay.stowcheck.edifact.EdifactSyntaxException;
import dev.vijay.stowcheck.plan.RunNotFoundException;
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

    @ExceptionHandler(RunNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Dtos.Problem notFound(RunNotFoundException e) {
        return new Dtos.Problem(e.getMessage());
    }
}
