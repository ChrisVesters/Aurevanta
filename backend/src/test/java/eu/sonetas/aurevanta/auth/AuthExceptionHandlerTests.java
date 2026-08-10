package eu.sonetas.aurevanta.auth;

import java.util.Map;
import java.util.stream.Stream;

import eu.sonetas.aurevanta.auth.problem.AuthProblemException;
import eu.sonetas.aurevanta.auth.problem.EmailAlreadyRegisteredException;
import eu.sonetas.aurevanta.auth.problem.EmailNotVerifiedException;
import eu.sonetas.aurevanta.auth.problem.FieldProblem;
import eu.sonetas.aurevanta.auth.problem.InvalidCredentialsException;
import eu.sonetas.aurevanta.auth.problem.InvalidTokenException;
import eu.sonetas.aurevanta.auth.problem.NotAMemberException;
import eu.sonetas.aurevanta.auth.problem.OrganisationNameUnavailableException;
import eu.sonetas.aurevanta.auth.problem.UnusableOrganisationNameException;
import eu.sonetas.aurevanta.auth.registration.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end-to-end tests drive most of these through the API; covered here are the paths a
 * request cannot readily produce — a constraint violation from two racing registrations,
 * and a field that fails more than one constraint at once.
 */
class AuthExceptionHandlerTests {

	private final AuthExceptionHandler handler = new AuthExceptionHandler();

	@Test
	void reportsAConstraintViolationAsAConflict() {
		ProblemDetail problem = this.handler.handleConflict(new DataIntegrityViolationException("uq_users_email"));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
		assertThat(problem.getProperties()).containsEntry("code", "registration_conflict");
		// The underlying message can name database objects, so it must not be echoed
		// back.
		assertThat(problem.getDetail()).doesNotContain("uq_users_email");
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
		return new BeanPropertyBindingResult(new RegistrationRequest("Acme", "Ada", "ada@acme.test", ""),
				"registrationRequest");
	}

	@Test
	void reportsEveryFailingField() throws Exception {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(
				new RegistrationRequest("", "Ada", "nope", "short"), "registrationRequest");
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
				new RegistrationRequest("Acme", "Ada", "ada@acme.test", "a-long-enough-passphrase"),
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
				new RegistrationRequest("Acme", "Ada", "ada@acme.test", "a-long-enough-passphrase"),
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
	void reportsADomainFailureFromWhatTheExceptionDeclares(AuthProblemException failure, HttpStatus status,
			String code) {
		ProblemDetail problem = this.handler.handleAuthProblem(failure);

		assertThat(problem.getStatus()).isEqualTo(status.value());
		assertThat(problem.getProperties()).containsEntry("code", code);
		assertThat(problem.getTitle()).isEqualTo(failure.getTitle());
		assertThat(problem.getDetail()).isEqualTo(failure.getMessage());
	}

	static Stream<Arguments> domainFailures() {
		return Stream.of(
				Arguments.of(new EmailAlreadyRegisteredException(), HttpStatus.CONFLICT, "email_already_registered"),
				Arguments.of(new OrganisationNameUnavailableException(), HttpStatus.CONFLICT,
						"organisation_name_unavailable"),
				Arguments.of(new UnusableOrganisationNameException(), HttpStatus.BAD_REQUEST,
						"organisation_name_unusable"),
				Arguments.of(new InvalidCredentialsException(), HttpStatus.UNAUTHORIZED, "invalid_credentials"),
				Arguments.of(new NotAMemberException(), HttpStatus.FORBIDDEN, "not_a_member"),
				Arguments.of(new EmailNotVerifiedException(), HttpStatus.FORBIDDEN, "email_not_verified"),
				Arguments.of(new InvalidTokenException(), HttpStatus.BAD_REQUEST, "invalid_token"));
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
		MethodParameter parameter = new MethodParameter(
				AuthController.class.getDeclaredMethod("register", RegistrationRequest.class), 0);
		return new MethodArgumentNotValidException(parameter, binding);
	}

}
