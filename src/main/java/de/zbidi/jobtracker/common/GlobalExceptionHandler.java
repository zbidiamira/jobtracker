package de.zbidi.jobtracker.common;

import java.util.List;

import de.zbidi.jobtracker.company.CompanyInUseException;
import de.zbidi.jobtracker.jobapplication.DuplicateApplicationException;
import de.zbidi.jobtracker.jobapplication.InvalidStatusTransitionException;
import de.zbidi.jobtracker.jobapplication.RecruiterConflictException;
import de.zbidi.jobtracker.jobapplication.StaleVersionException;
import de.zbidi.jobtracker.user.EmailAlreadyRegisteredException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
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

	// --- security: login failures come from AuthService, the rest from the SecurityConfig entry points ---

	/** Wrong email or password at login; same message for both. */
	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail handleBadCredentials(BadCredentialsException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	/** Missing, malformed, expired or wrongly signed token. Details stay in the WWW-Authenticate header. */
	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleUnauthenticated(AuthenticationException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Missing or invalid bearer token");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleForbidden(AccessDeniedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You are not allowed to do this");
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ProblemDetail handleEmailTaken(EmailAlreadyRegisteredException ex) {
		return conflict("Email already registered", ex.getMessage());
	}

	@ExceptionHandler(CompanyInUseException.class)
	ProblemDetail handleCompanyInUse(CompanyInUseException ex) {
		return conflict("Company in use", ex.getMessage());
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
		problem.setProperty("allowedStatuses", ex.getAllowedStatuses());
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

	/** The client's version is outdated (detected before writing). */
	@ExceptionHandler(StaleVersionException.class)
	ProblemDetail handleStaleVersion(StaleVersionException ex) {
		ProblemDetail problem = conflict("Concurrent modification", ex.getMessage());
		problem.setProperty("expectedVersion", ex.getExpectedVersion());
		problem.setProperty("currentVersion", ex.getCurrentVersion());
		return problem;
	}

	/** Two requests raced inside the database (detected by {@code @Version} on flush). */
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
