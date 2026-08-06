/**
 * Reads a text field out of a submitted form.
 *
 * `FormData.get` returns null for a field that is not present and a `File` for a file
 * input, neither of which survives being passed through `String()` into an API call.
 * Both are treated as "nothing was entered".
 */
export function textField(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === 'string' ? value : '';
}
