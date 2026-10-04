// Uses the existing developer environment. Does not start mock servers or alter service definitions.
// Credentials remain in memory; temporary gateway resources are removed in finally.
import fs from 'node:fs/promises';
import path from 'node:path';
import { performance } from 'node:perf_hooks';
import http from 'node:http';

const root = process.cwd();
const gateway = process.env.GATEWAY_TEST_URL ?? 'http://localhost:19000';
const admin = process.env.GATEWAY_TEST_ADMIN_URL ?? 'http://localhost:8080';
const serviceCode = process.env.GATEWAY_TEST_SERVICE_CODE ?? 'sql';
const config = await fs.readFile(path.join(root, 'super-api-gateway/src/main/resources/config/application-local.yml'), 'utf8');
const raw = config.match(/^\s*machine-token:\s*(.+)$/m)?.[1]?.trim().replace(/^["']|["']$/g, '');
if (!raw) throw new Error('Gateway local machine-token is not configured');
const placeholder = raw.match(/^\$\{([^:}]+):([^}]+)\}$/);
const machine = placeholder ? process.env[placeholder[1]] ?? placeholder[2] : raw;
const managementHeaders = { 'X-Super-Gateway-Admin-Token': machine };
const checks = [];
const report = { startedAt: new Date().toISOString(), checks, performance: [], cleanup: [] };
const jsonHeaders = { 'Content-Type': 'application/json' };
async function request(url, { method = 'GET', headers = {}, body, expected } = {}) {
  const response = await fetch(url, { method, headers: { ...jsonHeaders, ...headers }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000) });
  const text = await response.text();
  if (expected !== undefined && response.status !== expected) throw new Error(`Unexpected HTTP ${response.status}; expected ${expected}; ${new URL(url).pathname}`);
  return { status: response.status, headers: response.headers, value: text ? JSON.parse(text) : null };
}
async function manage(p, body) {
  const result = await request(`${gateway}/admin-api/v1${p}`, { method: body === undefined ? 'GET' : 'POST', headers: managementHeaders, body });
  if (result.status >= 400) throw new Error(`Management HTTP ${result.status}: ${p}`);
  return result;
}
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function loaded() {
  for (let i = 0; i < 80; i++) {
    const { value } = await manage('/runtime');
    const online = value.instances.filter((v) => v.state !== 'OFFLINE');
    if (online.length && online.every((v) => v.state === 'READY' && v.loadedRevision >= value.targetRevision)) return;
    await pause(250);
  }
  throw new Error('Gateway snapshot did not converge');
}
function check(name, passed) { if (!passed) throw new Error(`FAILED: ${name}`); checks.push(name); console.log(`PASS ${name}`); }
async function call(url, expected, key, body = {}) {
  const response = await request(url, { method: 'POST', body, headers: key ? { 'X-API-Key': key } : {}, expected });
  if (expected >= 400) check(`ProblemDetail ${expected}`, !!response.value.code && !!response.value.timestamp && response.value.status === expected);
  return response;
}
async function benchmark(name, url, key, count = 300, concurrency = 8) {
  for (let i = 0; i < 20; i++) await call(url, 200, key);
  let index = 0; const durations = []; const statuses = {};
  const started = performance.now();
  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (index++ < count) {
      const begin = performance.now();
      const result = await request(url, { method: 'POST', body: {}, headers: key ? { 'X-API-Key': key } : {} });
      durations.push(performance.now() - begin);
      statuses[result.status] = (statuses[result.status] ?? 0) + 1;
    }
  }));
  const elapsed = performance.now() - started;
  durations.sort((a, b) => a - b);
  report.performance.push({ name, count, concurrency, elapsedMs: +elapsed.toFixed(1), requestsPerSecond: +(count * 1000 / elapsed).toFixed(1), p50Ms: +durations[Math.ceil(count * .5) - 1].toFixed(2), p95Ms: +durations[Math.ceil(count * .95) - 1].toFixed(2), p99Ms: +durations[Math.ceil(count * .99) - 1].toFixed(2), statuses });
  check(`benchmark ${name}: all responses 200`, statuses[200] === count);
}

