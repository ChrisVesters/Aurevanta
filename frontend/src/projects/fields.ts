import { textField } from '../auth/formValues';

/**
 * A box somebody left empty, as the server wants to hear about it.
 *
 * **Nothing rather than an empty string**, because the column has one spelling for an
 * absence and the server would faithfully store `''` if a form sent it. Stated once
 * because both forms that describe a thing — a project and a work item — have exactly this
 * question, and the version of this bug that gets written is the second copy.
 */
export function optionalField(form: FormData, name: string): string | null {
  return textField(form, name) || null;
}

/**
 * A number somebody typed, or null where they typed nothing.
 *
 * The empty check comes first because `Number('')` is **zero**, not `NaN` — so the obvious
 * version of this sends a box nobody filled in as an estimate of no hours at all, which the
 * server would refuse for being not positive rather than for being missing. The visitor
 * would be told their estimate must be more than zero about a field they never touched.
 */
export function numberField(form: FormData, name: string): number | null {
  return numberFrom(textField(form, name));
}

/**
 * The same rule, for a box whose value a component is holding itself rather than reading
 * out of a submitted form.
 *
 * Split out rather than written twice because the bug is in the *rule*, not in where the
 * string came from: a form that asks one question at a time cannot use `FormData` for the
 * answers it is no longer rendering, and re-deriving "empty means nothing, not zero" at the
 * second call site is exactly how the two come to disagree.
 */
export function numberFrom(value: string): number | null {
  const typed = value.trim();
  return typed === '' ? null : Number(typed);
}
