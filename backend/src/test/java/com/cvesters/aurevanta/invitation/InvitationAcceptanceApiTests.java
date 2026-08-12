package com.cvesters.aurevanta.invitation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.auth.signin.LoginRequest;
import com.cvesters.aurevanta.mail.RecordingEmailSender;
import com.cvesters.aurevanta.mail.RecordingEmailSenderConfiguration;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
import com.cvesters.aurevanta.ratelimit.SignInRateLimiter;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.token.LinkTokens;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reading an invitation and acting on it — the half of invitations that is done by
 * somebody who may not have an account yet, and so cannot be asked for a token.
 *
 * <p>
 * The two ways through accepting are the point of this class. An address nobody holds
 * becomes an account and a session in one step, because following the link proved it. An
 * address somebody <em>does</em> hold has to be claimed by that somebody first: a token
 * emailed to a mailbox proves control of the mailbox, and treating that as proof of
 * account ownership would make a forwarded message a way into somebody else's account.
 */
@Import({ TestcontainersConfiguration.class, RecordingEmailSenderConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
class InvitationAcceptanceApiTests {

	private static final String PASSWORD = "correct-horse-battery";

	private static final String CHOSEN_PASSWORD = "a-passphrase-of-my-own";

	private static final Instant CREATED_AT = Instant.parse("2026-08-06T08:00:00Z");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private UserRepository users;

	@Autowired
	private MembershipRepository memberships;

	@Autowired
	private TenantRepository tenants;

	@Autowired
	private InvitationRepository invitations;

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	@Autowired
	private RecordingEmailSender mail;

	@Autowired
	private MailRateLimiter rateLimiter;

	@Autowired
	private SignInRateLimiter signInRateLimiter;

	private Tenant acme;

	private Tenant umbrella;

	/** Owner of Acme, and the one doing the inviting throughout. */
	private Membership ada;

	/** Owner of the other organisation, so a wrong-identity accept has somebody to be. */
	private Membership grace;

	/** Holds an account and one membership already, in the *other* organisation. */
	private User erin;

	/**
	 * Holds an account and no memberships at all, so she signs in to an identity token.
	 */
	private User mallory;

	@BeforeEach
	void seedTwoOrganisations() {
		this.invitations.deleteAll();
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();
		this.mail.clear();
		this.rateLimiter.clear();
		this.signInRateLimiter.clear();

		this.acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		this.umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), this.acme, UserRole.OWNER);
		this.grace = member(user("grace@umbrella.test", "Grace"), this.umbrella, UserRole.OWNER);
		this.erin = user("erin@elsewhere.test", "Erin");
		this.memberships.save(new Membership(this.erin, this.umbrella, UserRole.MEMBER, CREATED_AT));
		this.mallory = user("mallory@nowhere.test", "Mallory");
	}

	@Test
	void thePreviewNamesTheOrganisationTheInviterAndTheRole() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);

		this.mvc.perform(get("/api/invitations/" + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.organisationName").value("Acme Planning Co"))
			.andExpect(jsonPath("$.invitedBy").value("Ada"))
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andExpect(jsonPath("$.claimed").value(false));
	}

	/**
	 * Which of the two ways through applies, said before the attempt rather than after
	 * it. Somebody invited at an address they already have an account for is asked to
	 * sign in, not to choose a display name and a password for an account that exists.
	 */
	@Test
	void thePreviewSaysWhenTheInvitedAddressAlreadyHasAnAccount() throws Exception {
		String token = inviteAndCaptureLink(this.erin.getEmail(), UserRole.MEMBER);

		this.mvc.perform(get("/api/invitations/" + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.claimed").value(true));
	}

	/**
	 * The address is matched the way accepting matches it, so casing cannot change it.
	 */
	@Test
	void thePreviewIgnoresCaseWhenLookingTheAddressUp() throws Exception {
		String token = inviteAndCaptureLink("ERIN@ELSEWHERE.TEST", UserRole.MEMBER);

		this.mvc.perform(get("/api/invitations/" + token)).andExpect(jsonPath("$.claimed").value(true));
	}

	/**
	 * Served to anybody holding the link, so it must disclose nothing a member would have
	 * had to sign in to see. What it now says about the invited address is one bit and no
	 * more: whether an account holds it, never the address itself.
	 */
	@Test
	void thePreviewDisclosesNothingElse() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);

		this.mvc.perform(get("/api/invitations/" + token))
			.andExpect(jsonPath("$.*", hasSize(4)))
			.andExpect(jsonPath("$.email").doesNotExist())
			.andExpect(jsonPath("$.organisation").doesNotExist())
			.andExpect(jsonPath("$.expiresAt").doesNotExist());
	}

	@Test
	void previewingALinkNobodyIssuedFails() throws Exception {
		this.mvc.perform(get("/api/invitations/not-a-link-we-sent"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));
	}

	/** Expired and withdrawn are told apart because the advice is opposite. */
	@Test
	void previewingAnExpiredInvitationSaysSo() throws Exception {
		String token = seed("dave@elsewhere.test", Instant.now().minus(Duration.ofDays(1)));

		this.mvc.perform(get("/api/invitations/" + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invitation_expired"));
	}

	@Test
	void previewingAWithdrawnInvitationSaysSo() throws Exception {
		String token = seedWithdrawn("dave@elsewhere.test");

		this.mvc.perform(get("/api/invitations/" + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invitation_revoked"));
	}

	/** A spent link and a link nobody recognises get one answer: it does not work. */
	@Test
	void previewingAnAlreadyAcceptedInvitationSaysTheLinkNoLongerWorks() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);
		accept(token, null, new AcceptInvitationRequest("Dave", CHOSEN_PASSWORD)).andExpect(status().isOk());

		this.mvc.perform(get("/api/invitations/" + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));
	}

	@Test
	void acceptingWithNoAccountCreatesOneAndSignsThemIn() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);

		accept(token, null, new AcceptInvitationRequest("Dave", CHOSEN_PASSWORD)).andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.account.email").value("dave@elsewhere.test"))
			.andExpect(jsonPath("$.account.displayName").value("Dave"))
			.andExpect(jsonPath("$.account.organisation.slug").value("acme-planning-co"))
			.andExpect(jsonPath("$.account.role").value("MEMBER"))
			// Following the link proved the address, exactly as a confirmation link
			// does, so there is nothing left to confirm and no second message to send.
			.andExpect(jsonPath("$.account.emailVerified").value(true));

		User dave = this.users.findByEmailIgnoringCase("dave@elsewhere.test").orElseThrow();
		assertThat(dave.isEmailVerified()).isTrue();
		assertThat(this.memberships.findAllForUser(dave.getId())).singleElement()
			.satisfies((membership) -> assertThat(membership.getTenant().getId()).isEqualTo(this.acme.getId()));
	}

	/**
	 * The account created here has to be one its owner can come back to, which under the
	 * gate means it must arrive already confirmed — an account nobody can sign into would
	 * be a worse outcome than never having accepted.
	 */
	@Test
	void theAccountItCreatesCanSignInAfterwards() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);
		accept(token, null, new AcceptInvitationRequest("Dave", CHOSEN_PASSWORD)).andExpect(status().isOk());

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new LoginRequest("dave@elsewhere.test", CHOSEN_PASSWORD))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("SIGNED_IN"));
	}

	/** Signing straight in is the choice sign-in would otherwise have recorded. */
	@Test
	void acceptingRecordsTheOrganisationAsTheOneTheyAreWorkingIn() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);
		accept(token, null, new AcceptInvitationRequest("Dave", CHOSEN_PASSWORD)).andExpect(status().isOk());

		User dave = this.users.findByEmailIgnoringCase("dave@elsewhere.test").orElseThrow();
		assertThat(this.memberships.findAllForUser(dave.getId()).getFirst().getLastAccessedAt()).isNotNull();
	}

	/** A link that kept working would be a standing way into the organisation. */
	@Test
	void acceptingSpendsTheInvitation() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);
		accept(token, null, new AcceptInvitationRequest("Dave", CHOSEN_PASSWORD)).andExpect(status().isOk());

		accept(token, null, new AcceptInvitationRequest("Dave Again", CHOSEN_PASSWORD))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));

		assertThat(this.memberships.findAll()).hasSize(4);
	}

	@Test
	void acceptingWithoutTheDetailsAnAccountNeedsIsRefused() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);

		accept(token, null, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("credentials_required"));

		assertThat(this.invitations.findAll().getFirst().getStatus()).isEqualTo(InvitationStatus.PENDING);
	}

	/**
	 * The third place a credential is set, held to the bounds the other two are. Anything
	 * looser here would let an invitation quietly rewrite the rule for everybody.
	 */
	@Test
	void rejectsAPasswordShorterThanTheRulesAllow() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);

		accept(token, null, new AcceptInvitationRequest("Dave", "short")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.password.code").value("size"))
			.andExpect(jsonPath("$.errors.password.min").value(12));
	}

	/**
	 * Stripped before validation, so a name of nothing but spaces is a name of nothing.
	 */
	@Test
	void rejectsAnAccountWhoseNameIsAllSpaces() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);

		accept(token, null, new AcceptInvitationRequest("  ", CHOSEN_PASSWORD)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.displayName.code").value("not_blank"));
	}

	/**
	 * A body carrying a password and no name at all, which is a different shape from one
	 * carrying a blank name and has to survive being stripped before validation sees it.
	 */
	@Test
	void rejectsAnAccountThatNamesNobody() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);

		this.mvc
			.perform(post("/api/invitations/" + token + "/accept").contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"" + CHOSEN_PASSWORD + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.displayName.code").value("not_blank"));
	}

	/**
	 * The hole this closes: the token proves somebody can read that mailbox, which is not
	 * the same as being the person whose account it registered. Attaching a membership on
	 * the strength of the link alone would be a way into an account by way of an inbox.
	 */
	@Test
	void anAddressThatAlreadyHasAnAccountMustSignInFirst() throws Exception {
		String token = inviteAndCaptureLink("erin@elsewhere.test", UserRole.OWNER);
		long accountsBefore = this.users.count();

		accept(token, null, new AcceptInvitationRequest("Not Erin", CHOSEN_PASSWORD))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("sign_in_required"));

		// No second account for an address that already has one, and the invitation is
		// still there for the person who was actually invited.
		assertThat(this.users.count()).isEqualTo(accountsBefore);
		assertThat(this.invitations.findAll().getFirst().getStatus()).isEqualTo(InvitationStatus.PENDING);
	}

	/**
	 * What Step 1 was for: one address, one password, a different role in each
	 * organisation. This is the first time the product itself can produce it.
	 */
	@Test
	void anExistingIdentityGainsASecondMembershipAndKeepsTheFirst() throws Exception {
		String token = inviteAndCaptureLink("erin@elsewhere.test", UserRole.OWNER);

		accept(token, tokenFor(membershipOf(this.erin, this.umbrella)), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.account.organisation.slug").value("acme-planning-co"))
			.andExpect(jsonPath("$.account.role").value("OWNER"));

		assertThat(this.memberships.findAllForUser(this.erin.getId()))
			.extracting((membership) -> membership.getTenant().getSlug(), Membership::getRole)
			.containsExactlyInAnyOrder(Tuple.tuple("umbrella", UserRole.MEMBER),
					Tuple.tuple("acme-planning-co", UserRole.OWNER));
	}

	/**
	 * Somebody who belongs to nothing holds an identity token, which is the only kind
	 * they can have — so accepting has to work with one, or the empty state has no way
	 * out.
	 */
	@Test
	void anIdentityTokenCanAcceptToo() throws Exception {
		String token = inviteAndCaptureLink("mallory@nowhere.test", UserRole.MEMBER);
		String identity = this.accessTokens.issueIdentityToken(this.mallory).value();

		accept(token, identity, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.account.organisation.slug").value("acme-planning-co"));

		assertThat(this.memberships.findAllForUser(this.mallory.getId())).hasSize(1);
	}

	/**
	 * A shared computer, or a link forwarded to a colleague. Attaching the membership to
	 * whoever happens to be signed in would put it on the wrong person and leave the one
	 * who was invited with nothing but a spent link.
	 */
	@Test
	void acceptingWhileSignedInAsSomebodyElseIsRefused() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);

		accept(token, tokenFor(this.grace), null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("invitation_for_another_address"));

		assertThat(this.invitations.findAll().getFirst().getStatus()).isEqualTo(InvitationStatus.PENDING);
		assertThat(this.memberships.findAllForUser(this.grace.getUser().getId())).hasSize(1);
	}

	/**
	 * Issuing already refuses an address that is a member, so this is the check that
	 * keeps that true if a second way into an organisation is ever added. Seeded directly
	 * for that reason: the product cannot currently produce the state.
	 */
	@Test
	void acceptingAnInvitationToAnOrganisationYouAlreadyBelongToIsRefused() throws Exception {
		String token = seedFor(this.umbrella, "erin@elsewhere.test", Instant.now().plus(Duration.ofDays(1)));

		accept(token, tokenFor(membershipOf(this.erin, this.umbrella)), null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("already_a_member"));

		assertThat(this.memberships.findAllForUser(this.erin.getId())).hasSize(1);
	}

	/**
	 * A token outlives the account it names by no more than the request presenting it.
	 */
	@Test
	void acceptingWithATokenWhoseAccountIsGoneIsRefused() throws Exception {
		String token = inviteAndCaptureLink("mallory@nowhere.test", UserRole.MEMBER);
		String stale = this.accessTokens.issueIdentityToken(this.mallory).value();
		this.users.delete(this.mallory);

		accept(token, stale, null).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"));
	}

	@Test
	void acceptingAnExpiredInvitationIsRefused() throws Exception {
		String token = seed("dave@elsewhere.test", Instant.now().minus(Duration.ofDays(1)));

		accept(token, null, new AcceptInvitationRequest("Dave", CHOSEN_PASSWORD)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invitation_expired"));

		assertThat(this.users.findByEmailIgnoringCase("dave@elsewhere.test")).isEmpty();
	}

	@Test
	void acceptingAWithdrawnInvitationIsRefused() throws Exception {
		String token = seedWithdrawn("dave@elsewhere.test");

		accept(token, null, new AcceptInvitationRequest("Dave", CHOSEN_PASSWORD)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invitation_revoked"));
	}

	@Test
	void acceptingALinkNobodyIssuedIsRefused() throws Exception {
		accept("not-a-link-we-sent", null, new AcceptInvitationRequest("Dave", CHOSEN_PASSWORD))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));
	}

	/**
	 * "Once" has to hold when a link is clicked twice in the same instant, not merely one
	 * click after the other. Redemption is a single conditional update for that reason;
	 * reading the row and then writing it would let every caller see a pending invitation
	 * and every caller be given a membership out of it.
	 */
	@Test
	void acceptingOnlyOnceWhenSeveralClicksArriveTogether() throws Exception {
		String token = inviteAndCaptureLink("dave@elsewhere.test", UserRole.MEMBER);
		int clicks = 4;
		CountDownLatch together = new CountDownLatch(1);

		List<Future<Membership>> attempts = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(clicks)) {
			for (int click = 0; click < clicks; click++) {
				attempts.add(pool.submit(() -> {
					together.await();
					return this.invitationService.accept(token, null,
							new AcceptInvitationRequest("Dave", CHOSEN_PASSWORD));
				}));
			}
			together.countDown();
		}

		long joined = 0;
		for (Future<Membership> attempt : attempts) {
			if (succeeded(attempt)) {
				joined++;
			}
		}
		assertThat(joined).isEqualTo(1);
		assertThat(this.users.findByEmailIgnoringCase("dave@elsewhere.test")).isPresent();
		assertThat(this.invitations.findAll().getFirst().getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
	}

	private static boolean succeeded(Future<Membership> attempt) throws Exception {
		try {
			return attempt.get() != null;
		}
		catch (ExecutionException ex) {
			return false;
		}
	}

	private User user(String email, String displayName) {
		User user = new User(email, this.passwordEncoder.encode(PASSWORD), displayName, CREATED_AT);
		user.markEmailVerified(CREATED_AT);
		return this.users.save(user);
	}

	private Membership member(User user, Tenant tenant, UserRole role) {
		return this.memberships.save(new Membership(user, tenant, role, CREATED_AT));
	}

	private Membership membershipOf(User user, Tenant tenant) {
		return this.memberships.findForUserInTenant(user.getId(), tenant.getId()).orElseThrow();
	}

	private String tokenFor(Membership membership) {
		return this.accessTokens.issue(membership).value();
	}

	/** Invites through the API, then reads the link out of the message it sent. */
	private String inviteAndCaptureLink(String email, UserRole role) throws Exception {
		this.mvc
			.perform(post("/api/invitations").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new InvitationBody(email, role.name()))))
			.andExpect(status().isCreated());

		Matcher link = Pattern.compile("/invite/([A-Za-z0-9_-]+)").matcher(this.mail.lastMessage().body());
		assertThat(link.find()).as("an invitation link in the message body").isTrue();
		this.mail.clear();
		return link.group(1);
	}

	/**
	 * Writes an invitation the product would take a week to produce, and returns the link
	 * for it. The raw token is chosen here rather than generated, which is the one thing
	 * issuing never allows.
	 */
	private String seed(String email, Instant expiresAt) {
		return seedFor(this.acme, email, expiresAt);
	}

	private String seedWithdrawn(String email) {
		String rawToken = seed(email, Instant.now().plus(Duration.ofDays(1)));
		Invitation invitation = this.invitations.findByTokenHash(LinkTokens.hash(rawToken)).orElseThrow();
		invitation.revoke();
		this.invitations.save(invitation);
		return rawToken;
	}

	private String seedFor(Tenant tenant, String email, Instant expiresAt) {
		String rawToken = "a-link-put-there-by-a-test-" + email;
		this.invitations.save(new Invitation(tenant, email, UserRole.MEMBER, this.ada.getUser(),
				LinkTokens.hash(rawToken), expiresAt, CREATED_AT));
		return rawToken;
	}

	private ResultActions accept(String token, String bearer, AcceptInvitationRequest credentials) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/invitations/" + token + "/accept");
		if (bearer != null) {
			request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
		}
		if (credentials != null) {
			request = request.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(credentials));
		}
		return this.mvc.perform(request);
	}

	/** Sent as text so a request naming no role can be built, as in the issuing tests. */
	private record InvitationBody(String email, String role) {
	}

}
