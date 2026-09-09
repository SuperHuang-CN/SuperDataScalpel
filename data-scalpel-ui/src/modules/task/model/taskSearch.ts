import type { TaskFilters, TaskType } from './task';

import { andSearch, orSearch, escapeSearchText as escapeDslText, searchEquals as equals } from '../../../shared/search';

export const buildTaskSearch = (filters: TaskFilters, types?: readonly TaskType[]): string | undefined => {
  const conditions = [
    filters.keyword?.trim()
      ? `name:*"${escapeDslText(filters.keyword.trim())}"*`
      : undefined,
    filters.status ? equals('status', filters.status) : undefined,
    filters.type ? equals('type', filters.type) : undefined,
    filters.directoryIds?.length
      ? filters.directoryIds.length === 1
        ? equals('directoryId', filters.directoryIds[0])
        : `(${filters.directoryIds.map((id) => equals('directoryId', id)).join(' OR ')})`
      : undefined,
    filters.uncategorized ? 'directoryId:null' : undefined,
  ].filter((condition): condition is string => Boolean(condition));

  return andSearch(
    types ? orSearch(...types.map(type => equals('type', type))) : undefined,
    ...conditions,
  );
};
