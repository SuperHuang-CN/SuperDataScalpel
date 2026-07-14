/**
 * Frontend representation of the backend SearchRequest contract.
 */
export interface SearchRequest {
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const toSearchParams = (request: SearchRequest): URLSearchParams => {
  const params = new URLSearchParams();

  if (request.search?.trim()) params.set('search', request.search);
  if (request.page !== undefined) params.set('page', String(request.page));
  if (request.size !== undefined) params.set('size', String(request.size));
  if (request.sort?.trim()) params.set('sort', request.sort);

  return params;
};
