package eu.sonetas.aurevanta.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import eu.sonetas.aurevanta.auth.problem.AuthProblemException;
import eu.sonetas.aurevanta.auth.problem.FieldProblem;
import eu.sonetas.aurevanta.auth.problem.TooManyRequestsException;
import jakarta.validation.ConstraintViolation;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
 *
 * <p>
 * Scoped by package rather than by class, so every controller serving {@code /api/auth}
 * is covered — including ones in subpackages, which a selector naming a single class
 * would have silently left answering Boot's default problem documents instead.
 */
@RestControllerAdvice(basePackages = "eu.sonetas.aurevanta.auth")
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {

	/**
	 * Constraint names as Bean Validation spells them, mapped to the codes this API
	 * publishes. A mapping rather than a lower-cased name, because these are part of the
	 * contract: they should stay put even if a constraint is renamed, and read like the
	 * other codes in this application.
	 */
	private static final Map<String, String> CONSTRAINT_CODES = Map.of("NotBlank", "not_blank", "Size", "size", "Email",
			"email");

	/** What an unmapped constraint becomes, so a new one degrades rather than leaks. */
	private static final String UNKNOWN_CONSTRAINT = "invalid";

	/**
	 * Which complaint wins when one field breaks several rules at once.
	 *
	 * <p>
	 * An empty password fails {@code @NotBlank} and {@code @Size(min = 12)} together, and
	 * Bean Validation hands back violations in a set whose iteration order genuinely
	 * varies from request to request — so without an order stated here, the same empty
	 * field would be told "this cannot be empty" one time and "use between 12 and 72
	 * characters" the next.
	 *
	 * <p>
	 * Presence comes first: an empty field needs to hear that it is empty, and the rules
	 * about shape only start to mean something once there is something to shape. Anything
	 * unlisted ranks last, so a mapped constraint always beats a generic {@code invalid}.
	 */
	private static final List<String> CODE_PRECEDENCE = List.of("not_blank", "size", "max_size", "email");

	/** Every domain failure describes itself, so one branch covers all of them. */
	@ExceptionHandler(AuthProblemException.class)
	ProblemDetail handleAuthProblem(AuthProblemException ex) {
		return problem(ex.getStatus(), ex.getTitle(), ex.getMessage(), ex.getCode());
	}

	/**
	 * The one failure that needs a header as well as a body, so it is the one that cannot
	 * go through the branch above. Spring picks the more specific handler, so this wins
	 * for a rate-limit refusal and nothing else changes.
	 *
	 * <p>
	 * {@code Retry-After} is what makes the refusal actionable: a client told only "too
	 * many" can do nothing but guess, and a bad guess means retrying in a loop against
	 * the very limit that was trying to calm things down.
	 */
	@ExceptionHandler(TooManyRequestsException.class)
	ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
		return ResponseEntity.status(ex.getStatus())
			.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfter().toSeconds()))
			.body(problem(ex.getStatus(), ex.getTitle(), ex.getMessage(), ex.getCode()));
	}

	/**
	 * Reports which constraint each field failed, never the English Bean Validation
	 * generated for it. The client owns the wording; all it needs from here is what went
	 * wrong and the numbers to put in its own sentence.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleInvalidRequest(MethodArgumentNotValidException ex) {
		Map<String, FieldProblem> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.merge(error.getField(), fieldProblem(error), AuthExceptionHandler::moreTelling);
		}
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Some fields need attention",
				"validation_failed");
		problem.setProperty("errors", errors);
		return problem;
	}

	/**
	 * Picks by {@link #CODE_PRECEDENCE}, so the answer does not depend on arrival order.
	 */
	private static FieldProblem moreTelling(FieldProblem existing, FieldProblem candidate) {
		return (precedence(candidate.code()) < precedence(existing.code())) ? candidate : existing;
	}

	private static int precedence(String code) {
		int rank = CODE_PRECEDENCE.indexOf(code);
		return (rank >= 0) ? rank : Integer.MAX_VALUE;
	}

	private static FieldProblem fieldProblem(FieldError error) {
		Map<String, Object> attributes = attributes(error);
		String constraint = error.getCode();
		String code = (constraint != null) ? CONSTRAINT_CODES.getOrDefault(constraint, UNKNOWN_CONSTRAINT)
				: UNKNOWN_CONSTRAINT;
		return new FieldProblem(boundedAboveOnly(code, attributes) ? "max_size" : code, attributes);
	}

	/**
	 * {@code @Size(max = n)} reports a lower bound of zero, and "use between 0 and 200
	 * characters" is not a sentence worth putting in front of anyone. A constraint that
	 * only bounds the length above gets its own code, so the client can say "no more
	 * than" instead of inventing a range.
	 */
	private static boolean boundedAboveOnly(String code, Map<String, Object> attributes) {
		return "size".equals(code) && attributes.get("min") instanceof Number min && min.intValue() == 0;
	}

	/**
	 * The bounds a client interpolates into its own message, read from the constraint by
	 * name rather than from {@code FieldError.getArguments()} by position — that array is
	 * ordered by attribute name, so reading it positionally would silently swap
	 * {@code min} and {@code max}.
	 *
	 * <p>
	 * Only numeric attributes are published, because bounds are the whole of what a
	 * message interpolates today. Everything else describes how validation is implemented
	 * — a regular expression, the message template, the constraint's groups — which is
	 * ours to know and not the client's to render. A constraint carrying something else
	 * worth showing widens this, with a test for it.
	 */
	private static Map<String, Object> attributes(FieldError error) {
		if (!error.contains(ConstraintViolation.class)) {
			// Not a Bean Validation failure: a type mismatch during binding, for
			// instance.
			return Map.of();
		}
		// Held as a wildcard rather than raw, or the attribute map erases to Object keys.
		ConstraintViolation<?> violation = error.unwrap(ConstraintViolation.class);
		Map<String, Object> attributes = new TreeMap<>();
		violation.getConstraintDescriptor().getAttributes().forEach((name, value) -> {
			if (value instanceof Number) {
				attributes.put(name, value);
			}
		});
		return attributes;
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
