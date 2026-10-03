import { useDataSources } from '../../datasource';
import { useFileDatasets } from '../../filedataset';
import { useDataEntryForms } from '../../dataentry';
import { useMetrics } from '../../metric';
import { usePanoramas } from '../../panorama';

const firstPage = { page: 0, size: 1 };

export const useDashboardResourceCounts = (permissions: string[]) => {
  const can = (permission: string) => permissions.includes(permission);
  const sources = useDataSources(firstPage, can('datasource.view'));
  const files = useFileDatasets(firstPage, can('filedataset.view'));
  const forms = useDataEntryForms(firstPage, can('dataentry.view'));
  const metrics = useMetrics(firstPage, can('metric.view'));
  const panoramas = usePanoramas(firstPage, can('panorama.view'));
  const counts = { sources, files, forms, metrics, panoramas };
  const allowed = [
    { permission: 'datasource.view', query: sources },
    { permission: 'filedataset.view', query: files },
    { permission: 'dataentry.view', query: forms },
    { permission: 'metric.view', query: metrics },
    { permission: 'panorama.view', query: panoramas },
  ].filter(item => can(item.permission));
  return {
    counts,
    refreshing: allowed.some(item => item.query.isFetching),
    refresh: () => Promise.allSettled(allowed.map(item => item.query.refetch())),
  };
};
