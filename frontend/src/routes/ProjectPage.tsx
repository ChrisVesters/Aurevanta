import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams, useSearchParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { ForecastList } from '../projects/ForecastList';
import { ProjectForm } from '../projects/ProjectForm';
import { PlanTabs } from '../projects/PlanTabs';
import { WorkItems } from '../projects/WorkItems';
import {
  FIRST_TAB,
  PLAN_PANEL_ID,
  TAB_PARAM,
  tabFrom,
  tabId
} from '../projects/tabs';
import type { PlanTab } from '../projects/tabs';
import type { Project } from '../projects/types';

/**
 * One plan: what it is called, what it covers, and whether it is still in use.
 *
 * The work, then the forecasts made of it, then what the plan is called. **That order is the
 * argument of the whole product**: ranges go on the work, the engine reads them, and the name
 * is a setting. Each forecast opens on a page of its own, which is where the questions asked
 * *of* it live — they were two more tabs here until November 2026, about the newest forecast
 * only.
 *
 * **A tab is a view of this page and never a page of its own.** The plan stays
 * `/app/projects/{id}`, the tab rides in a query parameter so a link and a reload keep it,
 * and it replaces rather than stacks — otherwise leaving a plan takes as many presses of
 * Back as tabs somebody happened to look at.
 *
 * An identifier belonging to another organisation is a 404 from the server and reads here
 * exactly as one that never existed, because that is the whole point of answering both the
 * same way.
 */
export function ProjectPage() {
  const { t } = useTranslation();
  const { projectId } = useParams();
  const { account, request } = useAuth();
  const [project, setProject] = useState<Project | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [params, setParams] = useSearchParams();
  const { message, fieldErrors, report, clear } = useFormFailure([
    'name',
    'description'
  ]);

  const organisationId = account?.organisation.id;

  useEffect(() => {
    if (!organisationId || !projectId) {
      return;
    }
    let cancelled = false;

    request<Project>(`/projects/${projectId}`)
      .then((loaded) => {
        if (!cancelled) {
          setProject(loaded);
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
  }, [request, organisationId, projectId, t]);

  /**
   * Both actions here answer with the project as it now is, so the screen takes what the
   * server said rather than editing what it was holding. The two can disagree — somebody
   * else may have renamed it a moment ago — and the server is the one that knows.
   */
  const save = useCallback(
    async (values: { name: string; description: string | null }) => {
      setBusy(true);
      setNotice(null);
      clear();
      try {
        setProject(
          await request<Project>(`/projects/${projectId}`, {
            method: 'PATCH',
            body: values
          })
        );
        setNotice(t('projects.project.saved'));
      } catch (error) {
        report(error);
      }
      setBusy(false);
    },
    [request, projectId, report, clear, t]
  );

  const setArchived = useCallback(
    async (archived: boolean) => {
      setBusy(true);
      setNotice(null);
      clear();
      try {
        setProject(
          await request<Project>(
            `/projects/${projectId}/${archived ? 'archive' : 'unarchive'}`,
            { method: 'POST' }
          )
        );
        setNotice(
          archived
            ? t('projects.project.archived')
            : t('projects.project.unarchived')
        );
      } catch (error) {
        setFailure(describeFailure(t, error));
      }
      setBusy(false);
    },
    [request, projectId, clear, t]
  );

  const tab = tabFrom(params.get(TAB_PARAM));

  /**
   * The tab, written where a link and a reload can both read it.
   *
   * Replacing rather than pushing, because a tab is a view of one page: pushing would make
   * Back walk through every tab somebody glanced at before it left the plan. The work tab
   * carries no parameter at all, so the URL people already hold stays the URL it was.
   */
  const show = useCallback(
    (next: PlanTab) => {
      const updated = new URLSearchParams(params);
      if (next === FIRST_TAB) {
        updated.delete(TAB_PARAM);
      } else {
        updated.set(TAB_PARAM, next);
      }
      setParams(updated, { replace: true });
    },
    [params, setParams]
  );

  if (!account) {
    return null;
  }

  return (
    <main className="projects">
      <p className="back">
        <Link to="/app/projects">{t('projects.project.back')}</Link>
      </p>

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}

      {project === null ? (
        !failure && (
          <p className="loading" role="status">
            {t('projects.project.loading')}
          </p>
        )
      ) : (
        <>
          <h1>{project.name}</h1>
          {project.archivedAt && (
            <p className="empty">{t('projects.project.archivedNotice')}</p>
          )}

          <PlanTabs tab={tab} onChange={show} />

          <div
            className="plan-panel"
            id={PLAN_PANEL_ID}
            role="tabpanel"
            aria-labelledby={tabId(tab)}
          >
            {tab === 'work' && <WorkItems projectId={project.id} />}

            {tab === 'forecasts' && <ForecastList projectId={project.id} />}

            {tab === 'settings' && (
              <>
                <ProjectForm
                  // Keyed on what the server last said, so the fields show the saved project
                  // rather than whatever was in them before somebody else's change arrived.
                  key={`${project.name}:${project.description ?? ''}`}
                  id="project"
                  name={project.name}
                  description={project.description}
                  busy={busy}
                  submit={t('projects.project.save')}
                  submitting={t('projects.project.saving')}
                  banner={message}
                  fieldErrors={fieldErrors}
                  onSubmit={(values) => void save(values)}
                >
                  <h2>{t('projects.project.details')}</h2>
                </ProjectForm>

                <p className="archive">
                  <button
                    type="button"
                    className={project.archivedAt ? 'secondary' : 'danger'}
                    disabled={busy}
                    onClick={() => void setArchived(!project.archivedAt)}
                  >
                    {project.archivedAt
                      ? t('projects.project.unarchive')
                      : t('projects.project.archive')}
                  </button>
                  <span className="hint">
                    {t('projects.project.archiveHint')}
                  </span>
                </p>
              </>
            )}
          </div>
        </>
      )}
    </main>
  );
}