let temporaryService, consumer, subscription;
try {
  const login = await request(`${admin}/api/v1/auth/login`, { method: 'POST', body: { username: process.env.GATEWAY_TEST_ADMIN_USER ?? 'admin', password: process.env.GATEWAY_TEST_ADMIN_PASSWORD ?? 'admin123456' }, expected: 200 });
  const adminHeaders = { Authorization: `Bearer ${login.value.accessToken}` };
  const contract = (await request(`${admin}/v3/api-docs`, { headers: adminHeaders, expected: 200 })).value;
  for (const endpoint of ['/api/v1/gateway-access-statistics/recent', '/api/v1/data-services/{id}/gateway-traffic-policy', '/api/v1/gateway-access-validities/{kind}/{id}']) {
    check(`OpenAPI operation ${endpoint}`, !!contract.paths[endpoint]?.get?.summary);
  }
  for (const name of ['GatewayAccessRecentResponse', 'GatewayTrafficPolicyRequest', 'GatewayTrafficPolicyResponse', 'GatewayAccessValidityRequest', 'GatewayAccessValidityResponse']) {
    const schema = contract.components.schemas[name];
    check(`OpenAPI field documentation ${name}`, !!schema?.properties && Object.values(schema.properties).every((field) => !!field.description));
  }
  const services = (await manage('/services?page=0&size=200')).value.content;
  const existing = services.find((v) => v.code === serviceCode && v.source === 'DATASCALPEL' && v.accessMode === 'PUBLIC');
  if (!existing) throw new Error('Select an existing public read-only SQL query service with GATEWAY_TEST_SERVICE_CODE');
  const definition = (await request(`${admin}/api/v1/data-services/${existing.externalId}`, { headers: adminHeaders, expected: 200 })).value;
  if (definition.type !== 'SQL_QUERY') throw new Error('Performance target must be a read-only SQL_QUERY service');
  const routes = (await manage(`/routes?serviceId=${existing.id}`)).value;
  const route = routes.find((v) => v.enabled && !v.pathPattern.includes('{') && !v.pathPattern.includes('*'));
  if (!route) throw new Error('No literal route available for test');
  const existingUrl = gateway + route.pathPattern;
  const first = await call(existingUrl, 200);
  check('public published service responds', !!first.headers.get('x-request-id'));
  let ingested;
  for (let i = 0; i < 60; i++) {
    const logs = (await request(`${admin}/api/v1/gateway-access-logs?gatewayRequestId=${first.headers.get('x-request-id')}`, { headers: adminHeaders, expected: 200 })).value.content;
    if (logs.length) { ingested = logs[0]; break; }
    await pause(500);
  }
  check('Kafka -> Admin log -> correct service identity', ingested?.dataServiceId === existing.externalId && ingested?.responseStatus === 200);
  check('upstream status attributed', ingested?.upstreamStatus === '200' && !ingested?.gatewayError);
  const recent = (await request(`${admin}/api/v1/gateway-access-statistics/recent?dataServiceId=${existing.externalId}`, { headers: adminHeaders, expected: 200 })).value;
  check('current-hour successful call appears in recent statistics', recent.ingestionEnabled && recent.requestCount > 0 && recent.p95LatencyMs !== null);
  const policyPath = `${admin}/api/v1/data-services/${existing.externalId}`;
  const savedPolicy = (await request(`${policyPath}/gateway-traffic-policy`, { headers: adminHeaders, expected: 200 })).value;
  await request(`${policyPath}/actions/update-gateway-traffic-policy`, { method: 'POST', headers: adminHeaders, body: savedPolicy.policy, expected: 200 });
  await loaded();
  check('Admin policy save/read/propagation', (await request(`${policyPath}/gateway-traffic-policy`, { headers: adminHeaders, expected: 200 })).value.loadedRevision >= savedPolicy.targetRevision);
  await request(`${policyPath}/actions/update-gateway-traffic-policy`, { method: 'POST', headers: adminHeaders,
    body: { ...savedPolicy.policy, allowedCidrs: ['not-an-ip'] }, expected: 400 });
  await request(`${policyPath}/actions/update-gateway-traffic-policy`, { method: 'POST', headers: adminHeaders,
    body: { ...savedPolicy.policy, allowedCidrs: [null] }, expected: 400 });
  check('Admin rejects invalid CIDR and null entries without changing policy',
    JSON.stringify((await request(`${policyPath}/gateway-traffic-policy`, { headers: adminHeaders, expected: 200 })).value.policy) === JSON.stringify(savedPolicy.policy));
  const managedConsumers = (await manage('/consumers?page=0&size=200')).value.content.filter((v) => v.source === 'DATASCALPEL');
  let checkedAdminValidity = false;
  for (const managedConsumer of managedConsumers) {
    const managedKeys = (await manage(`/consumers/${managedConsumer.id}/api-keys`)).value;
    const managedSubscriptions = (await manage(`/subscriptions?consumerId=${managedConsumer.id}&page=0&size=200`)).value.content;
    for (const [kind, objects] of [['keys', managedKeys], ['subscriptions', managedSubscriptions]]) {
      const object = objects.find((v) => v.source === 'DATASCALPEL' && v.externalId);
      if (!object) continue;
      const endpoint = `${admin}/api/v1/gateway-access-validities/${kind}/${object.externalId}`;
      const prior = (await request(endpoint, { headers: adminHeaders, expected: 200 })).value;
      const unchanged = { validFrom: prior.validFrom, expiresAt: prior.expiresAt, requestsPerSecond: prior.requestsPerSecond };
      await request(`${endpoint}/actions/update`, { method: 'POST', headers: adminHeaders, body: unchanged, expected: 200 });
      await request(`${endpoint}/actions/update`, { method: 'POST', headers: adminHeaders,
        body: { ...unchanged, validFrom: '2030-01-02T00:00:00Z', expiresAt: '2030-01-01T00:00:00Z' }, expected: 400 });
      check(`Admin ${kind} validity read/save and invalid-range rejection`, true);
      checkedAdminValidity = true;
    }
  }
  check('managed credentials/subscriptions available for Admin integration verification', checkedAdminValidity);

  const stamp = Date.now();
  const serviceBody = { code: `gateway-check-${stamp}`, name: 'Gateway verification (temporary)', upstreamUri: existing.upstreamUri, accessMode: 'SUBSCRIPTION_REQUIRED', connectTimeoutMs: 1000, responseTimeoutMs: 5000, enabled: true, source: 'MANUAL' };
  temporaryService = (await manage('/services', serviceBody)).value;
  const testPath = `/gateway-check-${stamp}/query`;
  await manage('/routes', { serviceId: temporaryService.id, code: `check-route-${stamp}`, name: 'Temporary query route', pathPattern: testPath, methods: ['POST'], order: 0, stripPrefixSegments: 0, upstreamPath: route.upstreamPath ?? route.pathPattern, enabled: true, source: 'MANUAL' });
  consumer = (await manage('/consumers', { code: `check-consumer-${stamp}`, name: 'Temporary verification consumer', enabled: true, source: 'MANUAL' })).value;
  let key = (await manage(`/consumers/${consumer.id}/api-keys`, { name: 'verification', source: 'MANUAL' })).value;
  await loaded();
  const testUrl = gateway + testPath;
  await call(testUrl, 401); await call(testUrl, 403, key.secret);
  subscription = (await manage('/subscriptions', { consumerId: consumer.id, serviceId: temporaryService.id, source: 'MANUAL' })).value;
  await loaded(); await call(testUrl, 200, key.secret); check('subscription authorizes consumer, not service-wide key', true);
  const oldKey = key.secret;
  key = (await manage(`/consumers/${consumer.id}/api-keys/${key.id}/actions/rotate`, {})).value;
  await loaded(); await call(testUrl, 401, oldKey); await call(testUrl, 200, key.secret); check('key rotation invalidates old key', true);
  await manage(`/consumers/${consumer.id}/actions/disable`, {}); await loaded(); await call(testUrl, 403, key.secret);
  await manage(`/consumers/${consumer.id}/actions/enable`, {}); await loaded(); await call(testUrl, 200, key.secret);
  const validity = async (kind, id, settings) => {
    await manage(`/access-validities/${kind}/${id}/actions/update`, { validFrom: null, expiresAt: null, requestsPerSecond: 0, ...settings });
    await loaded();
  };
  await validity('keys', key.id, { expiresAt: new Date(Date.now() - 1000).toISOString() });
  await call(testUrl, 401, key.secret);
  await validity('keys', key.id, { expiresAt: new Date(Date.now() + 3600000).toISOString() });
  await call(testUrl, 200, key.secret); check('expired key renews without replacing secret', true);
  await validity('subscriptions', subscription.id, { validFrom: new Date(Date.now() + 3600000).toISOString() });
  await call(testUrl, 403, key.secret);
  await validity('subscriptions', subscription.id, { expiresAt: new Date(Date.now() - 1000).toISOString() });
  await call(testUrl, 403, key.secret);
  await validity('subscriptions', subscription.id, { requestsPerSecond: 1 });
  await call(testUrl, 200, key.secret); await call(testUrl, 429, key.secret);
  await validity('subscriptions', subscription.id, {});
  await call(testUrl, 200, key.secret); check('subscription validity, renewal and individual quota enforced', true);
  const unrestricted = { requestsPerSecond: 0, consumerRequestsPerSecond: 0, maxConcurrentRequests: 0, maxRequestBytes: 0, allowedCidrs: [], deniedCidrs: [] };
  const policy = async (changes) => { await manage(`/services/${temporaryService.id}/actions/update-traffic-policy`, { ...unrestricted, ...changes }); await loaded(); };
  await policy({ requestsPerSecond: 1 }); await call(testUrl, 200, key.secret); await call(testUrl, 429, key.secret);
  await policy({ allowedCidrs: ['192.0.2.0/24'] });
  await request(testUrl, { method: 'POST', body: {}, headers: { 'X-API-Key': key.secret, 'X-Forwarded-For': '192.0.2.1' }, expected: 403 }); check('spoofed forwarded IP does not bypass allowlist', true);
  await policy({ maxRequestBytes: 1 }); await call(testUrl, 413, key.secret);
  const chunked = await new Promise((resolve, reject) => {
    const req = http.request(testUrl, { method: 'POST', headers: { 'X-API-Key': key.secret, 'Content-Type': 'application/json', 'Transfer-Encoding': 'chunked' } }, (res) => {
      res.resume(); res.on('end', () => resolve(res.statusCode));
    });
    req.on('error', reject); req.setTimeout(10000, () => req.destroy(new Error('Chunked request timed out')));
    req.write('{}'); req.end();
  });
  check('chunked body cannot bypass request size limit', chunked === 413);
  await policy({ maxConcurrentRequests: 1 });
  const concurrent = await Promise.all(Array.from({ length: 32 }, () => request(testUrl, { method: 'POST', body: {}, headers: { 'X-API-Key': key.secret } })));
  check('real concurrent traffic is bounded', concurrent.some((r) => r.status === 429) && concurrent.some((r) => r.status === 200) && concurrent.every((r) => [200, 429].includes(r.status)));
  const hanging = http.request(testUrl, { method: 'POST', headers: { 'X-API-Key': key.secret, 'Content-Type': 'application/json', 'Transfer-Encoding': 'chunked' } });
  hanging.on('error', () => {}); hanging.write('{');
  await pause(200);
  try { await call(testUrl, 429, key.secret); } finally { hanging.destroy(); }
  await pause(300); await call(testUrl, 200, key.secret); check('client cancellation releases concurrency lease', true);
  check('cancelled request recorded as 499, not success',
    (await manage('/runtime/telemetry')).value.traffic.recentCalls.some((v) => v.serviceCode === serviceBody.code && v.status === 499));
  await policy({});
  await manage(`/services/${temporaryService.id}/actions/update`, { ...serviceBody, upstreamUri: 'http://127.0.0.1:1' });
  await loaded();
  const unavailable = await request(testUrl, { method: 'POST', body: {}, headers: { 'X-API-Key': key.secret } });
  check('unavailable upstream yields safe 502 or connection-timeout 504',
    [502, 504].includes(unavailable.status) && unavailable.value.status === unavailable.status
    && ['GATEWAY_UPSTREAM_UNAVAILABLE', 'GATEWAY_UPSTREAM_TIMEOUT'].includes(unavailable.value.code));
  await manage(`/services/${temporaryService.id}/actions/update`, serviceBody); await loaded();
  await benchmark('direct-engine', existing.upstreamUri + (route.upstreamPath ?? route.pathPattern));
  await benchmark('gateway-public', existingUrl);
  await benchmark('gateway-authenticated', testUrl, key.secret);
  await benchmark('gateway-authenticated-burst', testUrl, key.secret, 1000, 32);
  await manage(`/subscriptions/${subscription.id}/actions/revoke`, {}); await loaded(); await call(testUrl, 403, key.secret); check('subscription revocation blocks access', true);
  report.telemetry = (await manage('/runtime/telemetry')).value;
  // Safe diagnostic fields only. Never persist management tokens, API keys or response data.
  check('delivery continues without drops', report.telemetry.delivery.delivered > 0 && report.telemetry.delivery.dropped === 0);
} catch (error) {
  report.error = error.message;
  process.exitCode = 1;
  console.error(error.message);
} finally {
  for (const [name, action] of [
    ['temporary subscription', async () => { if (subscription) await manage(`/subscriptions/${subscription.id}/actions/revoke`, {}); }],
    ['temporary consumer and credentials', async () => { if (consumer) { await manage(`/consumers/${consumer.id}/actions/disable`, {}); await manage(`/consumers/${consumer.id}/actions/delete`, {}); } }],
    ['temporary service and routes', async () => { if (temporaryService) { await manage(`/services/${temporaryService.id}/actions/disable`, {}); await manage(`/services/${temporaryService.id}/actions/delete`, {}); } }],
  ]) {
    try { await action(); report.cleanup.push({ name, success: true }); }
    catch (error) { report.cleanup.push({ name, success: false, error: error.message }); process.exitCode = 1; }
  }
  report.finishedAt = new Date().toISOString();
  await fs.mkdir(path.join(root, '.local/gateway-verification'), { recursive: true });
  await fs.writeFile(path.join(root, '.local/gateway-verification/result.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({ checks: checks.length, performance: report.performance, cleanup: report.cleanup, error: report.error }, null, 2));
}
