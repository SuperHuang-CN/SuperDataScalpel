import { describe, expect, it } from 'vitest';

import { buildDataServiceAccessUrl, buildDataServiceCurlCommand } from './dataServiceCurl';

describe('buildDataServiceAccessUrl', () => {
  it('joins the Service Engine public URL and service route with exactly one slash', () => {
    expect(buildDataServiceAccessUrl('https://engine.example.com/', '/open-api/v1/orders')).toBe(
      'https://engine.example.com/open-api/v1/orders',
    );
  });
});

describe('buildDataServiceCurlCommand', () => {
  it('uses a complete gateway URL without appending an extra slash', () => {
    expect(buildDataServiceCurlCommand('http://gateway.test:8000/open-api/v1/orders', '')).toContain(
      "--url 'http://gateway.test:8000/open-api/v1/orders'",
    );
  });

  it('builds a public POST request with a basic paging payload', () => {
    expect(buildDataServiceCurlCommand('https://engine.example.com/', '/open-api/v1/orders')).toBe(
      [
        'curl --request POST \\',
        "  --url 'https://engine.example.com/open-api/v1/orders' \\",
        "  --header 'Content-Type: application/json' \\",
        "  --data '{\"pageNo\":1,\"pageSize\":20,\"returnCount\":false}'",
      ].join('\n'),
    );
  });

  it('shell-quotes a URL safely', () => {
    expect(buildDataServiceCurlCommand("https://engine.example.com/team's", 'orders')).toContain(
      "--url 'https://engine.example.com/team'\"'\"'s/orders'",
    );
  });

  it('adds the API Key header placeholder for subscription-protected services', () => {
    const command = buildDataServiceCurlCommand(
      'https://gateway.example.com/open-api/v1/orders',
      '',
      {
        type: 'STANDARD_TABLE',
        sqlDefinition: null,
        accessMode: 'SUBSCRIPTION_REQUIRED',
      },
    );

    expect(command).toContain("--header 'X-API-Key: <YOUR_API_KEY>'");
  });

  it('builds SQL service arguments and disables count by default', () => {
    const command = buildDataServiceCurlCommand(
      'https://engine.example.com',
      '/open-api/v1/customers',
      {
        type: 'SQL_QUERY',
        accessMode: 'PUBLIC',
        sqlDefinition: {
          dataSourceId: 'source-1',
          modelIds: ['model-1', 'model-2'],
          sqlText: 'select id from customer where department_id = :departmentId',
          version: 1,
          parameters: [{
            name: 'departmentId',
            typeDefinition: { type: 'LONG', length: null, precision: null, scale: null },
            required: true,
            description: null,
          }],
        },
      },
    );

    expect(command).toContain(
      '--data \'{"pageNo":1,"pageSize":20,"arguments":{"departmentId":"1001"},"returnCount":false}\'',
    );
  });
});
