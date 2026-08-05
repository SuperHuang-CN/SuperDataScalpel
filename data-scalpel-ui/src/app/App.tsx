import { lazy, Suspense } from 'react';
import { createBrowserRouter, createRoutesFromElements, Navigate, Route, RouterProvider, useParams } from 'react-router-dom';
import { DashboardPage } from '../modules/dashboard';
import { ComputeEnginePage } from '../modules/computeengine';
import { DataSourcePage } from '../modules/datasource';
import { ApiConsumerPage, DataServicePage } from '../modules/dataservice';
import { FileDatasetPage } from '../modules/filedataset';
import { DataModelPage, ModelFieldTemplatePage, ModelWarehouseLayerPage } from '../modules/model';
import { ServiceEnginePage } from '../modules/serviceengine';
import { StandardDictionaryDetailPage, StandardDictionaryPage } from '../modules/standard';
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

const TaskDetailPage = lazy(async () => {
  const module = await import('../modules/task/pages/TaskDetailPage');
  return { default: module.TaskDetailPage };
});

const MaskingRulePage = lazy(async () => {
  const module = await import('../modules/task/pages/MaskingRulePage');
  return { default: module.MaskingRulePage };
});

const DataModelDetailPage = lazy(async () => {
  const module = await import('../modules/model/pages/DataModelDetailPage');
  return { default: module.DataModelDetailPage };
});

const FileDatasetDetailPage = lazy(async () => {
  const module = await import('../modules/filedataset/pages/FileDatasetDetailPage');
  return { default: module.FileDatasetDetailPage };
});

const DataServiceEditorPage = lazy(async () => {
  const module = await import('../modules/dataservice/pages/DataServiceEditorPage');
  return { default: module.DataServiceEditorPage };
});

const GatewayOperationsPage = lazy(async () => {
  const module = await import('../modules/dataservice/pages/GatewayOperationsPage');
  return { default: module.GatewayOperationsPage };
});

const LegacyTaskDefinitionRedirect = () => {
  const { taskId } = useParams<{ taskId: string }>();
  return <Navigate to={taskId ? `/task/${taskId}?tab=definition` : '/task'} replace />;
};

const router = createBrowserRouter(createRoutesFromElements(
  <>
        <Route path="login" element={<LoginPage />} />
        <Route element={<RequireAuthentication />}>
          <Route element={<AppShell />}>
            <Route index element={<DashboardPage />} />
            <Route path="system/configurations" element={<RequirePermission permission="system.configuration.view"><SystemConfigurationPage /></RequirePermission>} />
            <Route path="system/model-warehouse-layers" element={<RequirePermission permission="system.configuration.view"><ModelWarehouseLayerPage /></RequirePermission>} />
            <Route path="system/users" element={<RequirePermission permission="system.user.view"><SystemUserManagementPage /></RequirePermission>} />
            <Route path="system/roles" element={<RequirePermission permission="system.role.view"><SystemRoleManagementPage /></RequirePermission>} />
            <Route path="system/permissions" element={<RequirePermission permission="system.permission.view"><SystemPermissionManagementPage /></RequirePermission>} />
            <Route path="system/*" element={<PlaceholderPage title="系统管理" description="请选择左侧已有的系统管理功能。" />} />
            <Route path="datasource" element={<RequirePermission permission="datasource.view"><DataSourcePage /></RequirePermission>} />
            <Route path="datasource/*" element={<Navigate to="/datasource" replace />} />
            <Route path="file-dataset" element={<RequirePermission permission="filedataset.view"><FileDatasetPage /></RequirePermission>} />
            <Route
              path="file-dataset/:id"
              element={(
                <RequirePermission permission="filedataset.view">
                  <Suspense fallback="正在加载文件数据集详情…"><FileDatasetDetailPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route path="file-dataset/*" element={<Navigate to="/file-dataset" replace />} />
            <Route
              path="standard/dictionaries"
              element={<RequirePermission permission="standard.dictionary.view"><StandardDictionaryPage /></RequirePermission>}
            />
            <Route
              path="standard/dictionaries/:id"
              element={<RequirePermission permission="standard.dictionary.view"><StandardDictionaryDetailPage /></RequirePermission>}
            />
            <Route path="standard/*" element={<Navigate to="/standard/dictionaries" replace />} />
            <Route path="model" element={<RequirePermission permission="model.view"><DataModelPage /></RequirePermission>} />
            <Route
              path="model/field-templates"
              element={<RequirePermission permission="model.view"><ModelFieldTemplatePage /></RequirePermission>}
            />
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
              path="task/masking-rules"
              element={(
                <RequirePermission permission="task.view">
                  <Suspense fallback="正在加载脱敏规则…"><MaskingRulePage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route
              path="task/:taskId/definition"
              element={<LegacyTaskDefinitionRedirect />}
            />
            <Route
              path="task/:taskId"
              element={(
                <RequirePermission permission="task.view">
                  <Suspense fallback="正在加载任务详情…"><TaskDetailPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route path="task/orchestration" element={<Suspense fallback="正在加载任务编排器…"><TaskOrchestrationPage /></Suspense>} />
            <Route path="task/*" element={<Navigate to="/task" replace />} />
            <Route path="service-engine" element={<RequirePermission permission="service.engine.view"><ServiceEnginePage /></RequirePermission>} />
            <Route path="service-engine/*" element={<Navigate to="/service-engine" replace />} />
            <Route path="compute-engine" element={<RequirePermission permission="compute.engine.view"><ComputeEnginePage /></RequirePermission>} />
            <Route path="compute-engine/*" element={<Navigate to="/compute-engine" replace />} />
            <Route path="dataservice" element={<RequirePermission permission="service.view"><DataServicePage /></RequirePermission>} />
            <Route path="dataservice/consumers" element={<RequirePermission permission="service.view"><ApiConsumerPage /></RequirePermission>} />
            <Route
              path="dataservice/operations"
              element={(
                <RequirePermission permission="service.view">
                  <Suspense fallback="正在加载网关调用统计…"><GatewayOperationsPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route
              path="dataservice/new/standard"
              element={(
                <RequirePermission permission="service.create">
                  <Suspense fallback="正在加载标准服务编辑器…"><DataServiceEditorPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route
              path="dataservice/new/sql"
              element={(
                <RequirePermission permission="service.create">
                  <Suspense fallback="正在加载 SQL 服务工作台…"><DataServiceEditorPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route
              path="dataservice/new/script"
              element={(
                <RequirePermission permission="service.create">
                  <Suspense fallback="正在加载脚本服务工作台…"><DataServiceEditorPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route
              path="dataservice/:id"
              element={(
                <RequirePermission permission="service.view">
                  <Suspense fallback="正在加载数据服务详情…"><DataServiceEditorPage /></Suspense>
                </RequirePermission>
              )}
            />
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
