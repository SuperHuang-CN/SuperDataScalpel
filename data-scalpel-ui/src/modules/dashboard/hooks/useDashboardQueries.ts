import { useEffect, useMemo, useState } from 'react';
import { useModelStatistics, useQualityStatistics } from '../../model';
import { useTaskStatistics } from '../../task';
import { useServiceStatistics, useGatewayUsage, useGatewayAccessTrend, useGatewayAccessOverview } from '../../dataservice';
import { useAssetStatistics } from '../../asset';
import { useRuntimeOverview, useAlerts, buildAlertSearch } from '../../operations';
export const useDashboardQueries = (permissions: string[], days: number) => {
 const [now, setNow] = useState(() => Date.now());
 useEffect(() => {
   const timer = window.setInterval(() => { if (document.visibilityState === 'visible') setNow(Date.now()); }, 30_000);
   return () => window.clearInterval(timer);
 }, []);
 const can = (permission: string) => permissions.includes(permission);
 const runtimeRange = useMemo(() => ({ from: new Date(now - days * 86400_000).toISOString(), to: new Date(now).toISOString() }), [now, days]);
 const completedHour = Math.floor(now / 3600_000) * 3600_000;
 const usageRange = useMemo(() => ({ from: new Date(completedHour - 7 * 86400_000).toISOString(), to: new Date(completedHour).toISOString() }), [completedHour]);
 const models = useModelStatistics(can('model.view'));
 const quality = useQualityStatistics(can('model.view'));
 const tasks = useTaskStatistics(can('task.view'));
 const services = useServiceStatistics(can('service.view'));
 const usage = useGatewayUsage(usageRange, can('service.view'));
 const trend = useGatewayAccessTrend(usageRange, can('service.view'));
 const accessOverview = useGatewayAccessOverview(usageRange, can('service.view'));
 const showAssets = can('model.view') && can('asset.view');
 const assets = useAssetStatistics(showAssets);
 const canObserve = can('task.view') || can('compute.engine.view');
 const runtime = useRuntimeOverview(runtimeRange, canObserve);
 const alerts = useAlerts({ search: buildAlertSearch({ status: 'ACTIVE' }), page: 0, size: 5, sort: 'severity,-status,-occurredAt' }, canObserve);
 const enabledQueries = [
  ...(can('model.view') ? [models, quality] : []), ...(can('task.view') ? [tasks] : []),
  ...(can('service.view') ? [services, usage, trend, accessOverview] : []), ...(showAssets ? [assets] : []),
  ...(canObserve ? [runtime, alerts] : []),
 ];
 return { models, quality, tasks, services, usage, trend, accessOverview, assets, runtime, alerts, canObserve,
  refreshing: enabledQueries.some(query => query.isFetching),
  refresh: () => { setNow(Date.now()); void Promise.allSettled(enabledQueries.map(query => query.refetch())); },
 };
};
