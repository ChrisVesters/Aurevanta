package eu.sonetas.aurevanta.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns registration and login failures into RFC 9457 problem responses. Each carries a
 * stable {@code code} so the client can react, and translate, without parsing prose.
 *
 * <p>
 * Ordered ahead of Boot's own problem-detail advice, which would otherwise answer
 * validation failures first and drop the per-field detail the sign-up form needs.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {

	/** Every domain failure describes itself, so one branch covers all of them. */
	@ExceptionHandler(AuthProblemException.class)
	ProblemDetail handleAuthProblem(AuthProblemException ex) {
		return problem(ex.getStatus(), ex.getTitle(), ex.getMessage(), ex.getCode());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleInvalidRequest(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Some fields need attention",
				"validation_failed");
		problem.setProperty("errors", errors);
		return problem;
	}

	/**
	 * Two registrations racing on the same email or organisation name: one wins, the
	 * other trips a unique constraint and is reported the same way the pre-check would
	 * have reported it. The violation's own message can name database objects, so it is
	 * deliberately not echoed back.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleConflict(DataIntegrityViolationException ex) {
		return problem(HttpStatus.CONFLICT, "Already registered",
				"That email address or organisation name was just taken", "registration_conflict");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}

}
