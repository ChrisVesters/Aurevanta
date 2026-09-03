import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useLoaded } from '../api/useLoaded';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { ProjectForm } from '../projects/ProjectForm';
import { Modal } from '../ui/Modal';
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
  const [archived, setArchived] = useState(false);
  const [creating, setCreating] = useState(false);
  /**
   * Whether somebody is starting a plan.
   *
   * The form used to sit under the list, which put every plan the organisation holds between
   * somebody and the way to start another. It is asked for now, from a control above the
   * list — starting a plan is what somebody comes to this page to do, and nothing in the
   * list below says anything about how to fill the form in.
   */
  const [starting, setStarting] = useState(false);
  const [reloads, setReloads] = useState(0);
  const { message, fieldErrors, report, clear } = useFormFailure([
    'name',
    'description'
  ]);

  const organisationId = account?.organisation.id;

  // Keyed on the organisation as well, so switching to another one re-scopes the list
  // rather than leaving the previous organisation's plans on screen.
  const { data: projects, failure } = useLoaded<Project[]>(
    organisationId ? `/projects${archived ? '?archived=true' : ''}` : null,
    [organisationId, archived, reloads]
  );

  const create = useCallback(
    async (values: { name: string; description: string | null }) => {
      setCreating(true);
      clear();
      try {
        await request<Project>('/projects', { method: 'POST', body: values });
        // The server decides the order and what a project ends up called, so the list is
        // asked again rather than having the new one pushed onto it here.
        setArchived(false);
        // Closed only once the server has taken it. A refusal leaves the form up holding
        // what was typed, which is why this is the page's state and not the dialog's own.
        setStarting(false);
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

      {/*
        Above the plans rather than under them, because an organisation's list of them only
        grows and the way to start another must not be a thing somebody scrolls to. Offered
        against the archived listing as well, which is the same decision `create` makes by
        switching back to the live one afterwards.
      */}
      <p className="add">
        <button
          type="button"
          className="primary"
          onClick={() => {
            // Cleared on the way in, so a refusal from the last attempt is not the first
            // thing on an empty form.
            clear();
            setStarting(true);
          }}
        >
          {t('projects.new.open')}
        </button>
      </p>

      <Modal
        title={t('projects.new.title')}
        open={starting}
        onClose={() => setStarting(false)}
      >
        <ProjectForm
          id="new-project"
          busy={creating}
          submit={t('projects.new.submit')}
          submitting={t('projects.new.submitting')}
          banner={message}
          fieldErrors={fieldErrors}
          onSubmit={(values) => void create(values)}
          onCancel={() => setStarting(false)}
        >
          <p className="lede">{t('projects.new.lede')}</p>
        </ProjectForm>
      </Modal>

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
                {/*
                  Coverage without opening the plan, which is the reason the server
                  counts it rather than leaving the page to work it out from a list it
                  does not load.
                */}
                <span className="coverage">
                  {t('projects.coverage', {
                    estimated: project.estimatedItemCount,
                    total: project.itemCount
                  })}
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
