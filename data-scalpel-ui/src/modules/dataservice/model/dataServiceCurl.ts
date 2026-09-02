import type {
  DataServiceAccessMode,
  DataServiceDetail,
  PlatformTypeDefinition,
  SqlServiceParameterDefinition,
} from './dataService';

const shellQuote = (value: string): string => `'${value.replace(/'/g, `'"'"'`)}'`;

export const buildDataServiceAccessUrl = (publicUrl: string, routePath: string): string => {
  const baseUrl = publicUrl.replace(/\/+$/, '');
  const normalizedRoutePath = routePath.replace(/^\/+/, '');
  return normalizedRoutePath ? `${baseUrl}/${normalizedRoutePath}` : baseUrl;
};

const exampleValue = (type: PlatformTypeDefinition): unknown => {
  switch (type.type) {
    case 'BOOLEAN': return true;
    case 'BYTE':
    case 'SHORT':
    case 'INTEGER': return 1;
    case 'LONG': return '1001';
    case 'FLOAT':
    case 'DOUBLE': return 1.5;
    case 'DECIMAL': return '12.34';
    case 'STRING': return 'example';
    case 'DATE': return '2026-01-01';
    case 'TIMESTAMP': return '2026-01-01T00:00:00Z';
    case 'TIMESTAMP_NTZ': return '2026-01-01T00:00:00';
    case 'BINARY': return null;
  }
};

const argumentsExample = (parameters: SqlServiceParameterDefinition[]) => Object.fromEntries(
  parameters.map((parameter) => [parameter.name, exampleValue(parameter.typeDefinition)]),
);

export const buildDataServiceCurlCommand = (
  publicUrl: string,
  routePath: string,
  service?: Pick<DataServiceDetail, 'type' | 'sqlDefinition'> & { accessMode?: DataServiceAccessMode },
): string => {
  const requestUrl = buildDataServiceAccessUrl(publicUrl, routePath);
  const body = service?.type === 'SQL_QUERY'
    ? {
      pageNo: 1,
      pageSize: 20,
      arguments: argumentsExample(service.sqlDefinition?.parameters ?? []),
      returnCount: false,
    }
    : service?.type === 'SCRIPT_API'
      ? {}
      : { pageNo: 1, pageSize: 20, returnCount: false };

  const headers = [
    `  --header ${shellQuote('Content-Type: application/json')} \\`,
  ];
  if (service?.accessMode === 'SUBSCRIPTION_REQUIRED') {
    headers.push(`  --header ${shellQuote('X-API-Key: <YOUR_API_KEY>')} \\`);
  }

  return [
    'curl --request POST \\',
    `  --url ${shellQuote(requestUrl)} \\`,
    ...headers,
    `  --data ${shellQuote(JSON.stringify(body))}`,
  ].join('\n');
};
