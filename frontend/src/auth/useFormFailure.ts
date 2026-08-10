import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { describeFailure, describeFieldErrors } from '../i18n/problems';

/**
 * What a form shows when a submission is refused.
 *
 * Shared rather than written per form, because the rule that decides it is easy to leave
 * out and invisible when you do: **a complaint that belongs to a field is shown against
 * that field, and only a failure belonging to the form as a whole gets the banner.** A
 * form that handled just the banner would answer an empty required field with "some
 * fields need attention" and never say which, or why.
 *
 * Also hands back the failure's `code`. That is not for choosing wording — this hook has
 * already done that, and a form that reached for `describeFailure` itself is the mistake
 * the rule above exists to prevent. It is for the rarer case where one particular failure
 * has an *action* attached: sign-in offers to send a new confirmation link, and it can
 * only offer that when it knows which refusal it received.
 *
 * @param fields the names this form renders an input for. Suppressing the banner is only
 * safe for complaints the visitor will actually see, so a complaint about a field that is
 * not on screen — a name the server and the form disagree about — falls back to the
 * banner rather than being shown nowhere at all.
 */
export function useFormFailure(fields: readonly string[]) {
  const { t } = useTranslation();
  const [message, setMessage] = useState<string | null>(null);
  const [code, setCode] = useState<string | undefined>(undefined);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const clear = useCallback(() => {
    setMessage(null);
    setCode(undefined);
    setFieldErrors({});
  }, []);

  const report = useCallback(
    (error: unknown) => {
      const perField = describeFieldErrors(t, error);
      setFieldErrors(perField);
      setCode(error instanceof ApiError ? error.code : undefined);
      const onScreen = Object.keys(perField).filter((field) =>
        fields.includes(field)
      );
      // The banner would only repeat what each input already says — but only if the
      // visitor can see at least one of them.
      setMessage(onScreen.length > 0 ? null : describeFailure(t, error));
    },
    // Depends on what the field names *are*, not on the array's identity: every call site
    // passes a literal, which would otherwise be a new array on each render and re-create
    // `report` every time.
    [t, fields.join(',')]
  );

  return { message, code, fieldErrors, report, clear };
}
