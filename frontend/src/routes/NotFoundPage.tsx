import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

export function NotFoundPage() {
  const { t } = useTranslation();

  return (
    <main className="not-found">
      <h1>{t('notFound.title')}</h1>
      <p className="lede">{t('notFound.lede')}</p>
      <p>
        <Link to="/">{t('notFound.back')}</Link>
      </p>
    </main>
  );
}
