package com.cvesters.aurevanta.dependency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemRepository;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.problem.ApiProblemException;
import com.cvesters.aurevanta.project.Project;
import com.cvesters.aurevanta.project.ProjectRepository;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one invariant in this milestone that a serial test cannot prove.
 *
 * <p>
 * "The plan stays acyclic" is a property of every edge at once, and two callers can each
 * read a graph their own new edge leaves acyclic and close a loop together. Checked and
 * then written without a lock, both would land and the plan would wait for itself forever
 * — with every other test in the suite still green, because each of the two edges is
 * perfectly legal on its own.
 *
 * <p>
 * Released together as {@code SingleUseTokenServiceTests} does, and for the same reason:
 * a test that submits one and then the other cannot tell the lock from luck.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DependencyGraphLockTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-13T08:00:00Z");

	private static final BigDecimal NO_LAG = BigDecimal.ZERO;

	@Autowired
	private DependencyService dependencyService;

	@Autowired
	private DependencyRepository dependencies;

	@Autowired
	private WorkItemRepository items;

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private MembershipRepository memberships;

	@Autowired
	private TenantRepository tenants;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Membership ada;

	private WorkItem design;

	private WorkItem build;

	private WorkItem ship;

	@BeforeEach
	void seedAPlanNobodyHasJoinedUpYet() {
		this.dependencies.deleteAll();
		this.items.deleteAll();
		this.projects.deleteAll();
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();

		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		User user = new User("ada@acme.test", this.passwordEncoder.encode("correct-horse-battery"), "Ada", CREATED_AT);
		user.markEmailVerified(CREATED_AT);
		this.ada = this.memberships.save(new Membership(this.users.save(user), acme, UserRole.OWNER, CREATED_AT));
		Project plan = this.projects.save(new Project(acme, "Q3 platform work", null, CREATED_AT));
		this.design = this.items.save(new WorkItem(plan, "Design the migration", null, CREATED_AT));
		this.build = this.items.save(new WorkItem(plan, "Build the migration", null, CREATED_AT));
		this.ship = this.items.save(new WorkItem(plan, "Ship it", null, CREATED_AT));
	}

	/**
	 * Neither arrow is a cycle against the empty graph both callers would otherwise read.
	 * Together they are the shortest one there is.
	 */
	@Test
	void twoArrowsThatWouldCloseALoopTogetherLeaveOneOfThem() throws Exception {
		List<Boolean> drawn = bothAtOnce(this.design.getId(), this.build.getId(), this.build.getId(),
				this.design.getId());

		assertThat(drawn).containsExactlyInAnyOrder(true, false);
		assertThat(this.dependencies.findAll()).hasSize(1);
	}

	/**
	 * The same race a hop further apart, where the loop only closes through an edge that
	 * was already there — so the second caller has to have read the first caller's write,
	 * not merely have been serialised against it.
	 */
	@Test
	void twoArrowsThatWouldCloseALongerLoopTogetherLeaveOneOfThem() throws Exception {
		this.dependencies.save(new Dependency(this.design, this.build, NO_LAG, CREATED_AT));

		List<Boolean> drawn = bothAtOnce(this.build.getId(), this.ship.getId(), this.ship.getId(), this.design.getId());

		assertThat(drawn).containsExactlyInAnyOrder(true, false);
		assertThat(this.dependencies.findAll()).hasSize(2);
	}

	/**
	 * Two callers, released together, each drawing one of the two arrows — and what comes
	 * back is whether each of them landed. A refusal is expected of exactly one; anything
	 * that is not a refusal this API publishes is left to fail the test.
	 */
	private List<Boolean> bothAtOnce(UUID firstFrom, UUID firstTo, UUID secondFrom, UUID secondTo) throws Exception {
		UUID callerId = this.ada.getUser().getId();
		UUID tenantId = this.ada.getTenant().getId();
		CountDownLatch together = new CountDownLatch(1);

		List<Future<Boolean>> attempts = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			attempts.add(pool.submit(() -> draw(together, callerId, tenantId, firstFrom, firstTo)));
			attempts.add(pool.submit(() -> draw(together, callerId, tenantId, secondFrom, secondTo)));
			together.countDown();
		}

		List<Boolean> drawn = new ArrayList<>();
		for (Future<Boolean> attempt : attempts) {
			drawn.add(attempt.get());
		}
		return drawn;
	}

	private boolean draw(CountDownLatch together, UUID callerId, UUID tenantId, UUID predecessorId, UUID successorId)
			throws Exception {
		together.await();
		try {
			this.dependencyService.create(callerId, tenantId, predecessorId, successorId, NO_LAG);
			return true;
		}
		catch (ApiProblemException ex) {
			return false;
		}
	}

}
