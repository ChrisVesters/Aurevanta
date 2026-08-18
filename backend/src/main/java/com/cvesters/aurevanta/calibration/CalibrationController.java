package com.cvesters.aurevanta.calibration;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * What this organisation's ranges have turned out to be worth.
 *
 * <p>
 * <strong>One endpoint, and there is nothing for a second one to do.</strong> Nothing is
 * written — the record is derived from estimates and outcomes that already exist, which
 * is why it can answer for every estimate this product has ever held rather than only for
 * the ones written since somebody added a column, and why there is no problem code here
 * beyond the {@code not_a_member} every tenant-scoped endpoint shares.
 *
 * <p>
 * <strong>Scoped to the organisation and not to a plan.</strong> A single plan holds far
 * too few completed items to reach the counts at which a hit rate distinguishes 45% from
 * 80% — four out of five is 80% and says nothing — and calibration is in any case a
 * property of people rather than of plans.
 *
 * <p>
 * Reachable by every member, for the reason {@code /api/members} is: colleagues may see
 * what their colleagues estimated, and they can already see the estimates themselves.
 */
@RestController
class CalibrationController {

	private final CalibrationService calibration;

	CalibrationController(CalibrationService calibration) {
		this.calibration = calibration;
	}

	@GetMapping("/api/calibration")
	CalibrationResponse read(@AuthenticationPrincipal AuthenticatedUser caller) {
		return CalibrationResponse.of(this.calibration.recordFor(caller.userId(), caller.tenantId()));
	}

}
