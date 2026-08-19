package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;
import java.util.List;

/**
 * What hiring would be worth at one confidence.
 *
 * <p>
 * <strong>Three of these rather than one, because the control moves.</strong> Every
 * replay produces all five percentiles at once, so publishing the three M4's control
 * offers costs nothing — and asking again would mean paying for the simulations a second
 * time to answer a question the reader has already bought.
 *
 * @param stands the date this run gives today, so the distance below can be checked
 * rather than believed.
 */
public record HireAtResponse(int confidence, LocalDate stands, List<HireStepResponse> hires) {

}
