package de.zbidi.jobtracker.common;

import java.util.List;

import de.zbidi.jobtracker.jobapplication.DuplicateApplicationException;
import de.zbidi.jobtracker.jobapplication.InvalidStatusTransitionException;
import de.zbidi.jobtracker.jobapplication.RecruiterConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 {@link ProblemDetail} responses.
 * The base class already covers malformed JSON, unknown enum values and type mismatches (400).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	public record FieldViolation(String field, String message) {
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
				.toList();
		ProblemDetail problem = ex.getBody();
		problem.setDetail("Validation failed");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	/** e.g. {@code ?sort=doesNotExist} */
	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handleUnknownProperty(PropertyReferenceException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(InvalidStatusTransitionException.class)
	ProblemDetail handleInvalidTransition(InvalidStatusTransitionException ex) {
		ProblemDetail problem = conflict("Invalid status transition", ex.getMessage());
		problem.setProperty("currentStatus", ex.getCurrentStatus());
		problem.setProperty("requestedStatus", ex.getRequestedStatus());
		return problem;
	}

	@ExceptionHandler(DuplicateApplicationException.class)
	ProblemDetail handleDuplicate(DuplicateApplicationException ex) {
		return conflict("Duplicate application", ex.getMessage());
	}

	@ExceptionHandler(RecruiterConflictException.class)
	ProblemDetail handleRecruiterConflict(RecruiterConflictException ex) {
		return conflict("Recruiter conflict", ex.getMessage());
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		return conflict("Concurrent modification",
				"The resource was changed by someone else. Reload it and try again.");
	}

	/** Safety net: a unique index fired because two requests passed the service checks at the same time. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		return conflict("Data conflict", "The request conflicts with existing data.");
	}

	private static ProblemDetail conflict(String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
		problem.setTitle(title);
		return problem;
	}

}
