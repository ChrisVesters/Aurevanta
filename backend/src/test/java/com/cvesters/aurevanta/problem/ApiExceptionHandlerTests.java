package com.cvesters.aurevanta.problem;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.cvesters.aurevanta.problem.AlreadyAMemberException;
import com.cvesters.aurevanta.problem.ApiProblemException;
import com.cvesters.aurevanta.problem.EmailAlreadyRegisteredException;
import com.cvesters.aurevanta.problem.EmailNotVerifiedException;
import com.cvesters.aurevanta.problem.FieldProblem;
import com.cvesters.aurevanta.problem.InvalidCredentialsException;
import com.cvesters.aurevanta.problem.InvalidTokenException;
import com.cvesters.aurevanta.problem.InvitationAlreadyPendingException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.NotAnOwnerException;
import com.cvesters.aurevanta.problem.SlugTakenException;
import com.cvesters.aurevanta.problem.TooManyRequestsException;
import com.cvesters.aurevanta.auth.registration.RegistrationRequest;
import jakarta.validation.Valid;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end-to-end tests drive most of these through the API; covered here are the paths a
 * request cannot readily produce — a constraint violation from two racing registrations,
 * and a field that fails more than one constraint at once.
 */
class ApiExceptionHandlerTests {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	/**
	 * Every one of these is a race that got past a pre-check. Each answers with the code
	 * that pre-check would have used, so a caller cannot tell the two apart — which is
	 * right, because there is nothing they could do with the difference.
	 */
	@ParameterizedTest
	@MethodSource("racedConstraints")
	void reportsARaceAsWhateverTheCheckWouldHaveSaid(String constraint, String code) {
		ProblemDetail problem = this.handler.handleConflict(violating(constraint));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
		assertThat(problem.getProperties()).containsEntry("code", code);
		// The underlying message can name database objects, so it must not be echoed
		// back.
		assertThat(problem.getDetail()).doesNotContain(constraint);
	}

	static Stream<Arguments> racedConstraints() {
		return Stream.of(Arguments.of(ApiExceptionHandler.UNIQUE_SLUG_INDEX, "slug_taken"),
				Arguments.of(ApiExceptionHandler.UNIQUE_EMAIL_INDEX, "email_already_registered"),
				Arguments.of(ApiExceptionHandler.UNIQUE_PENDING_INVITATION_INDEX, "invitation_already_pending"));
	}

	/**
	 * The one that lost its race cannot say what would have been free instead: the
	 * transaction that would have gone and looked is already spent.
	 */
	@Test
	void offersNoAlternativeToAHandleLostInARace() {
		assertThat(this.handler.handleConflict(violating(ApiExceptionHandler.UNIQUE_SLUG_INDEX)).getProperties())
			.doesNotContainKey("suggested");
	}

	/**
	 * A constraint nobody mapped gets an answer that names no field. It used to name two
	 * — an email address or an organisation name — which was true while a registration
	 * was the only thing that could arrive here, and wrong from the moment this advice
	 * covered every controller.
	 */
	@Test
	void admitsItDoesNotKnowRatherThanGuessing() {
		ProblemDetail problem = this.handler.handleConflict(violating("uq_memberships_user_tenant"));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
		assertThat(problem.getProperties()).containsEntry("code", "conflict");
		assertThat(problem.getDetail()).doesNotContain("email").doesNotContain("organisation");
	}

	/**
	 * A driver that said nothing at all is still a violation, and reading a name out of
	 * nothing must not be how this advice falls over. The cause carries no message, which
	 * is the shape that reaches the null check — an exception whose own message is a
	 * string never does.
	 */
	@Test
	void survivesAViolationThatSaysNothingAtAll() {
		DataIntegrityViolationException silent = new DataIntegrityViolationException("could not execute statement",
				new SQLException());

		assertThat(ApiExceptionHandler.violated(silent, ApiExceptionHandler.UNIQUE_SLUG_INDEX)).isFalse();
		assertThat(this.handler.handleConflict(silent).getProperties()).containsEntry("code", "conflict");
	}

	private static DataIntegrityViolationException violating(String constraint) {
		return new DataIntegrityViolationException("could not execute statement",
				new SQLException("duplicate key value violates unique constraint \"" + constraint + "\""));
	}

