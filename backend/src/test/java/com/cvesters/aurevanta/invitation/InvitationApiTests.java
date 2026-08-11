package com.cvesters.aurevanta.invitation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.cvesters.aurevanta.mail.EmailMessage;
import com.cvesters.aurevanta.mail.RecordingEmailSender;
import com.cvesters.aurevanta.mail.RecordingEmailSenderConfiguration;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.token.LinkTokens;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issuing an invitation: the second way into an organisation.
 *
 * <p>
 * The fixture holds <em>two</em> organisations on purpose. Everything this endpoint
 * decides — who may invite, whether the address is already a member, whether an
 * invitation is already outstanding — is scoped to one of them, and every one of those
 * checks would pass a single-tenant test whether or not the scoping is really there.
 *
 * <p>
 * Tokens are minted from {@link AccessTokenService} rather than by signing in, because
 * two of the callers here cannot sign in: one has never confirmed their address, and one
 * has had their membership taken away. Both are exactly the states worth proving the
 * endpoint refuses.
 */
@Import({ TestcontainersConfiguration.class, RecordingEmailSenderConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
class InvitationApiTests {

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
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	@Autowired
	private RecordingEmailSender mail;

	/** One bean across the class, and every request here provokes mail. */
	@Autowired
	private MailRateLimiter rateLimiter;

	private Tenant acme;

	private Tenant umbrella;

	/** Owner of Acme, and the caller in most of what follows. */
	private Membership ada;

	/** In Acme, but not an owner of it. */
	private Membership bob;

	/** An owner of Acme whose own address was never confirmed. */
	private Membership ivan;

	/** Owner of the other organisation entirely. */
	private Membership grace;

	@BeforeEach
	void seedTwoOrganisations() {
		this.invitations.deleteAll();
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();
		this.mail.clear();
		this.rateLimiter.clear();

		this.acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		this.umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada", true), this.acme, UserRole.OWNER);
		this.bob = member(user("bob@acme.test", "Bob", true), this.acme, UserRole.MEMBER);
		this.ivan = member(user("ivan@acme.test", "Ivan", false), this.acme, UserRole.OWNER);
		this.grace = member(user("grace@umbrella.test", "Grace", true), this.umbrella, UserRole.OWNER);
	}

	@Test
	void anOwnerCanInviteSomebodyIntoTheirOrganisation() throws Exception {
		invite(this.ada, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("dave@elsewhere.test"))
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.expiresAt").isNotEmpty())
			// The raw token went into the message and nowhere else; a response carrying
			// it would put a way into the organisation in every proxy log on the way
			// back.
			.andExpect(jsonPath("$.token").doesNotExist());

		Invitation stored = onlyInvitation();
		assertThat(stored.getTenant().getId()).isEqualTo(this.acme.getId());
		assertThat(stored.getEmail()).isEqualTo("dave@elsewhere.test");
		assertThat(stored.getRole()).isEqualTo(UserRole.MEMBER);
		assertThat(stored.getInvitedBy().getId()).isEqualTo(this.ada.getUser().getId());
		assertThat(stored.getStatus()).isEqualTo(InvitationStatus.PENDING);
		assertThat(stored.getAcceptedAt()).isNull();
		assertThat(stored.getExpiresAt()).isAfter(Instant.now());
	}

	/** Inviting a second owner and inviting a member are different decisions. */
	@Test
	void invitesSomebodyAsTheRoleTheyWereOffered() throws Exception {
		invite(this.ada, "dave@elsewhere.test", UserRole.OWNER).andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("OWNER"));

		assertThat(onlyInvitation().getRole()).isEqualTo(UserRole.OWNER);
	}

	@Test
	void putsTheInvitationInThePost() throws Exception {
		invite(this.ada, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isCreated());

		EmailMessage sent = this.mail.onlyMessage();
		assertThat(sent.to()).isEqualTo("dave@elsewhere.test");
		// A stranger who has never heard of this application needs to see who is asking
		// and what they are being asked to join, or the message reads like phishing.
		assertThat(sent.subject()).contains("Ada").contains("Acme Planning Co");
		assertThat(sent.body()).contains("Ada").contains("Acme Planning Co").contains("/invite/");
	}

	/**
	 * The whole reason invitations carry a {@code token_hash} rather than a token: a
	 * stolen backup must not be a list of working ways into other people's organisations.
	 */
	@Test
	void writesDownOnlyAHashOfTheLink() throws Exception {
		invite(this.ada, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isCreated());

		String rawToken = tokenFromLatestMail();
		Invitation stored = onlyInvitation();
		assertThat(stored.getTokenHash()).isEqualTo(LinkTokens.hash(rawToken)).hasSize(64).isNotEqualTo(rawToken);
	}

	/** Decision 4: owners invite, and nobody else, until permissions get finer. */
	@Test
	void aMemberCannotInvite() throws Exception {
		invite(this.bob, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_an_owner"));

		assertThat(this.invitations.findAll()).isEmpty();
		assertThat(this.mail.sent()).isEmpty();
	}

	/**
	 * Unreachable while the gate holds, since an unconfirmed account is refused a token —
	 * which is the reason to prove it here rather than trust the gate never to move.
	 * Somebody who has not shown they can read their own inbox may not write to a
	 * stranger's.
	 */
	@Test
	void anOwnerWhoseOwnAddressWasNeverConfirmedCannotInvite() throws Exception {
		invite(this.ivan, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("email_not_verified"));

		assertThat(this.mail.sent()).isEmpty();
	}

	/**
	 * An access token pins the role held when it was issued and lasts twelve hours, so
	 * standing is re-read rather than believed. Otherwise somebody removed this morning
	 * goes on inviting people into the organisation for the rest of the day.
	 */
	@Test
	void somebodyWhoNoLongerBelongsToTheOrganisationCannotInvite() throws Exception {
		String token = tokenFor(this.ada);
		this.memberships.delete(this.ada);

		this.mvc.perform(inviteRequest(token, "dave@elsewhere.test", UserRole.MEMBER))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));
	}

	@Test
	void refusesAnAddressThatAlreadyBelongsToTheOrganisation() throws Exception {
		invite(this.ada, "bob@acme.test", UserRole.MEMBER).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("already_a_member"));

		assertThat(this.invitations.findAll()).isEmpty();
		assertThat(this.mail.sent()).isEmpty();
	}

	/**
	 * One mailbox however it is spelled, so the check has to match the way lookups do.
	 */
	@Test
	void refusesAMemberHoweverTheAddressIsSpelled() throws Exception {
		invite(this.ada, "  BOB@ACME.TEST  ", UserRole.MEMBER).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("already_a_member"));
	}

	/**
	 * Belonging to one organisation says nothing about another. Were the membership check
	 * not scoped by tenant, Bob would be un-invitable everywhere because he is in Acme.
	 */
	@Test
	void aMemberOfAnotherOrganisationCanStillBeInvited() throws Exception {
		invite(this.grace, "bob@acme.test", UserRole.MEMBER).andExpect(status().isCreated());

		assertThat(onlyInvitation().getTenant().getId()).isEqualTo(this.umbrella.getId());
	}

	/**
	 * Two live links to one organisation in one inbox is two ways in where the owner
	 * believes there is one.
	 */
	@Test
	void refusesASecondLiveInvitationToTheSameAddress() throws Exception {
		invite(this.ada, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isCreated());
		this.mail.clear();

		invite(this.ada, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("invitation_already_pending"));

		assertThat(this.invitations.findAll()).hasSize(1);
		assertThat(this.mail.sent()).isEmpty();
	}

	/**
	 * An expired invitation is no longer a way in, but it does still hold the slot the
	 * partial unique index reserves — so it is renewed in place rather than treated as an
	 * obstacle, which is what stops an address becoming permanently un-invitable once its
	 * first invitation times out.
	 */
	@Test
	void renewsAnInvitationThatHasRunOutOfTime() throws Exception {
		Instant past = Instant.now().minus(Duration.ofDays(1));
		String staleHash = LinkTokens.hash("a-link-that-timed-out");
		Invitation stale = this.invitations.save(new Invitation(this.acme, "dave@elsewhere.test", UserRole.MEMBER,
				this.ada.getUser(), staleHash, past, past));

		invite(this.ada, "dave@elsewhere.test", UserRole.OWNER).andExpect(status().isCreated());

		Invitation renewed = onlyInvitation();
		// The same row, because the index allows only one — carrying a link that works
		// and a role restated by whoever is inviting now.
		assertThat(renewed.getId()).isEqualTo(stale.getId());
		assertThat(renewed.getTokenHash()).isNotEqualTo(staleHash);
		assertThat(renewed.getExpiresAt()).isAfter(Instant.now());
		assertThat(renewed.getRole()).isEqualTo(UserRole.OWNER);
		assertThat(this.mail.onlyMessage().to()).isEqualTo("dave@elsewhere.test");
	}

	/**
	 * The index is per organisation, so one address can be considering two at once — the
	 * whole point of splitting identity from membership in Step 1.
	 */
	@Test
	void oneAddressCanHoldAnInvitationToEachOrganisation() throws Exception {
		invite(this.ada, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isCreated());

		invite(this.grace, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isCreated());

		assertThat(this.invitations.findAll()).extracting((invitation) -> invitation.getTenant().getId())
			.containsExactlyInAnyOrder(this.acme.getId(), this.umbrella.getId());
	}

	/**
	 * An identity token names no organisation, so there is none to invite anybody into.
	 */
	@Test
	void refusesAnIdentityToken() throws Exception {
		String identity = this.accessTokens.issueIdentityToken(this.ada.getUser()).value();

		this.mvc.perform(inviteRequest(identity, "dave@elsewhere.test", UserRole.MEMBER))
			.andExpect(status().isForbidden());
	}

	@Test
	void refusesARequestWithNoToken() throws Exception {
		this.mvc
			.perform(post("/api/invitations").contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new InvitationBody("dave@elsewhere.test", "MEMBER"))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsARequestThatNamesNoRole() throws Exception {
		this.mvc.perform(inviteRequest(tokenFor(this.ada), "dave@elsewhere.test", null))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.role.code").value("not_null"));
	}

	@Test
	void rejectsARequestThatNamesNoAddress() throws Exception {
		this.mvc.perform(inviteRequest(tokenFor(this.ada), null, UserRole.MEMBER))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email.code").value("not_blank"));
	}

	@Test
	void rejectsAMalformedAddress() throws Exception {
		invite(this.ada, "not-an-address", UserRole.MEMBER).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email.code").value("email"));
	}

	/**
	 * Needing credentials is not what makes an endpoint safe here: the inbox on the
	 * receiving end belongs to somebody who never asked to hear from this application,
	 * and cannot tell an invitation from any other message it sends.
	 *
	 * <p>
	 * Also the case the widened exception advice exists for. This controller sits outside
	 * {@code auth}, where the advice used to be scoped: the refusal would otherwise have
	 * lost its {@code code} and its {@code Retry-After} and arrived as Boot's default
	 * error.
	 */
	@Test
	void refusesToKeepWritingToOneAddress() throws Exception {
		// Counted before anything is looked up, so the refusals in between still spend
		// the
		// allowance — the limit bounds requests rather than the messages they produce.
		invite(this.ada, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isCreated());
		invite(this.ada, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isConflict());
		invite(this.ada, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isConflict());

		invite(this.ada, "dave@elsewhere.test", UserRole.MEMBER).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("too_many_requests"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}

	private User user(String email, String displayName, boolean verified) {
		User user = new User(email, this.passwordEncoder.encode("correct-horse-battery"), displayName, CREATED_AT);
		if (verified) {
			user.markEmailVerified(CREATED_AT);
		}
		return this.users.save(user);
	}

	private Membership member(User user, Tenant tenant, UserRole role) {
		return this.memberships.save(new Membership(user, tenant, role, CREATED_AT));
	}

	private ResultActions invite(Membership caller, String email, UserRole role) throws Exception {
		return this.mvc.perform(inviteRequest(tokenFor(caller), email, role));
	}

	private MockHttpServletRequestBuilder inviteRequest(String token, String email, UserRole role) throws Exception {
		InvitationBody body = new InvitationBody(email, (role != null) ? role.name() : null);
		return post("/api/invitations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(body));
	}

	private String tokenFor(Membership membership) {
		return this.accessTokens.issue(membership).value();
	}

	private Invitation onlyInvitation() {
		List<Invitation> all = this.invitations.findAll();
		assertThat(all).as("exactly one invitation").hasSize(1);
		return all.getFirst();
	}

	private String tokenFromLatestMail() {
		Matcher token = Pattern.compile("/invite/([A-Za-z0-9_-]+)").matcher(this.mail.lastMessage().body());
		assertThat(token.find()).as("an invitation link in the message body").isTrue();
		return token.group(1);
	}

	/**
	 * The role is sent as text so that a request naming none can be built, which the
	 * record itself cannot express.
	 */
	private record InvitationBody(String email, String role) {
	}

}
