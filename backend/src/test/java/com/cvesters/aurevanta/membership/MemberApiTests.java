package com.cvesters.aurevanta.membership;

import java.time.Instant;
import java.util.UUID;

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
import org.springframework.test.web.servlet.RequestBuilder;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administering an organisation: who is in it, what they may do, and who may leave.
 *
 * <p>
 * Two organisations in the fixture, because every operation here takes an identifier from
 * the request and pairs it with the tenant from the caller's token. A single-tenant test
 * would pass whether or not that pairing is really there, and what it prevents is an
 * owner of one organisation demoting somebody in another.
 *
 * <p>
 * The invariant this class exists for is that an organisation always keeps an owner.
 * Everything else an owner can get wrong here, an owner can put right; an organisation
 * with nobody able to administer it cannot be repaired from inside the product at all.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MemberApiTests {

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
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	private Tenant acme;

	private Tenant umbrella;

	/** Owner of Acme. */
	private Membership ada;

	/** The second owner of Acme, so the first is not always the last one. */
	private Membership ivan;

	/** In Acme, and not an owner of it. */
	private Membership bob;

	/** The sole owner of the other organisation. */
	private Membership grace;

	/** In Acme *and* Umbrella, so removal can be shown to touch only one of them. */
	private User erin;

	@BeforeEach
	void seedTwoOrganisations() {
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();

		this.acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		this.umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), this.acme, UserRole.OWNER);
		this.ivan = member(user("ivan@acme.test", "Ivan"), this.acme, UserRole.OWNER);
		this.bob = member(user("bob@acme.test", "Bob"), this.acme, UserRole.MEMBER);
		this.grace = member(user("grace@umbrella.test", "Grace"), this.umbrella, UserRole.OWNER);
		this.erin = user("erin@elsewhere.test", "Erin");
		member(this.erin, this.acme, UserRole.MEMBER);
		member(this.erin, this.umbrella, UserRole.MEMBER);
	}

	@Test
	void anyMemberCanSeeWhoTheyWorkWith() throws Exception {
		this.mvc.perform(list(this.bob))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(4))
			// By display name, so the list does not reorder itself between requests.
			.andExpect(jsonPath("$[0].displayName").value("Ada"))
			.andExpect(jsonPath("$[0].email").value("ada@acme.test"))
			.andExpect(jsonPath("$[0].role").value("OWNER"))
			.andExpect(jsonPath("$[0].joinedAt").isNotEmpty());
	}

	/**
	 * Erin is in both organisations, so a list built from anything but the caller's own
	 * tenant would show Umbrella's people to Acme.
	 */
	@Test
	void theListShowsOnlyTheCallersOwnOrganisation() throws Exception {
		this.mvc.perform(list(this.grace))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[*].email").value(containsInAnyOrder("erin@elsewhere.test", "grace@umbrella.test")));
	}

	@Test
	void theListRequiresATokenScopedToAnOrganisation() throws Exception {
		this.mvc.perform(get("/api/members")).andExpect(status().isUnauthorized());

		this.mvc
			.perform(get("/api/members").header(HttpHeaders.AUTHORIZATION,
					"Bearer " + this.accessTokens.issueIdentityToken(this.erin).value()))
			.andExpect(status().isForbidden());
	}

	@Test
	void anOwnerCanPromoteAMember() throws Exception {
		this.mvc.perform(changeRole(this.ada, this.bob, UserRole.OWNER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("bob@acme.test"))
			.andExpect(jsonPath("$.role").value("OWNER"));

		assertThat(reload(this.bob).getRole()).isEqualTo(UserRole.OWNER);
	}

	@Test
	void anOwnerCanDemoteAnotherOwner() throws Exception {
		this.mvc.perform(changeRole(this.ada, this.ivan, UserRole.MEMBER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("MEMBER"));

		assertThat(reload(this.ivan).getRole()).isEqualTo(UserRole.MEMBER);
	}

	/**
	 * An organisation with no owner cannot invite, promote or administer anything, and
	 * nothing inside the product can put that right — so the last demotion is the point
	 * at which it has to be refused.
	 */
	@Test
	void theLastOwnerCannotBeDemoted() throws Exception {
		this.mvc.perform(changeRole(this.grace, this.grace, UserRole.MEMBER))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("last_owner"));

		assertThat(reload(this.grace).getRole()).isEqualTo(UserRole.OWNER);
	}

	/** Demoting one of two owners is fine; demoting the survivor afterwards is not. */
	@Test
	void theRuleFollowsWhoeverIsLeft() throws Exception {
		this.mvc.perform(changeRole(this.ada, this.ivan, UserRole.MEMBER)).andExpect(status().isOk());

		this.mvc.perform(changeRole(this.ada, this.ada, UserRole.MEMBER))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("last_owner"));
	}

	/** Setting an owner's role to the one they already hold takes nobody away. */
	@Test
	void theSoleOwnerCanBeLeftAnOwner() throws Exception {
		this.mvc.perform(changeRole(this.grace, this.grace, UserRole.OWNER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("OWNER"));
	}

	@Test
	void aMemberCannotChangeAnybodysRole() throws Exception {
		this.mvc.perform(changeRole(this.bob, this.erinIn(this.acme), UserRole.OWNER))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_an_owner"));

		assertThat(reload(this.erinIn(this.acme)).getRole()).isEqualTo(UserRole.MEMBER);
	}

	/**
	 * The membership is named in the path and the organisation is not, so this is where
	 * administering would reach across organisations if the lookup were by identifier
	 * alone. Erin really is a member somewhere — just not where Grace is.
	 */
	@Test
	void anotherOrganisationsMemberCannotBePromoted() throws Exception {
		this.mvc.perform(changeRole(this.grace, this.erinIn(this.acme), UserRole.OWNER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("member_not_found"));

		assertThat(reload(this.erinIn(this.acme)).getRole()).isEqualTo(UserRole.MEMBER);
	}

	@Test
	void rejectsARoleChangeThatNamesNoRole() throws Exception {
		this.mvc
			.perform(patch("/api/members/" + this.bob.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.role.code").value("not_null"));
	}

	@Test
	void anOwnerCanRemoveAMember() throws Exception {
		this.mvc.perform(remove(this.ada, this.bob)).andExpect(status().isNoContent());

		assertThat(this.memberships.findInTenant(this.bob.getId(), this.acme.getId())).isEmpty();
	}

	/**
	 * Removal takes away a membership, never an identity. Somebody removed keeps their
	 * account, their password and everywhere else they belong — the whole reason identity
	 * was split from membership in the first place.
	 */
	@Test
	void removingSomebodyLeavesTheirAccountAndTheirOtherOrganisationsAlone() throws Exception {
		this.mvc.perform(remove(this.ada, this.erinIn(this.acme))).andExpect(status().isNoContent());

		assertThat(this.users.findById(this.erin.getId())).isPresent();
		assertThat(this.memberships.findAllForUser(this.erin.getId()))
			.extracting((membership) -> membership.getTenant().getSlug(), Membership::getRole)
			.containsExactly(Tuple.tuple("umbrella", UserRole.MEMBER));
	}

	/**
	 * The rule is about the organisation keeping an administrator, not about who asks.
	 */
	@Test
	void anOwnerCanRemoveThemselvesWhileAnotherOwnerRemains() throws Exception {
		this.mvc.perform(remove(this.ada, this.ada)).andExpect(status().isNoContent());

		assertThat(this.memberships.findAllInTenant(this.acme.getId())).extracting(Membership::getId)
			.doesNotContain(this.ada.getId());
	}

	@Test
	void theLastOwnerCannotBeRemoved() throws Exception {
		this.mvc.perform(remove(this.grace, this.grace))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("last_owner"));

		assertThat(this.memberships.findInTenant(this.grace.getId(), this.umbrella.getId())).isPresent();
	}

	@Test
	void aMemberCannotRemoveAnybody() throws Exception {
		this.mvc.perform(remove(this.bob, this.erinIn(this.acme)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_an_owner"));

		assertThat(this.memberships.findInTenant(this.erinIn(this.acme).getId(), this.acme.getId())).isPresent();
	}

	@Test
	void anotherOrganisationsMemberCannotBeRemoved() throws Exception {
		this.mvc.perform(remove(this.grace, this.bob))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("member_not_found"));

		assertThat(this.memberships.findInTenant(this.bob.getId(), this.acme.getId())).isPresent();
	}

	/** A membership that never existed and one in another organisation read the same. */
	@Test
	void anIdentifierThatNamesNobodyIsRefused() throws Exception {
		this.mvc
			.perform(delete("/api/members/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION,
					"Bearer " + tokenFor(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("member_not_found"));
	}

	@Test
	void removingSomebodyWhoIsAlreadyGoneIsRefused() throws Exception {
		this.mvc.perform(remove(this.ada, this.bob)).andExpect(status().isNoContent());

		this.mvc.perform(remove(this.ada, this.bob))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("member_not_found"));
	}

	/**
	 * An access token pins the role held when it was issued, and lasts twelve hours. If
	 * it were believed rather than read back, somebody demoted this morning would go on
	 * administering the organisation for the rest of the day.
	 */
	@Test
	void aTokenIssuedBeforeADemotionNoLongerAdministers() throws Exception {
		String stale = tokenFor(this.ivan);
		this.mvc.perform(changeRole(this.ada, this.ivan, UserRole.MEMBER)).andExpect(status().isOk());

		this.mvc
			.perform(patch("/api/members/" + this.bob.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + stale)
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new ChangeRoleRequest(UserRole.OWNER))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_an_owner"));
	}

	/** The same, for a token whose membership has gone entirely. */
	@Test
	void aTokenIssuedBeforeARemovalReachesNothing() throws Exception {
		String stale = tokenFor(this.bob);
		this.mvc.perform(remove(this.ada, this.bob)).andExpect(status().isNoContent());

		this.mvc.perform(get("/api/members").header(HttpHeaders.AUTHORIZATION, "Bearer " + stale))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));
	}

	private User user(String email, String displayName) {
		User user = new User(email, this.passwordEncoder.encode("correct-horse-battery"), displayName, CREATED_AT);
		user.markEmailVerified(CREATED_AT);
		return this.users.save(user);
	}

	private Membership member(User user, Tenant tenant, UserRole role) {
		return this.memberships.save(new Membership(user, tenant, role, CREATED_AT));
	}

	private Membership erinIn(Tenant tenant) {
		return this.memberships.findForUserInTenant(this.erin.getId(), tenant.getId()).orElseThrow();
	}

	private Membership reload(Membership membership) {
		return this.memberships.findById(membership.getId()).orElseThrow();
	}

	private String tokenFor(Membership membership) {
		return this.accessTokens.issue(membership).value();
	}

	private RequestBuilder list(Membership caller) {
		return get("/api/members").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(caller));
	}

	private RequestBuilder changeRole(Membership caller, Membership target, UserRole role) {
		return patch("/api/members/" + target.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(caller))
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(new ChangeRoleRequest(role)));
	}

	private RequestBuilder remove(Membership caller, Membership target) {
		return delete("/api/members/" + target.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(caller));
	}

}
