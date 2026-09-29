import { useEffect } from 'react';
import { Button, Result, Space } from 'antd';
import { isRouteErrorResponse, useRouteError } from 'react-router-dom';

const MODULE_RECOVERY_WINDOW_MS = 30_000;
const MODULE_ERROR_MARKERS = [
  'failed to fetch dynamically imported module',
  'error loading dynamically imported module',
  'importing a module script failed',
  'unable to preload css',
];

const errorMessage = (error: unknown): string => {
  if (error instanceof Error) return error.message;
  if (isRouteErrorResponse(error)) return `${error.status} ${error.statusText}`;
  return String(error ?? '');
};

const isRouteModuleLoadError = (error: unknown): boolean => {
  const message = errorMessage(error).toLowerCase();
  return MODULE_ERROR_MARKERS.some(marker => message.includes(marker));
};

const recoveryKey = () => `datascalpel:route-module-recovery:${window.location.pathname}${window.location.search}`;

const markRecovery = (key: string) => {
  try {
    window.sessionStorage.setItem(key, String(Date.now()));
  } catch {
    // Keep the manual reload action available when browser storage is unavailable.
  }
};

export const AppRouteErrorPage = () => {
  const error = useRouteError();
  const moduleLoadFailed = isRouteModuleLoadError(error);
  const key = recoveryKey();

  useEffect(() => {
    if (!moduleLoadFailed) return;
    try {
      const lastRecovery = Number(window.sessionStorage.getItem(key));
      if (Number.isFinite(lastRecovery) && Date.now() - lastRecovery < MODULE_RECOVERY_WINDOW_MS) return;
      markRecovery(key);
      window.location.reload();
    } catch {
      // A blocked sessionStorage must not cause an automatic reload loop.
    }
  }, [key, moduleLoadFailed]);

  const reload = () => {
    markRecovery(key);
    window.location.reload();
  };

  return (
    <main className="app-route-error">
      <Result
        status="error"
        title={moduleLoadFailed ? '页面资源加载失败' : '页面暂时无法显示'}
        subTitle={moduleLoadFailed
          ? '应用可能刚刚更新。请重新加载页面获取最新资源。'
          : '页面运行时发生异常，请重新加载后再试。'}
        extra={(
          <Space>
            <Button type="primary" onClick={reload}>重新加载</Button>
            <Button onClick={() => window.location.assign('/')}>返回首页</Button>
          </Space>
        )}
      />
    </main>
  );
};
