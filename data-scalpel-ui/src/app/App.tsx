import { SystemMcpPage } from '../modules/system-mcp';
import { lazy, Suspense } from 'react';
import { createBrowserRouter, createRoutesFromElements, Navigate, Route, RouterProvider, useParams } from 'react-router-dom';
import { DashboardPage } from '../modules/dashboard';
import { LlmModelManagementPage } from '../modules/assistant';
import { ComputeEngineDetailPage, ComputeEnginePage } from '../modules/computeengine';
import { DataSourcePage } from '../modules/datasource';
import { ApiConsumerPage } from '../modules/dataservice/pages/ApiConsumerPage';
import { DataServicePage } from '../modules/dataservice/pages/DataServicePage';
import { FileDatasetPage } from '../modules/filedataset';
import { DataEntryPage } from '../modules/dataentry';
import { MetricListPage, MetricDetailPage } from '../modules/metric';
import { DataModelPage, ModelFieldTemplatePage, ModelWarehouseLayerPage } from '../modules/model';
import { ServiceEngineDetailPage, ServiceEnginePage } from '../modules/serviceengine';
import { StandardDictionaryDetailPage, StandardDictionaryPage } from '../modules/standard';
import {
  LoginPage,
  SystemConfigurationPage,
  SystemPermissionManagementPage,
  SystemRoleManagementPage,
  SystemUserManagementPage,
} from '../modules/system';
import { TaskListPage, taskViews } from '../modules/task';
import { PlaceholderPage } from '../shared/components/PlaceholderPage';
import { RequireAuthentication } from './auth/RequireAuthentication';
import { RequirePermission } from './auth/RequirePermission';
import { AppShell } from './layout/AppShell';
import { AppProviders } from './providers/AppProviders';

const TaskOrchestrationPage = lazy(async () => {
  const module = await import('../modules/task/pages/TaskOrchestrationPage');
  return { default: module.TaskOrchestrationPage };
});

const RuntimeWorkbenchPage = lazy(async () => ({ default: (await import('../modules/operations/pages/RuntimeWorkbenchPage')).RuntimeWorkbenchPage }));
const AlertCenterPage = lazy(async () => ({ default: (await import('../modules/operations/pages/AlertCenterPage')).AlertCenterPage }));
const AlertConfigurationPage = lazy(async () => ({ default: (await import('../modules/operations/pages/AlertConfigurationPage')).AlertConfigurationPage }));

const TaskDetailPage = lazy(async () => {
  const module = await import('../modules/task/pages/TaskDetailPage');
  return { default: module.TaskDetailPage };
});

const TaskDefinitionEditorPage = lazy(async () => {
  const module = await import('../modules/task/pages/TaskDefinitionEditorPage');
  return { default: module.TaskDefinitionEditorPage };
});

const SparkJarOnlineEditorPage = lazy(async () => {
  const module = await import('../modules/task/pages/SparkJarOnlineEditorPage');
  return { default: module.SparkJarOnlineEditorPage };
});

const MaskingRulePage = lazy(async () => {
  const module = await import('../modules/task/pages/MaskingRulePage');
  return { default: module.MaskingRulePage };
});

const DataModelDetailPage = lazy(async () => {
  const module = await import('../modules/model/pages/DataModelDetailPage');
  return { default: module.DataModelDetailPage };
});

const DataEntryDetailPage = lazy(async () => {
  const module = await import('../modules/dataentry/pages/DataEntryDetailPage');
  return { default: module.DataEntryDetailPage };
});

const DataSourceDetailPage = lazy(async () => {
  const module = await import('../modules/datasource/pages/DataSourceDetailPage');
  return { default: module.DataSourceDetailPage };
});

const PanoramaPage = lazy(() => import('../modules/panorama').then(module => ({ default: module.PanoramaPage })));
const PanoramaDetailPage = lazy(() => import('../modules/panorama').then(module => ({ default: module.PanoramaDetailPage })));

const FileDatasetDetailPage = lazy(async () => {
  const module = await import('../modules/filedataset/pages/FileDatasetDetailPage');
  return { default: module.FileDatasetDetailPage };
});

const DataServiceDefinitionEditorPage = lazy(async () => {
  const module = await import('../modules/dataservice/pages/DataServiceDefinitionEditorPage');
  return { default: module.DataServiceDefinitionEditorPage };
});

const DataServiceDetailPage = lazy(async () => {
  const module = await import('../modules/dataservice/pages/DataServiceDetailPage');
  return { default: module.DataServiceDetailPage };
});

