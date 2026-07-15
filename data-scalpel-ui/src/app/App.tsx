import { lazy, Suspense } from 'react';
import { createBrowserRouter, createRoutesFromElements, Navigate, Route, RouterProvider } from 'react-router-dom';
import { DashboardPage } from '../modules/dashboard';
import { DataSourcePage } from '../modules/datasource';
import { DataServicePage } from '../modules/dataservice';
import { FileDatasetPage } from '../modules/filedataset';
import { DataModelPage } from '../modules/model';
import { ServiceEnginePage } from '../modules/serviceengine';
import {
  LoginPage,
  SystemConfigurationPage,
  SystemPermissionManagementPage,
  SystemRoleManagementPage,
  SystemUserManagementPage,
} from '../modules/system';
import { TaskListPage } from '../modules/task/pages/TaskListPage';
import { PlaceholderPage } from '../shared/components/PlaceholderPage';
import { RequireAuthentication } from './auth/RequireAuthentication';
import { RequirePermission } from './auth/RequirePermission';
import { AppShell } from './layout/AppShell';
import { AppProviders } from './providers/AppProviders';

const TaskOrchestrationPage = lazy(async () => {
  const module = await import('../modules/task/pages/TaskOrchestrationPage');
  return { default: module.TaskOrchestrationPage };
});

const TaskDefinitionPage = lazy(async () => {
  const module = await import('../modules/task/pages/TaskDefinitionPage');
  return { default: module.TaskDefinitionPage };
});

const DataModelDetailPage = lazy(async () => {
  const module = await import('../modules/model/pages/DataModelDetailPage');
  return { default: module.DataModelDetailPage };
});

const router = createBrowserRouter(createRoutesFromElements(
  <>
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
            <Route path="file-dataset" element={<RequirePermission permission="filedataset.view"><FileDatasetPage /></RequirePermission>} />
            <Route path="file-dataset/*" element={<Navigate to="/file-dataset" replace />} />
            <Route path="model" element={<RequirePermission permission="model.view"><DataModelPage /></RequirePermission>} />
            <Route
              path="model/:id"
              element={(
                <RequirePermission permission="model.view">
                  <Suspense fallback="正在加载模型详情…"><DataModelDetailPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route path="model/*" element={<Navigate to="/model" replace />} />
            <Route path="task" element={<RequirePermission permission="task.view"><TaskListPage /></RequirePermission>} />
            <Route
              path="task/:taskId/definition"
              element={(
                <RequirePermission permission="task.view">
                  <Suspense fallback="正在加载 SQL 定义编辑器…"><TaskDefinitionPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route path="task/:taskId" element={<Navigate to="definition" replace />} />
            <Route path="task/orchestration" element={<Suspense fallback="正在加载任务编排器…"><TaskOrchestrationPage /></Suspense>} />
            <Route path="task/*" element={<Navigate to="/task" replace />} />
            <Route path="service-engine" element={<RequirePermission permission="service.engine.view"><ServiceEnginePage /></RequirePermission>} />
            <Route path="service-engine/*" element={<Navigate to="/service-engine" replace />} />
            <Route path="dataservice" element={<RequirePermission permission="service.view"><DataServicePage /></RequirePermission>} />
            <Route path="dataservice/*" element={<Navigate to="/dataservice" replace />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
  </>
));

export const App = () => (
  <AppProviders>
    <RouterProvider router={router} />
  </AppProviders>
);
