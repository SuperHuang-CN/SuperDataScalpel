import { lazy, Suspense } from 'react';
import { createBrowserRouter, createRoutesFromElements, Navigate, Route, RouterProvider, useParams } from 'react-router-dom';
import { LoginPage } from '../modules/system/pages/LoginPage';
import { taskViews } from '../modules/task/model/taskViews';
import { PlaceholderPage } from '../shared/components/PlaceholderPage';
import { RequireAuthentication } from './auth/RequireAuthentication';
import { RequirePermission } from './auth/RequirePermission';
import { AppShell } from './layout/AppShell';
import { AppProviders } from './providers/AppProviders';

const DashboardPage = lazy(async () => ({ default: (await import('../modules/dashboard/pages/DashboardPage')).DashboardPage }));
const RuntimeWorkbenchPage = lazy(async () => ({ default: (await import('../modules/operations/pages/RuntimeWorkbenchPage')).RuntimeWorkbenchPage }));
const AlertCenterPage = lazy(async () => ({ default: (await import('../modules/operations/pages/AlertCenterPage')).AlertCenterPage }));
const AlertConfigurationPage = lazy(async () => ({ default: (await import('../modules/operations/pages/AlertConfigurationPage')).AlertConfigurationPage }));
const SystemMcpPage = lazy(async () => ({ default: (await import('../modules/system-mcp/pages/SystemMcpPage')).SystemMcpPage }));
const SystemConfigurationPage = lazy(async () => ({ default: (await import('../modules/system/pages/SystemConfigurationPage')).SystemConfigurationPage }));
const SystemPermissionManagementPage = lazy(async () => ({ default: (await import('../modules/system/pages/SystemPermissionManagementPage')).SystemPermissionManagementPage }));
const SystemRoleManagementPage = lazy(async () => ({ default: (await import('../modules/system/pages/SystemRoleManagementPage')).SystemRoleManagementPage }));
const SystemUserManagementPage = lazy(async () => ({ default: (await import('../modules/system/pages/SystemUserManagementPage')).SystemUserManagementPage }));
const AssetPortalPage = lazy(async () => ({ default: (await import('../modules/asset/pages/AssetPortalPage')).AssetPortalPage }));
const AssetPortalDetailPage = lazy(async () => ({ default: (await import('../modules/asset/pages/AssetPortalDetailPage')).AssetPortalDetailPage }));
const AssetDomainManagementPage = lazy(async () => ({ default: (await import('../modules/asset/pages/AssetDomainManagementPage')).AssetDomainManagementPage }));
const AssetManagementPage = lazy(async () => ({ default: (await import('../modules/asset/pages/AssetManagementPage')).AssetManagementPage }));
const DataSourcePage = lazy(async () => ({ default: (await import('../modules/datasource/pages/DataSourcePage')).DataSourcePage }));
const DataSourceDetailPage = lazy(async () => ({ default: (await import('../modules/datasource/pages/DataSourceDetailPage')).DataSourceDetailPage }));
const PanoramaPage = lazy(async () => ({ default: (await import('../modules/panorama/pages/PanoramaPage')).PanoramaPage }));
const PanoramaDetailPage = lazy(async () => ({ default: (await import('../modules/panorama/pages/PanoramaDetailPage')).PanoramaDetailPage }));
const FileDatasetPage = lazy(async () => ({ default: (await import('../modules/filedataset/pages/FileDatasetPage')).FileDatasetPage }));
const FileDatasetDetailPage = lazy(async () => ({ default: (await import('../modules/filedataset/pages/FileDatasetDetailPage')).FileDatasetDetailPage }));
const StandardDictionaryPage = lazy(async () => ({ default: (await import('../modules/standard/pages/StandardDictionaryPage')).StandardDictionaryPage }));
const StandardDictionaryDetailPage = lazy(async () => ({ default: (await import('../modules/standard/pages/StandardDictionaryDetailPage')).StandardDictionaryDetailPage }));
const DataModelPage = lazy(async () => ({ default: (await import('../modules/model/pages/DataModelPage')).DataModelPage }));
const ModelFieldTemplatePage = lazy(async () => ({ default: (await import('../modules/model/pages/ModelFieldTemplatePage')).ModelFieldTemplatePage }));
const ModelWarehouseLayerPage = lazy(async () => ({ default: (await import('../modules/model/pages/ModelWarehouseLayerPage')).ModelWarehouseLayerPage }));
const DataModelDetailPage = lazy(async () => ({ default: (await import('../modules/model/pages/DataModelDetailPage')).DataModelDetailPage }));
const BusinessObjectTypeListPage = lazy(async () => ({ default: (await import('../modules/ontology/pages/BusinessObjectTypeListPage')).BusinessObjectTypeListPage }));
const BusinessObjectTypeDetailPage = lazy(async () => ({ default: (await import('../modules/ontology/pages/BusinessObjectTypeDetailPage')).BusinessObjectTypeDetailPage }));
const DataEntryPage = lazy(async () => ({ default: (await import('../modules/dataentry/pages/DataEntryPage')).DataEntryPage }));
const DataEntryDetailPage = lazy(async () => ({ default: (await import('../modules/dataentry/pages/DataEntryDetailPage')).DataEntryDetailPage }));
const McpServerPage = lazy(async () => ({ default: (await import('../modules/mcp/pages/McpServerPage')).McpServerPage }));
const McpServerDetailPage = lazy(async () => ({ default: (await import('../modules/mcp/pages/McpServerDetailPage')).McpServerDetailPage }));
const McpToolEditorPage = lazy(async () => ({ default: (await import('../modules/mcp/pages/McpToolEditorPage')).McpToolEditorPage }));
const McpInvocationPage = lazy(async () => ({ default: (await import('../modules/mcp/pages/McpInvocationPage')).McpInvocationPage }));
const McpAccessTokenPage = lazy(async () => ({ default: (await import('../modules/mcp/pages/McpAccessTokenPage')).McpAccessTokenPage }));
const TaskListPage = lazy(async () => ({ default: (await import('../modules/task/pages/TaskListPage')).TaskListPage }));
const TaskOrchestrationPage = lazy(async () => ({ default: (await import('../modules/task/pages/TaskOrchestrationPage')).TaskOrchestrationPage }));
const TaskDetailPage = lazy(async () => ({ default: (await import('../modules/task/pages/TaskDetailPage')).TaskDetailPage }));
const TaskDefinitionEditorPage = lazy(async () => ({ default: (await import('../modules/task/pages/TaskDefinitionEditorPage')).TaskDefinitionEditorPage }));
const SparkJarOnlineEditorPage = lazy(async () => ({ default: (await import('../modules/task/pages/SparkJarOnlineEditorPage')).SparkJarOnlineEditorPage }));
const MaskingRulePage = lazy(async () => ({ default: (await import('../modules/task/pages/MaskingRulePage')).MaskingRulePage }));
const MetricListPage = lazy(async () => ({ default: (await import('../modules/metric/pages/MetricListPage')).MetricListPage }));
const MetricDetailPage = lazy(async () => ({ default: (await import('../modules/metric/pages/MetricDetailPage')).MetricDetailPage }));
const ServiceEnginePage = lazy(async () => ({ default: (await import('../modules/serviceengine/pages/ServiceEnginePage')).ServiceEnginePage }));
const ServiceEngineDetailPage = lazy(async () => ({ default: (await import('../modules/serviceengine/pages/ServiceEngineDetailPage')).ServiceEngineDetailPage }));
const ComputeEnginePage = lazy(async () => ({ default: (await import('../modules/computeengine/pages/ComputeEnginePage')).ComputeEnginePage }));
const ComputeEngineDetailPage = lazy(async () => ({ default: (await import('../modules/computeengine/pages/ComputeEngineDetailPage')).ComputeEngineDetailPage }));
const DataServicePage = lazy(async () => ({ default: (await import('../modules/dataservice/pages/DataServicePage')).DataServicePage }));
const ApiConsumerPage = lazy(async () => ({ default: (await import('../modules/dataservice/pages/ApiConsumerPage')).ApiConsumerPage }));
const GatewayOperationsPage = lazy(async () => ({ default: (await import('../modules/dataservice/pages/GatewayOperationsPage')).GatewayOperationsPage }));
const DataServiceDefinitionEditorPage = lazy(async () => ({ default: (await import('../modules/dataservice/pages/DataServiceDefinitionEditorPage')).DataServiceDefinitionEditorPage }));
const DataServiceDetailPage = lazy(async () => ({ default: (await import('../modules/dataservice/pages/DataServiceDetailPage')).DataServiceDetailPage }));

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
            <Route index element={<Suspense fallback="正在加载工作台…"><DashboardPage /></Suspense>} />
            <Route path="operations" element={<Suspense fallback="正在加载运行工作台…"><RuntimeWorkbenchPage /></Suspense>} />
            <Route path="operations/alerts" element={<Suspense fallback="正在加载告警中心…"><AlertCenterPage /></Suspense>} />
            <Route path="operations/configuration" element={<RequirePermission permission="alert.manage"><Suspense fallback="正在加载告警配置…"><AlertConfigurationPage /></Suspense></RequirePermission>} />
            <Route path="system/system-mcp" element={<RequirePermission permission="system.mcp.view"><Suspense fallback="正在加载系统 MCP…"><SystemMcpPage /></Suspense></RequirePermission>} />
            <Route path="system/configurations" element={<RequirePermission permission="system.configuration.view"><Suspense fallback="正在加载系统配置…"><SystemConfigurationPage /></Suspense></RequirePermission>} />
            <Route path="system/model-warehouse-layers" element={<RequirePermission permission="system.configuration.view"><Suspense fallback="正在加载模型分层…"><ModelWarehouseLayerPage /></Suspense></RequirePermission>} />
            <Route path="system/users" element={<RequirePermission permission="system.user.view"><Suspense fallback="正在加载用户管理…"><SystemUserManagementPage /></Suspense></RequirePermission>} />
            <Route path="system/roles" element={<RequirePermission permission="system.role.view"><Suspense fallback="正在加载角色管理…"><SystemRoleManagementPage /></Suspense></RequirePermission>} />
            <Route path="system/permissions" element={<RequirePermission permission="system.permission.view"><Suspense fallback="正在加载权限管理…"><SystemPermissionManagementPage /></Suspense></RequirePermission>} />
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
            <Route path="datasource" element={<RequirePermission permission="datasource.view"><Suspense fallback="正在加载数据源…"><DataSourcePage /></Suspense></RequirePermission>} />
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
            <Route path="file-dataset" element={<RequirePermission permission="filedataset.view"><Suspense fallback="正在加载文件数据集…"><FileDatasetPage /></Suspense></RequirePermission>} />
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
              element={<RequirePermission permission="standard.dictionary.view"><Suspense fallback="正在加载码表…"><StandardDictionaryPage /></Suspense></RequirePermission>}
            />
            <Route
              path="standard/dictionaries/:id"
              element={<RequirePermission permission="standard.dictionary.view"><Suspense fallback="正在加载码表详情…"><StandardDictionaryDetailPage /></Suspense></RequirePermission>}
            />
            <Route path="standard/*" element={<Navigate to="/standard/dictionaries" replace />} />
            <Route path="model" element={<RequirePermission permission="model.view"><Suspense fallback="正在加载数据模型…"><DataModelPage /></Suspense></RequirePermission>} />
            <Route
              path="model/field-templates"
              element={<RequirePermission permission="model.view"><Suspense fallback="正在加载字段模板…"><ModelFieldTemplatePage /></Suspense></RequirePermission>}
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
            <Route path="business-object-types" element={<RequirePermission permission="ontology.view"><Suspense fallback="正在加载业务对象类型…"><BusinessObjectTypeListPage /></Suspense></RequirePermission>} />
            <Route path="business-object-types/:id" element={<RequirePermission permission="ontology.view"><Suspense fallback="正在加载业务对象详情…"><BusinessObjectTypeDetailPage /></Suspense></RequirePermission>} />
            <Route path="business-object-types/*" element={<Navigate to="/business-object-types" replace />} />
            <Route path="data-entry" element={<RequirePermission permission="dataentry.view"><Suspense fallback="正在加载数据填报…"><DataEntryPage /></Suspense></RequirePermission>} />
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
              <Route key={view.id} path={view.path.slice(1)} element={<RequirePermission permission="task.view"><Suspense fallback="正在加载任务列表…"><TaskListPage key={view.id} view={view.id} /></Suspense></RequirePermission>} />
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
            <Route path="metrics" element={<RequirePermission permission="metric.view"><Suspense fallback="正在加载指标…"><MetricListPage /></Suspense></RequirePermission>} />
            <Route path="metrics/:id" element={<RequirePermission permission="metric.view"><Suspense fallback="正在加载指标详情…"><MetricDetailPage /></Suspense></RequirePermission>} />
            <Route path="service-engine" element={<RequirePermission permission="service.engine.view"><Suspense fallback="正在加载服务引擎…"><ServiceEnginePage /></Suspense></RequirePermission>} />
            <Route path="service-engine/:id" element={<RequirePermission permission="service.engine.view"><Suspense fallback="正在加载服务引擎详情…"><ServiceEngineDetailPage /></Suspense></RequirePermission>} />
            <Route path="service-engine/*" element={<Navigate to="/service-engine" replace />} />
            <Route path="compute-engine" element={<RequirePermission permission="compute.engine.view"><Suspense fallback="正在加载计算引擎…"><ComputeEnginePage /></Suspense></RequirePermission>} />
            <Route path="compute-engine/:id" element={<RequirePermission permission="compute.engine.view"><Suspense fallback="正在加载计算引擎详情…"><ComputeEngineDetailPage /></Suspense></RequirePermission>} />
            <Route path="compute-engine/*" element={<Navigate to="/compute-engine" replace />} />
            <Route path="dataservice" element={<RequirePermission permission="service.view"><Suspense fallback="正在加载数据服务…"><DataServicePage /></Suspense></RequirePermission>} />
            <Route path="dataservice/consumers" element={<RequirePermission permission="service.view"><Suspense fallback="正在加载 API 消费者…"><ApiConsumerPage /></Suspense></RequirePermission>} />
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
