import { afterEach, expect, it, vi } from 'vitest';
import { getSdkApiDocumentation } from './sdkApi';

afterEach(() => vi.unstubAllGlobals());
it('uses the shared /api base and reads the current document without writing', async () => {
  const data = { version: 'test', fingerprint: 'fresh', types: [] };
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(data)));
  vi.stubGlobal('fetch', fetcher);
  expect(await getSdkApiDocumentation()).toEqual(data);
  expect(fetcher).toHaveBeenCalledWith('/api/v1/spark-jar-sdk-api', expect.not.objectContaining({ method: 'POST' }));
});
