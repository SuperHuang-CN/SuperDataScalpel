import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SdkApiDrawer } from './SdkApiDrawer';
import { getSdkApiDocumentation } from '../api/sdkApi';
import type { SdkApiDocumentation, SdkApiType } from '../model/sdkApi';

vi.mock('../api/sdkApi', () => ({ getSdkApiDocumentation: vi.fn() }));
const api = vi.mocked(getSdkApiDocumentation);
const resource = (name: string, mode: SdkApiType['mode'] = 'BOTH'): SdkApiType => ({ name, simpleName: name, mode,
  group: name === 'OnlyStreaming' ? '流式用途' : '新增用途', summary: `用于${name}`, note: '写入立即生效', parents: [], example: `context.${name}();`,
  members: [{ name: 'fetchNew', signature: 'fetchNew(String binding)', summary: `读取${name}资源`, returnType: 'Rows',
    returns: '读取结果', parameters: [{ name: 'binding', type: 'String', description: '任务中配置的引用名' }], example: 'var rows = context.fetchNew("source");', note: '', deprecated: false }],
});
const payload = (name = 'NewApi'): SdkApiDocumentation => ({ version: 'test', fingerprint: '123456789abcdef', types: [resource(name), resource('OnlyStreaming', 'STREAMING')] });
const clients: QueryClient[] = [];
function show(mode: 'BATCH' | 'STREAMING' = 'BATCH') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  clients.push(client);
  return render(<QueryClientProvider client={client}><SdkApiDrawer mode={mode} onClose={vi.fn()} /></QueryClientProvider>);
}
beforeEach(() => api.mockResolvedValue(payload()));
afterEach(() => { cleanup(); clients.splice(0).forEach(c => c.clear()); vi.resetAllMocks(); });

describe('SDK usage guide', () => {
  it('starts with capabilities, then shows an operation example and parameters in one click', async () => {
    show();
    expect(await screen.findByRole('heading', { name: '用 SDK 能做什么？' })).toBeVisible();
    expect(screen.queryByText('OnlyStreaming')).not.toBeInTheDocument();
    expect(screen.queryByText('任务中配置的引用名')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /读取NewApi资源/ }));
    expect(screen.getByRole('heading', { name: '读取NewApi资源' })).toBeVisible();
    expect(screen.getByText('var rows = context.fetchNew("source");')).toBeVisible();
    expect(screen.queryByText('Rows fetchNew(String binding)')).not.toBeInTheDocument();
    await userEvent.click(screen.getByText('方法定义与参数（参考）'));
    expect(screen.getByText('下面是接口定义，不是可直接粘贴运行的调用代码。')).toBeVisible();
    expect(screen.getByText('任务中配置的引用名')).toBeVisible();
    expect(screen.getByText('Rows fetchNew(String binding)')).toBeVisible();
    expect(screen.getByText('写入立即生效')).toBeVisible();
  });
  it('searches all capabilities by methods and parameters and supports clearing', async () => {
    show();
    await screen.findByRole('heading', { name: '用 SDK 能做什么？' });
    const nav = screen.getByRole('navigation', { name: 'SDK 用途' });
    await userEvent.click(within(nav).getByRole('button', { name: /新增用途/ }));
    const input = screen.getByRole('textbox', { name: '搜索 SDK API' });
    await userEvent.type(input, 'FETCHNEW 引用名');
    expect(screen.getByRole('button', { name: /读取NewApi资源/ })).toBeVisible();
    await userEvent.type(input, '不存在');
    expect(screen.getByText('没有匹配的 API，试试其他关键词')).toBeVisible();
    await userEvent.clear(input);
    expect(screen.getByRole('heading', { name: '用 SDK 能做什么？' })).toBeVisible();
  });
  it('refreshes new groups and methods and clears a removed selection without an allowlist', async () => {
    show();
    await userEvent.click(await screen.findByRole('button', { name: /读取NewApi资源/ }));
    const next = payload('NextApi'); next.types[0].group = '新部署用途';
    api.mockResolvedValue({ ...next, fingerprint: 'new-version-hash' });
    await userEvent.click(screen.getByRole('button', { name: /刷新/ }));
    expect(await screen.findByRole('button', { name: /读取NextApi资源/ })).toBeVisible();
    expect(screen.queryByRole('heading', { name: '读取NewApi资源' })).not.toBeInTheDocument();
    expect(screen.getByText('SDK test · new-vers')).toBeVisible();
  });
  it('shows errors and retry without presenting stale docs as current', async () => {
    show();
    await screen.findByRole('heading', { name: '用 SDK 能做什么？' });
    api.mockRejectedValueOnce(new Error('引擎暂不可用'));
    await userEvent.click(screen.getByRole('button', { name: /刷新/ }));
    expect(await screen.findByText('SDK 文档读取失败')).toBeVisible();
    expect(screen.queryByRole('heading', { name: '用 SDK 能做什么？' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    expect(await screen.findByRole('heading', { name: '用 SDK 能做什么？' })).toBeVisible();
  });
  it('preserves streaming and inherited type capabilities', async () => {
    const data = payload(); data.types[1].parents = ['NewApi']; api.mockResolvedValue(data);
    show('STREAMING');
    const nav = await screen.findByRole('navigation', { name: 'SDK 用途' });
    await userEvent.click(within(nav).getByRole('button', { name: /流式用途/ }));
    await userEvent.click(screen.getByRole('button', { name: 'OnlyStreaming' }));
    await userEvent.click(screen.getByRole('button', { name: '另含 NewApi 的通用能力' }));
    expect(screen.getByRole('heading', { name: '用于NewApi' })).toBeVisible();
  });
  it('keeps related configuration types accessible even without examples', async () => {
    const data = payload(); const configuration = resource('Rows'); configuration.example = ''; configuration.group = '配置';
    data.types.push(configuration); api.mockResolvedValue(data); show();
    await userEvent.click(await screen.findByRole('button', { name: /读取NewApi资源/ }));
    await userEvent.click(screen.getByRole('button', { name: /用于Rows/ }));
    expect(screen.getByRole('heading', { name: '用于Rows' })).toBeVisible();
    expect(screen.getByRole('button', { name: /读取Rows资源/ })).toBeVisible();
  });
});
