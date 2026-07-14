import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { DashboardPage } from '../modules/dashboard';
import { DataSourcePage } from '../modules/datasource';
import { DataModelPage } from '../modules/model';
import {
  LoginPage,
  SystemConfigurationPage,
  SystemPermissionManagementPage,
  SystemRoleManagementPage,
  SystemUserManagementPage,
} from '../modules/system';
import { PlaceholderPage } from '../shared/components/PlaceholderPage';
import { RequireAuthentication } from './auth/RequireAuthentication';
import { RequirePermission } from './auth/RequirePermission';
import { AppShell } from './layout/AppShell';
import { AppProviders } from './providers/AppProviders';

const TaskOrchestrationPage = lazy(async () => {
  const module = await import('../modules/task');
  return { default: module.TaskOrchestrationPage };
});

export const App = () => (
  <AppProviders>
    <BrowserRouter>
      <Routes>
        <Route path="login" element={<LoginPage />} />
        <Route element={<RequireAuthentication />}>
          <Route element={<AppShell />}>
            <Route index element={<DashboardPage />} />
            <Route path="system/configurations" element={<RequirePermission permission="system.configuration.view"><SystemConfigurationPage /></RequirePermission>} />
            <Route path="system/users" element={<RequirePermission permission="system.user.view"><SystemUserManagementPage /></RequirePermission>} />
            <Route path="system/roles" element={<RequirePermission permission="system.role.view"><SystemRoleManagementPage /></RequirePermission>} />
            <Route path="system/permissions" element={<RequirePermission permission="system.permission.view"><SystemPermissionManagementPage /></RequirePermission>} />
            <Route path="system/*" element={<PlaceholderPage title="系统管理" description="请选择左侧已有的系统管理功能。" />} />
            <Route path="datasource" element={<RequirePermission permission="datasource.view"><DataSourcePage /></RequirePermission>} />
            <Route path="datasource/*" element={<Navigate to="/datasource" replace />} />
            <Route path="model" element={<RequirePermission permission="model.view"><DataModelPage /></RequirePermission>} />
            <Route path="model/*" element={<Navigate to="/model" replace />} />
            <Route path="task/orchestration" element={<Suspense fallback="正在加载任务编排器…"><TaskOrchestrationPage /></Suspense>} />
            <Route path="task/*" element={<PlaceholderPage title="任务管理" description="任务定义、调度和运行记录将在这里逐步落地。" />} />
            <Route path="dataservice/*" element={<PlaceholderPage title="数据服务" description="数据服务定义、发布和调用监控将在这里逐步落地。" />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </AppProviders>
);
