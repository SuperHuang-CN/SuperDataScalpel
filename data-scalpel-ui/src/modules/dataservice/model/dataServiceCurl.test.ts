import { describe, expect, it } from 'vitest';

import { buildDataServiceCurlCommand } from './dataServiceCurl';

describe('buildDataServiceCurlCommand', () => {
  it('builds a public POST request with a basic paging payload', () => {
    expect(buildDataServiceCurlCommand('https://engine.example.com/', '/open-api/v1/orders')).toBe(
      [
        'curl --request POST \\',
        "  --url 'https://engine.example.com/open-api/v1/orders' \\",
        "  --header 'Content-Type: application/json' \\",
        "  --data '{\"pageNo\":1,\"pageSize\":20}'",
      ].join('\n'),
    );
  });

  it('shell-quotes a URL safely', () => {
    expect(buildDataServiceCurlCommand("https://engine.example.com/team's", 'orders')).toContain(
      "--url 'https://engine.example.com/team'\"'\"'s/orders'",
    );
  });
});