	/**
	 * An empty password breaks {@code @NotBlank} and {@code @Size(min = 12)} at once, and
	 * Bean Validation hands the two back in an order that genuinely varies between
	 * requests. Both orderings are asserted here because that is the bug: the same empty
	 * field used to be told it could not be empty one time and to use between 12 and 72
	 * characters the next.
	 */
	@Test
	void reportsTheSameConstraintHoweverTheFailuresArrive() throws Exception {
		assertThat(reportedFor("password", "NotBlank", "Size")).isEqualTo("not_blank");
		assertThat(reportedFor("password", "Size", "NotBlank")).isEqualTo("not_blank");
	}

	/** Presence outranks shape: an empty field needs to hear that it is empty. */
	@Test
	void prefersTheConstraintThatSaysTheFieldIsEmpty() throws Exception {
		BeanPropertyBindingResult binding = binding();
		binding.addError(constraintFailure("password", "Size"));
		binding.addError(constraintFailure("password", "NotBlank"));

		ProblemDetail problem = this.handler.handleInvalidRequest(methodArgumentNotValid(binding));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(problem.getProperties()).containsEntry("code", "validation_failed");
		assertThat(errors(problem)).hasSize(1);
		assertThat(errors(problem).get("password").code()).isEqualTo("not_blank");
	}

	/** A constraint we publish a code for beats one that would only say "invalid". */
	@Test
	void prefersAMappedConstraintOverAnUnmappedOne() throws Exception {
		assertThat(reportedFor("password", "DecimalMin", "Size")).isEqualTo("size");
		assertThat(reportedFor("password", "Size", "DecimalMin")).isEqualTo("size");
	}

	private String reportedFor(String field, String... constraints) throws Exception {
		BeanPropertyBindingResult binding = binding();
		for (String constraint : constraints) {
			binding.addError(constraintFailure(field, constraint));
		}
		return errors(this.handler.handleInvalidRequest(methodArgumentNotValid(binding))).get(field).code();
	}

	private static BeanPropertyBindingResult binding() {
		return new BeanPropertyBindingResult(new RegistrationRequest("Acme", "acme", "Ada", "ada@acme.test", ""),
				"registrationRequest");
	}

	@Test
	void reportsEveryFailingField() throws Exception {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(
				new RegistrationRequest("", "acme", "Ada", "nope", "short"), "registrationRequest");
		binding.addError(constraintFailure("organisationName", "NotBlank"));
		binding.addError(constraintFailure("email", "Email"));

		ProblemDetail problem = this.handler.handleInvalidRequest(methodArgumentNotValid(binding));

		assertThat(errors(problem)).containsOnlyKeys("organisationName", "email");
	}

	/**
	 * A constraint nobody has mapped becomes {@code invalid} rather than leaking its
	 * name, so adding one to a request object degrades to a generic message instead of
	 * putting "DecimalMin" in front of a user.
	 */
	@Test
	void reportsAnUnmappedConstraintAsGenericallyInvalid() throws Exception {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(
				new RegistrationRequest("Acme", "acme", "Ada", "ada@acme.test", "a-long-enough-passphrase"),
				"registrationRequest");
		binding.addError(constraintFailure("password", "DecimalMin"));

		ProblemDetail problem = this.handler.handleInvalidRequest(methodArgumentNotValid(binding));

		assertThat(errors(problem).get("password").code()).isEqualTo("invalid");
	}

	/**
	 * Binding can fail before validation runs — a type mismatch carries no constraint.
	 */
	@Test
	void reportsAFailureThatNamesNoConstraintAsGenericallyInvalid() throws Exception {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(
				new RegistrationRequest("Acme", "acme", "Ada", "ada@acme.test", "a-long-enough-passphrase"),
				"registrationRequest");
		binding.addError(new FieldError("registrationRequest", "password", "could not be bound"));

		ProblemDetail problem = this.handler.handleInvalidRequest(methodArgumentNotValid(binding));

		assertThat(errors(problem).get("password").code()).isEqualTo("invalid");
		assertThat(errors(problem).get("password").attributes()).isEmpty();
	}

	/**
	 * One branch serves every domain failure, so what matters is that each exception
	 * describes itself correctly.
	 */
	@ParameterizedTest
	@MethodSource("domainFailures")
	void reportsADomainFailureFromWhatTheExceptionDeclares(ApiProblemException failure, HttpStatus status,
			String code) {
		ProblemDetail problem = this.handler.handleProblem(failure);

		assertThat(problem.getStatus()).isEqualTo(status.value());
		assertThat(problem.getProperties()).containsEntry("code", code);
		assertThat(problem.getTitle()).isEqualTo(failure.getTitle());
		assertThat(problem.getDetail()).isEqualTo(failure.getMessage());
	}

