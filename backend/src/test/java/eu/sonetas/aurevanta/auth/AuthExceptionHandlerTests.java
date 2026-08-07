package eu.sonetas.aurevanta.auth;

import java.util.Map;
import java.util.stream.Stream;

import eu.sonetas.aurevanta.auth.problem.AuthProblemException;
import eu.sonetas.aurevanta.auth.problem.EmailAlreadyRegisteredException;
import eu.sonetas.aurevanta.auth.problem.InvalidCredentialsException;
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

	@Test
	void keepsTheFirstMessageWhenAFieldFailsTwoConstraints() throws Exception {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(
				new RegistrationRequest("Acme", "Ada", "ada@acme.test", ""), "registrationRequest");
		binding.addError(new FieldError("registrationRequest", "password", "must not be blank"));
		binding.addError(new FieldError("registrationRequest", "password", "size must be between 12 and 72"));

		ProblemDetail problem = this.handler.handleInvalidRequest(methodArgumentNotValid(binding));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(problem.getProperties()).containsEntry("code", "validation_failed");
		assertThat(errors(problem)).containsExactly(Map.entry("password", "must not be blank"));
	}

	@Test
	void reportsEveryFailingField() throws Exception {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(
				new RegistrationRequest("", "Ada", "nope", "short"), "registrationRequest");
		binding.addError(new FieldError("registrationRequest", "organisationName", "must not be blank"));
		binding.addError(new FieldError("registrationRequest", "email", "must be a well-formed email address"));

		ProblemDetail problem = this.handler.handleInvalidRequest(methodArgumentNotValid(binding));

		assertThat(errors(problem)).containsOnlyKeys("organisationName", "email");
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
				Arguments.of(new NotAMemberException(), HttpStatus.FORBIDDEN, "not_a_member"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> errors(ProblemDetail problem) {
		return (Map<String, String>) problem.getProperties().get("errors");
	}

	private MethodArgumentNotValidException methodArgumentNotValid(BeanPropertyBindingResult binding)
			throws NoSuchMethodException {
		MethodParameter parameter = new MethodParameter(
				AuthController.class.getDeclaredMethod("register", RegistrationRequest.class), 0);
		return new MethodArgumentNotValidException(parameter, binding);
	}

}
