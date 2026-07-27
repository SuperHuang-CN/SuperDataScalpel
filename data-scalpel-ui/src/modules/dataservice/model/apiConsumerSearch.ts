import type { ApiConsumerFilters } from './apiConsumer';

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const contains = (field: string, value: string) => `${field}:*"${escapeDslText(value)}"*`;

export const buildApiConsumerSearch = (filters: ApiConsumerFilters): string | undefined => {
  const keyword = filters.keyword?.trim();
  return keyword
    ? `(${contains('name', keyword)} OR ${contains('code', keyword)})`
    : undefined;
};