	/**
	 * The one failure that answers with a header as well as a body, and so takes a branch
	 * of its own. A client told "too many" and nothing else can only guess when to come
	 * back, and a client that guesses badly retries against the limit in a loop.
	 */
	@Test
	void tellsARefusedCallerWhenToComeBack() {
		ResponseEntity<ProblemDetail> response = this.handler
			.handleTooManyRequests(new TooManyRequestsException(Duration.ofSeconds(90)));

		assertThat(response.getStatusCode().value()).isEqualTo(429);
		assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("90");
		assertThat(response.getBody().getProperties()).containsEntry("code", "too_many_requests");
	}

	/** Seconds, not milliseconds: the header is defined in whole seconds. */
	@Test
	void reportsTheWaitInSeconds() {
		ResponseEntity<ProblemDetail> response = this.handler
			.handleTooManyRequests(new TooManyRequestsException(Duration.ofMinutes(15)));

		assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
	}

	/**
	 * The alternative is what this refusal is for: it is what the API offers instead of
	 * an endpoint anybody could walk to ask which handles are taken.
	 */
	@Test
	void offersAWayPastAHandleSomebodyElseHas() {
		ProblemDetail problem = this.handler.handleSlugTaken(new SlugTakenException("acme-2"));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
		assertThat(problem.getProperties()).containsEntry("code", "slug_taken").containsEntry("suggested", "acme-2");
	}

	/**
	 * The race that gets past the check and meets the unique index instead has no
	 * alternative to give — the transaction is spent. Absent rather than empty, so a
	 * client can tell "here is another" from "try something else".
	 */
	@Test
	void sendsNoAlternativeWhenItHasNoneToSend() {
		ProblemDetail problem = this.handler.handleSlugTaken(new SlugTakenException(null));

		assertThat(problem.getProperties()).containsEntry("code", "slug_taken").doesNotContainKey("suggested");
	}

	static Stream<Arguments> domainFailures() {
		return Stream.of(
				Arguments.of(new EmailAlreadyRegisteredException(), HttpStatus.CONFLICT, "email_already_registered"),
				Arguments.of(new InvalidCredentialsException(), HttpStatus.UNAUTHORIZED, "invalid_credentials"),
				Arguments.of(new NotAMemberException(), HttpStatus.FORBIDDEN, "not_a_member"),
				Arguments.of(new EmailNotVerifiedException(), HttpStatus.FORBIDDEN, "email_not_verified"),
				Arguments.of(new InvalidTokenException(), HttpStatus.BAD_REQUEST, "invalid_token"),
				Arguments.of(new NotAnOwnerException(), HttpStatus.FORBIDDEN, "not_an_owner"),
				Arguments.of(new AlreadyAMemberException(), HttpStatus.CONFLICT, "already_a_member"), Arguments
					.of(new InvitationAlreadyPendingException(), HttpStatus.CONFLICT, "invitation_already_pending"));
	}

	/**
	 * A field error as Bean Validation leaves one, minus the constraint itself: Spring
	 * puts the bare constraint name last in the codes array, and that is what
	 * {@code FieldError.getCode()} returns.
	 */
	private static FieldError constraintFailure(String field, String constraint) {
		return new FieldError("registrationRequest", field, null, false,
				new String[] { constraint + ".registrationRequest." + field, constraint + "." + field, constraint },
				null, "the English nobody should ever see");
	}

	@SuppressWarnings("unchecked")
	private Map<String, FieldProblem> errors(ProblemDetail problem) {
		return (Map<String, FieldProblem>) problem.getProperties().get("errors");
	}

	private MethodArgumentNotValidException methodArgumentNotValid(BeanPropertyBindingResult binding)
			throws NoSuchMethodException {
		// The exception needs a validated parameter to have come from, and any one will
		// do — it is never read for anything but the type. A stub rather than a real
		// controller method, which would have to be kept in step with a signature that
		// has nothing to do with what is being tested, in a package this one cannot see.
		MethodParameter parameter = new MethodParameter(
				ApiExceptionHandlerTests.class.getDeclaredMethod("aValidatedRequestBody", RegistrationRequest.class),
				0);
		return new MethodArgumentNotValidException(parameter, binding);
	}

	@SuppressWarnings("unused")
	private void aValidatedRequestBody(@Valid RegistrationRequest request) {
	}

}
