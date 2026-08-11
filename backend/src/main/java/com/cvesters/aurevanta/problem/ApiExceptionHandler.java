package com.cvesters.aurevanta.problem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

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
 * Turns a failure anywhere in this application into an RFC 9457 problem response. Each
 * carries a stable {@code code} so the client can react, and translate, without parsing
 * prose.
 *
 * <p>
 * Ordered ahead of Boot's own problem-detail advice, which would otherwise answer
 * validation failures first and drop the per-field detail the sign-up form needs.
 *
 * <p>
 * Deliberately unscoped: it covers every controller, not a named package. It began as a
 * selector naming a single class, was widened to a package when a second controller
 * appeared under {@code auth}, and would have had to be widened again the moment
 * invitations arrived outside it — a {@code 429} raised there losing its {@code code} and
 * its {@code Retry-After} and arriving as Boot's default error. Each of those selectors
 * was also a package name written as a string, which is a thing that can go quietly
 * stale: one did, when the root package was renamed, and every problem document in the
 * application silently became Boot's default. There is nothing to keep in step now.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {

	/**
	 * Constraint names as Bean Validation spells them, mapped to the codes this API
	 * publishes. A mapping rather than a lower-cased name, because these are part of the
	 * contract: they should stay put even if a constraint is renamed, and read like the
	 * other codes in this application.
	 */
	private static final Map<String, String> CONSTRAINT_CODES = Map.of("NotBlank", "not_blank", "NotNull", "not_null",
			"Size", "size", "Email", "email", "Pattern", "pattern");

	/** What an unmapped constraint becomes, so a new one degrades rather than leaks. */
	private static final String UNKNOWN_CONSTRAINT = "invalid";

	static final String UNIQUE_SLUG_INDEX = "uq_tenants_slug";

	static final String UNIQUE_EMAIL_INDEX = "uq_users_email";

	static final String UNIQUE_PENDING_INVITATION_INDEX = "uq_invitations_pending";

	/**
	 * Unique indexes whose violation has a better answer than "something conflicted", and
	 * the answer each one has.
	 *
	 * <p>
	 * Every entry here is a race. Each of these has a pre-check that produces the
	 * readable refusal in the ordinary case; this is for the pair who get past that check
	 * in the same instant and meet the index instead. **They answer with the same code
	 * the pre-check would have used**, so a caller cannot tell the race from the ordinary
	 * case — which is right, because there is nothing they could usefully do with the
	 * difference.
	 *
	 * <p>
	 * Named here rather than beside each pre-check because this is the one place that
	 * turns a failure into an answer, and a second place that read constraint names would
	 * be a second place to forget one.
	 *
	 * <p>
	 * These names belong to migrations rather than to this class, and nothing else would
	 * notice them drifting apart — renaming an index would quietly turn a specific
	 * refusal into a generic one. {@code ConstraintNamesTests} is what fails when they
	 * stop agreeing.
	 */
	private static final Map<String, Supplier<ApiProblemException>> CONSTRAINT_CONFLICTS = Map.of(UNIQUE_SLUG_INDEX,
			() -> new SlugTakenException(null), UNIQUE_EMAIL_INDEX, EmailAlreadyRegisteredException::new,
			UNIQUE_PENDING_INVITATION_INDEX, InvitationAlreadyPendingException::new);

	/**
	 * Every index name this advice reads, for the test that pins them to the database.
	 */
	static final Set<String> KNOWN_CONSTRAINTS = CONSTRAINT_CONFLICTS.keySet();

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
	private static final List<String> CODE_PRECEDENCE = List.of("not_blank", "not_null", "size", "max_size", "email",
			"pattern");

	/** Every domain failure describes itself, so one branch covers all of them. */
	@ExceptionHandler(ApiProblemException.class)
	ProblemDetail handleProblem(ApiProblemException ex) {
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
	 * The other failure that answers with more than a code, and so takes a branch of its
	 * own.
	 *
	 * <p>
	 * A refusal that names a free alternative is what this API offers instead of an
	 * endpoint for asking whether a handle is free — which would be a surface anybody
	 * could walk to enumerate the organisations that exist. The one caller that cannot
	 * offer an alternative sends none rather than an empty one, so a client can tell
	 * "here is another" from "try something else".
	 */
	@ExceptionHandler(SlugTakenException.class)
	ProblemDetail handleSlugTaken(SlugTakenException ex) {
		ProblemDetail problem = problem(ex.getStatus(), ex.getTitle(), ex.getMessage(), ex.getCode());
		if (ex.getSuggested() != null) {
			problem.setProperty("suggested", ex.getSuggested());
		}
		return problem;
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
			errors.merge(error.getField(), fieldProblem(error), ApiExceptionHandler::moreTelling);
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
	 * Two writers racing on the same unique index: one wins, and the other arrives here.
	 *
	 * <p>
	 * Reported as whatever the pre-check would have said, where the index is one this
	 * advice knows — a handle taken in the moment between the check and the write is
	 * still a handle taken, and telling somebody "something conflicted" about a field
	 * they typed would be a refusal they could not act on. What none of them can do is
	 * offer an alternative: the transaction is already lost, so there is nothing left to
	 * ask the database for.
	 *
	 * <p>
	 * Anything else falls through to a neutral answer that names no field. It used to
	 * name two — an email address or an organisation name — which was true when a
	 * registration was the only thing that could arrive here, and became wrong the moment
	 * this advice covered every controller. A refusal that guesses at what the caller was
	 * doing is worse than one that admits it does not know.
	 *
	 * <p>
	 * The violation's own message can name database objects, so it is read for a
	 * constraint name and never echoed back.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleConflict(DataIntegrityViolationException ex) {
		return CONSTRAINT_CONFLICTS.entrySet()
			.stream()
			.filter((conflict) -> violated(ex, conflict.getKey()))
			.findFirst()
			.map((conflict) -> handleProblem(conflict.getValue().get()))
			.orElseGet(() -> problem(HttpStatus.CONFLICT, "Conflict",
					"Something else changed at the same moment. Try again", "conflict"));
	}

	/**
	 * Package-visible so the mapping is testable without provoking a race to reach it.
	 */
	static boolean violated(DataIntegrityViolationException ex, String constraint) {
		String reported = ex.getMostSpecificCause().getMessage();
		return (reported != null) && reported.contains(constraint);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}

}
