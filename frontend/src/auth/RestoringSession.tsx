import { useTranslation } from 'react-i18next';

/**
 * Shown while a stored token is being checked. Without it a signed-in visitor would see
 * the sign-in page flash before being redirected away from it.
 */
export function RestoringSession() {
  const { t } = useTranslation();

  return (
    <p className="loading" role="status">
      {t('app.loading')}
    </p>
  );
}
