import type { DataServiceDetail, DataServiceSummary } from './dataService';

type GatewayPublicationView = Pick<DataServiceDetail | DataServiceSummary, 'gatewayBindings' | 'revision'>;

export const publishedGatewayBinding = (service: GatewayPublicationView) => service.gatewayBindings.find(
  (binding) => binding.publicationStatus === 'PUBLISHED'
    && binding.publishedRevision === service.revision
    && Boolean(binding.gatewayUrl),
);

export const gatewayOperationError = (
  service: Pick<GatewayPublicationView, 'gatewayBindings'>,
) => service.gatewayBindings
  .map((binding) => binding.lastError)
  .find((error): error is string => Boolean(error));