const GatewayOperationsPage = lazy(async () => {
  const module = await import('../modules/dataservice/pages/GatewayOperationsPage');
  return { default: module.GatewayOperationsPage };
});

const AssetPortalPage = lazy(async () => {
  const module = await import('../modules/asset');
  return { default: module.AssetPortalPage };
});

const AssetPortalDetailPage = lazy(async () => {
  const module = await import('../modules/asset');
  return { default: module.AssetPortalDetailPage };
});

const AssetDomainManagementPage = lazy(async () => {
  const module = await import('../modules/asset');
  return { default: module.AssetDomainManagementPage };
});

const AssetManagementPage = lazy(async () => {
  const module = await import('../modules/asset');
  return { default: module.AssetManagementPage };
});

const McpServerPage = lazy(async () => {
  const module = await import('../modules/mcp');
  return { default: module.McpServerPage };
});

const McpServerDetailPage = lazy(async () => {
  const module = await import('../modules/mcp');
  return { default: module.McpServerDetailPage };
});

const McpToolEditorPage = lazy(async () => {
  const module = await import('../modules/mcp');
  return { default: module.McpToolEditorPage };
});

const McpInvocationPage = lazy(async () => {
  const module = await import('../modules/mcp');
  return { default: module.McpInvocationPage };
});

const McpAccessTokenPage = lazy(async () => {
  const module = await import('../modules/mcp');
  return { default: module.McpAccessTokenPage };
});

const LegacyDataServiceEditRedirect = () => {
  const { id } = useParams<{ id: string }>();
  return <Navigate to={id ? `/dataservice/${id}?tab=basic` : '/dataservice'} replace />;
};

