import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { ProjectForm } from '../projects/ProjectForm';
import type { Project } from '../projects/types';

/**
 * The plans this organisation holds, and a way to start another.
 *
 * Every member sees this and every member may act on it: roles govern administration, and
 * estimation is a team activity. There is nothing here to hide from anybody, which is what
 * makes it shorter than the members page.
 *
 * Archived projects are asked for rather than mixed in, mirroring the endpoint — a list
 * showing both would need something on every row to say which was which, and the thing
 * somebody archived would still be sitting among their live work.
 */
export function ProjectsPage() {
  const { t } = useTranslation();
  const { account, request } = useAuth();
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [archived, setArchived] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [reloads, setReloads] = useState(0);
  const { message, fieldErrors, report, clear } = useFormFailure([
    'name',
    'description'
  ]);

  const organisationId = account?.organisation.id;

  useEffect(() => {
    if (!organisationId) {
      return;
    }
    let cancelled = false;
    // Cleared here rather than after the answer arrives, so switching listings does not
    // leave the empty state of the previous one on screen while this one loads.
    setProjects(null);

    request<Project[]>(`/projects${archived ? '?archived=true' : ''}`)
      .then((loaded) => {
        if (!cancelled) {
          setProjects(loaded);
          setFailure(null);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setFailure(describeFailure(t, error));
        }
      });
    return () => {
      cancelled = true;
    };
    // Keyed on the organisation as well, so switching to another one re-scopes the list
    // rather than leaving the previous organisation's plans on screen.
  }, [request, organisationId, archived, reloads, t]);

  const create = useCallback(
    async (values: { name: string; description: string | null }) => {
      setCreating(true);
      clear();
      try {
        await request<Project>('/projects', { method: 'POST', body: values });
        // The server decides the order and what a project ends up called, so the list is
        // asked again rather than having the new one pushed onto it here.
        setArchived(false);
        setReloads((count) => count + 1);
      } catch (error) {
        report(error);
      }
      setCreating(false);
    },
    [request, report, clear]
  );

  if (!account) {
    return null;
  }

  return (
    <main className="projects">
      <h1>{t('projects.title')}</h1>
      <p className="lede">
        {t('projects.lede', { organisation: account.organisation.name })}
      </p>

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}

      <p className="listing-switch">
        <button
          type="button"
          className="link"
          onClick={() => setArchived((showing) => !showing)}
        >
          {archived ? t('projects.showCurrent') : t('projects.showArchived')}
        </button>
      </p>

      {projects === null ? (
        <p className="loading" role="status">
          {t('projects.loading')}
        </p>
      ) : projects.length === 0 ? (
        <p className="empty">
          {archived ? t('projects.noneArchived') : t('projects.none')}
        </p>
      ) : (
        <ul className="project-list">
          {projects.map((project) => (
            <li key={project.id}>
              <span className="who">
                <span className="name">
                  <Link to={`/app/projects/${project.id}`}>{project.name}</Link>
                </span>
                {project.description && (
                  <span className="description">{project.description}</span>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}

      <ProjectForm
        // Remounted once a project has been made, which is what empties the fields: the
        // next project starts from nothing rather than from the name of the last one. A
        // refusal does not change this, so what somebody typed survives being told why it
        // was refused.
        key={reloads}
        id="new-project"
        busy={creating}
        submit={t('projects.new.submit')}
        submitting={t('projects.new.submitting')}
        banner={message}
        fieldErrors={fieldErrors}
        onSubmit={(values) => void create(values)}
      >
        <h2>{t('projects.new.title')}</h2>
        <p className="lede">{t('projects.new.lede')}</p>
      </ProjectForm>
    </main>
  );
}
