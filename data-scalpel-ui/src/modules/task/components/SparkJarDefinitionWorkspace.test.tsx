import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SparkJarAuthoringModeChoice, SparkJarDevelopmentKitPanel, SparkJarRuntimeConfiguration, SparkJarOnlineCodeSummary } from './SparkJarDefinitionWorkspace';
import type { SparkJarOnlineSource } from '../model/task';

afterEach(cleanup);

describe('online code application summary', () => {
  const source: SparkJarOnlineSource = {
    taskId: 'task', definitionVersion: 1, sourceCode: 'class Job {}', sourceSha256: 'draft',
    compiledSourceSha256: null, persisted: true, hasUncompiledChanges: true,
    currentJarOrigin: null, currentJar: null,
  };
  const applied: SparkJarOnlineSource = { ...source, hasUncompiledChanges: false,
    compiledSourceSha256: 'draft', currentJarOrigin: 'ONLINE_COMPILED', appliedAt: '2026-09-27T12:00:00Z',
    currentJar: { fileName: 'job.jar', sha256: 'jar', sizeBytes: 100, jobClass: 'example.Job', jobApiVersion: 1, jobMode: 'BATCH' } };
  const props = { failed: false, loading: false, opening: false, onRetry: vi.fn(), onOpen: vi.fn() };

  it('does not confuse a saved or trial-only draft with an applied jar', () => {
    render(<SparkJarOnlineCodeSummary {...props} source={source} />);
    expect(screen.getByText('尚未应用')).toBeVisible();
    expect(screen.queryByText(/最近应用时间/)).not.toBeInTheDocument();
  });
  it('shows applied source, current entry class and real application time', () => {
    render(<SparkJarOnlineCodeSummary {...props} source={applied} />);
    expect(screen.getByText('代码已应用到任务')).toBeVisible();
    expect(screen.getByText('example.Job')).toBeVisible();
    expect(screen.getByText(/最近应用时间：.*2026/)).toBeVisible();
  });
  it('keeps the applied package distinct from newer draft changes', () => {
    render(<SparkJarOnlineCodeSummary {...props} source={{ ...applied, hasUncompiledChanges: true, appliedAt: null }} />);
    expect(screen.getByText('有修改未应用')).toBeVisible();
    expect(screen.getByText('最近应用时间：—')).toBeVisible();
  });
  it('does not label an uploaded jar as online applied', () => {
    render(<SparkJarOnlineCodeSummary {...props} source={{ ...applied, currentJarOrigin: 'UPLOADED', compiledSourceSha256: null }} />);
    expect(screen.getByText('当前使用上传的运行包')).toBeVisible();
    expect(screen.queryByText(/最近应用时间/)).not.toBeInTheDocument();
  });
  it('does not report a loading request as an unapplied task', () => {
    render(<SparkJarOnlineCodeSummary {...props} loading />);
    expect(screen.getByText('正在读取代码状态…')).toBeVisible();
    expect(screen.queryByText('尚未应用')).not.toBeInTheDocument();
  });
  it('offers retry on errors instead of showing stale success', async () => {
    render(<SparkJarOnlineCodeSummary {...props} source={applied} failed />);
    expect(screen.getByRole('alert')).toHaveTextContent('代码状态加载失败');
    expect(screen.queryByText('代码已应用到任务')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(props.onRetry).toHaveBeenCalledOnce();
    await userEvent.click(screen.getByRole('button', { name: '进入编辑器' }));
    expect(props.onOpen).toHaveBeenCalledOnce();
  });
});

describe('Spark JAR workspace interactions', () => {
  it.each(['在线开发', '上传 JAR'] as const)('requires confirmation after choosing %s', async (label) => {
    const confirm = vi.fn();
    render(<SparkJarAuthoringModeChoice onConfirm={confirm} />);
    expect(screen.getByRole('button', { name: '确认并配置' })).toBeDisabled();
    await userEvent.click(screen.getByRole('radio', { name: new RegExp(label) }));
    expect(confirm).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: '确认并配置' }));
    expect(confirm).toHaveBeenCalledWith(label === '在线开发' ? 'ONLINE' : 'UPLOAD');
  });

  const kitProps = {
    status: { label: '未生成', color: 'default' }, generation: null, artifact: null,
    configurationDirty: false, inputModelCount: 1, jdbcTableCount: 2, outputModelCount: 1,
    running: false, submitting: false, downloading: false, onGenerate: vi.fn(), onDownload: vi.fn(),
  };

  it('generates a real task kit and only exposes download after an artifact exists', async () => {
    const { rerender } = render(<SparkJarDevelopmentKitPanel {...kitProps} />);
    expect(screen.queryByRole('button', { name: /下载开发工程/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /生成开发工程/ }));
    expect(kitProps.onGenerate).toHaveBeenCalledOnce();
    rerender(<SparkJarDevelopmentKitPanel {...kitProps} configurationDirty
      artifact={{ sizeBytes: 1024, sha256: 'abc', generatedAt: '2026-09-25T00:00:00Z', matchesSavedConfiguration: true }} />);
    expect(screen.getByText('当前为上一次成功生成的开发包')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: /下载开发工程/ }));
    expect(kitProps.onDownload).toHaveBeenCalledOnce();
    expect(screen.queryByText('查看生成代码')).not.toBeInTheDocument();
    expect(screen.queryByText('下载通用模板')).not.toBeInTheDocument();
  });

  it('prevents duplicate generation while submitting', () => {
    render(<SparkJarDevelopmentKitPanel {...kitProps} submitting />);
    expect(screen.getByText('正在提交生成任务')).toBeVisible();
    expect(screen.getByRole('button', { name: /生成开发工程/ })).toBeDisabled();
  });

  it('keeps runtime parameters reachable and closes the drawer without pretending to save', async () => {
    const change = vi.fn();
    const items = [{ key: 'resources', label: '资源', summary: '2 核', placement: 'primary' as const, children: <input aria-label="CPU" /> },
      { key: 'jvm', label: 'Driver JVM', summary: '0 项', children: <input aria-label="JVM 参数" /> }];
    const { rerender } = render(<SparkJarRuntimeConfiguration items={items} activeKeys={[]} onChange={change}
      overview={{ environment: 'Local Docker', resources: '2 核', timeout: '1 小时' }} />);
    await userEvent.click(screen.getByRole('button', { name: /调整/ }));
    expect(change).toHaveBeenLastCalledWith(['resources']);
    rerender(<SparkJarRuntimeConfiguration items={items} activeKeys={['jvm']} onChange={change}
      overview={{ environment: 'Local Docker', resources: '2 核', timeout: '1 小时' }} />);
    expect(screen.getByRole('textbox', { name: 'JVM 参数' })).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: '完成配置' }));
    expect(change).toHaveBeenLastCalledWith([]);
  });
});