const router = createBrowserRouter(createRoutesFromElements(
  <>
        <Route path="login" element={<LoginPage />} />
        <Route path="assets" element={<Suspense fallback="正在加载数据资产门户…"><AssetPortalPage /></Suspense>} />
        <Route path="assets/:id" element={<Suspense fallback="正在加载资产详情…"><AssetPortalDetailPage /></Suspense>} />
        <Route element={<RequireAuthentication />}>
          <Route element={<AppShell />}>
            <Route index element={<DashboardPage />} />
            <Route path="operations" element={<Suspense fallback="正在加载运行工作台…"><RuntimeWorkbenchPage /></Suspense>} />
            <Route path="operations/alerts" element={<Suspense fallback="正在加载告警中心…"><AlertCenterPage /></Suspense>} />
            <Route path="operations/configuration" element={<RequirePermission permission="alert.manage"><Suspense fallback="正在加载告警配置…"><AlertConfigurationPage /></Suspense></RequirePermission>} />
            <Route path="system/system-mcp" element={<RequirePermission permission="system.mcp.view"><SystemMcpPage /></RequirePermission>} />
            <Route path="system/configurations" element={<RequirePermission permission="system.configuration.view"><SystemConfigurationPage /></RequirePermission>} />
            <Route path="system/model-warehouse-layers" element={<RequirePermission permission="system.configuration.view"><ModelWarehouseLayerPage /></RequirePermission>} />
            <Route path="system/ai-models" element={<RequirePermission permission="system.configuration.view"><LlmModelManagementPage /></RequirePermission>} />
            <Route path="system/users" element={<RequirePermission permission="system.user.view"><SystemUserManagementPage /></RequirePermission>} />
            <Route path="system/roles" element={<RequirePermission permission="system.role.view"><SystemRoleManagementPage /></RequirePermission>} />
            <Route path="system/permissions" element={<RequirePermission permission="system.permission.view"><SystemPermissionManagementPage /></RequirePermission>} />
            <Route path="system/*" element={<PlaceholderPage title="系统管理" description="请选择左侧已有的系统管理功能。" />} />
            <Route
              path="asset-management/assets"
              element={(
                <RequirePermission permission="asset.view">
                  <Suspense fallback="正在加载资产管理…"><AssetManagementPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route
              path="asset-management/domains"
              element={(
                <RequirePermission permission="directory.view">
                  <Suspense fallback="正在加载业务领域…"><AssetDomainManagementPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route path="asset-management/*" element={<Navigate to="/asset-management/assets" replace />} />
            <Route path="datasource" element={<RequirePermission permission="datasource.view"><DataSourcePage /></RequirePermission>} />
            <Route
              path="datasource/:id"
              element={(
                <RequirePermission permission="datasource.view">
                  <Suspense fallback="正在加载数据源详情…"><DataSourceDetailPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route path="datasource/*" element={<Navigate to="/datasource" replace />} />
            <Route path="panorama" element={<RequirePermission permission="panorama.view"><Suspense fallback="正在加载全景影像…"><PanoramaPage /></Suspense></RequirePermission>} />
            <Route path="panorama/:id" element={<RequirePermission permission="panorama.view"><Suspense fallback="正在加载全景详情…"><PanoramaDetailPage /></Suspense></RequirePermission>} />
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
            <Route path="data-entry" element={<RequirePermission permission="dataentry.view"><DataEntryPage /></RequirePermission>} />
            <Route
              path="data-entry/:id"
              element={(
                <RequirePermission permission="dataentry.view">
                  <Suspense fallback="正在加载填报详情…"><DataEntryDetailPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route path="data-entry/*" element={<Navigate to="/data-entry" replace />} />
            <Route path="mcp-management" element={<RequirePermission permission="mcp.view"><Suspense fallback="正在加载 MCP 管理…"><McpServerPage /></Suspense></RequirePermission>} />
            <Route path="mcp-management/access-tokens" element={<RequirePermission permission="mcp.token.manage"><Suspense fallback="正在加载 MCP 访问凭证…"><McpAccessTokenPage /></Suspense></RequirePermission>} />
            <Route path="mcp-management/invocations" element={<RequirePermission permission="mcp.view"><Suspense fallback="正在加载 MCP 调用日志…"><McpInvocationPage /></Suspense></RequirePermission>} />
            <Route path="mcp-management/:serverId/tools/:toolId/edit" element={<RequirePermission permission="mcp.update"><Suspense fallback="正在加载 Tool 编辑器…"><McpToolEditorPage /></Suspense></RequirePermission>} />
            <Route path="mcp-management/:id" element={<RequirePermission permission="mcp.view"><Suspense fallback="正在加载 MCP Server…"><McpServerDetailPage /></Suspense></RequirePermission>} />
            <Route path="mcp-management/*" element={<Navigate to="/mcp-management" replace />} />
            {taskViews.map(view => (
              <Route key={view.id} path={view.path.slice(1)} element={<RequirePermission permission="task.view"><TaskListPage key={view.id} view={view.id} /></RequirePermission>} />
            ))}
            <Route
              path="task/masking-rules"
              element={(
                <RequirePermission permission="task.view">
                  <Suspense fallback="正在加载脱敏规则…"><MaskingRulePage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route
              path="task/:taskId/online-code"
              element={(
                <RequirePermission permission="task.update">
                  <Suspense fallback="正在加载在线开发工作台…"><SparkJarOnlineEditorPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route
              path="task/:taskId/definition"
              element={(
                <RequirePermission permission="task.update">
                  <Suspense fallback="正在加载任务定义编辑器…"><TaskDefinitionEditorPage /></Suspense>
                </RequirePermission>
              )}
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
            <Route path="metrics" element={<RequirePermission permission="metric.view"><MetricListPage /></RequirePermission>} />
            <Route path="metrics/:id" element={<RequirePermission permission="metric.view"><MetricDetailPage /></RequirePermission>} />
            <Route path="service-engine" element={<RequirePermission permission="service.engine.view"><ServiceEnginePage /></RequirePermission>} />
            <Route path="service-engine/:id" element={<RequirePermission permission="service.engine.view"><ServiceEngineDetailPage /></RequirePermission>} />
            <Route path="service-engine/*" element={<Navigate to="/service-engine" replace />} />
            <Route path="compute-engine" element={<RequirePermission permission="compute.engine.view"><ComputeEnginePage /></RequirePermission>} />
            <Route path="compute-engine/:id" element={<RequirePermission permission="compute.engine.view"><ComputeEngineDetailPage /></RequirePermission>} />
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
              path="dataservice/:id/edit"
              element={(
                <RequirePermission permission="service.view">
                  <LegacyDataServiceEditRedirect />
                </RequirePermission>
              )}
            />
            <Route
              path="dataservice/:id/definition/edit"
              element={(
                <RequirePermission permission="service.update">
                  <Suspense fallback="正在加载数据服务定义编辑器…"><DataServiceDefinitionEditorPage /></Suspense>
                </RequirePermission>
              )}
            />
            <Route
              path="dataservice/:id"
              element={(
                <RequirePermission permission="service.view">
                  <Suspense fallback="正在加载数据服务详情…"><DataServiceDetailPage /></Suspense>
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
