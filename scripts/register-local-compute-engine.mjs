// Used only by start-local-dev.sh. Discovery/registration is the same flow as the Admin UI.
const { ADMIN_ACCESS_TOKEN: token, BACKEND_INTERNAL_URL: admin, DISPATCHER_URL: dispatcher,
  DISPATCHER_TOKEN: dispatcherToken, COMPUTE_ENGINE_NAME: name } = process.env;
if (!token || !admin || !dispatcher || !dispatcherToken || !name) throw new Error('本地计算引擎登记缺少启动参数');
async function post(path, body) {
  const response = await fetch(`${admin}/api/v1/compute-engines${path}`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body), signal: AbortSignal.timeout(30000),
  });
  const responseText = await response.text();
  if (!response.ok) {
    let detail = responseText.trim();
    try {
      const problem = JSON.parse(responseText);
      detail = problem.detail || problem.title || problem.code || detail;
    } catch {
      // Keep a non-JSON response body so local startup failures remain diagnosable.
    }
    throw new Error(`本地计算引擎登记失败（HTTP ${response.status}）${detail ? `：${detail}` : ''}`);
  }
  return JSON.parse(responseText);
}
const connection = { dispatcherBaseUrl: dispatcher, accessToken: dispatcherToken };
async function main() {
  let directory;
  let row;
  for (let attempt = 0; attempt < 15; attempt++) {
    directory = await post('/actions/query-targets', connection);
    row = directory.targets.find(item => item.target.targetKey === 'local-docker');
    if (!row) throw new Error('Dispatcher 未启用 local-docker 目标，请检查 application-instance.yml');
    if (row.target.ready) break;
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  if (!row.target.ready) throw new Error('本地 Docker 目标尚未就绪，请检查 Docker 与 Runner 配置');
  const result = await post('/actions/register-targets', {
    ...connection, dispatcherInstanceId: directory.dispatcherInstanceId,
    targets: [{ targetKey: row.target.targetKey, targetFingerprint: row.target.targetFingerprint,
      name, maxQueuedExecutions: 20, maxConcurrentSubmissions: 2, maxInFlightApplications: 2 }],
  });
  if (!result.items.every(item => item.success)) {
    const details = result.items.filter(item => !item.success).map(item => item.error).filter(Boolean).join('；');
    throw new Error(`本地计算引擎注册未完成${details ? `：${details}` : '，请到计算引擎页面处理原注册或旧通道迁移'}`);
  }
  console.log('本地计算引擎已按 Dispatcher 发现结果注册，消息通道由实例配置提供。');
}

main().catch(error => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
