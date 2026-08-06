import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

/** Shared frame for the sign-up and sign-in pages: a way back, then the form. */
export function AuthLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation();

  return (
    <main className="auth-screen">
      <p className="back-home">
        <Link to="/">{t('auth.backToHome')}</Link>
      </p>
      {children}
    </main>
  );
}
