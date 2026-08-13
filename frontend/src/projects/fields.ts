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
