import { useTranslation } from 'react-i18next';
import { Tabs } from '../ui/Tabs';
import { PLAN_PANEL_ID, PLAN_TABS, tabId, type PlanTab } from './tabs';

/**
 * Which part of a plan is being read. The strip itself is `ui/Tabs`, shared with the parts
 * of one forecast; this names the plan's parts and says what the strip is called.
 */
export function PlanTabs({
  tab,
  onChange
}: {
  tab: PlanTab;
  onChange: (tab: PlanTab) => void;
}) {
  const { t } = useTranslation();
  return (
    <Tabs
      tabs={PLAN_TABS}
      tab={tab}
      label={t('projects.tabs.label')}
      nameOf={(name) => t(`projects.tabs.${name}`)}
      idOf={tabId}
      panelId={PLAN_PANEL_ID}
      onChange={onChange}
    />
  );
}
