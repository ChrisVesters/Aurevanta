package com.cvesters.aurevanta.project;

/**
 * A project together with how much of it has been estimated.
 *
 * <p>
 * <strong>Coverage is reported, never enforced.</strong> A plan with thirty items and
 * twelve estimates is not a broken plan — it is every real plan on the day somebody first
 * opens the tool — so the simulation engine forecasts what is estimated and says how much
 * it left out. The plan schema's half of that decision is making the number computable
 * and visible, which is this.
 *
 * @param itemCount items in the plan, archived ones excluded: what somebody put away is
 * not work the forecast is missing.
 * @param estimatedItemCount how many of those carry an estimate from anybody at all. One
 * person's range is enough to count — whether several estimators agree is a question the
 * simulation engine asks, and answering it here would make coverage mean two things at
 * once.
 */
public record PlannedProject(Project project, long itemCount, long estimatedItemCount) {

}
