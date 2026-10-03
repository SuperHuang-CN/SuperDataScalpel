import { test, expect } from '@playwright/test';

test.describe('homepage against the local development environment', () => {
  test.beforeEach(async ({ page, request }) => {
    const login = await request.post('/api/v1/auth/login', { data: {
      username: process.env.DATASCALPEL_TEST_USERNAME ?? 'admin',
      password: process.env.DATASCALPEL_TEST_PASSWORD ?? 'admin123456',
    } });
    expect(login.ok()).toBeTruthy();
    const { accessToken } = await login.json();
    await page.addInitScript(token => sessionStorage.setItem('data-scalpel.access-token', token), accessToken);
  });

  test('loads real statistics, preserves invariants and opens matching quality details', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    const responses = new Map<string, Record<string, unknown>>();
    page.on('response', async response => {
      if (response.url().includes('/statistics') && response.ok()) responses.set(new URL(response.url()).pathname, await response.json());
    });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('/');
    await expect(page.getByRole('heading', { name: '数据工作台', exact: true })).toBeVisible();
    for (const label of ['模型建设','任务生产','服务交付','生产运行','需要处理','数据质量','服务使用']) {
      await expect(page.getByRole('region', { name: label, exact: true })).toBeVisible();
    }
    await expect(page.locator('.home-update')).toHaveCount(7);
    await expect(page.getByText(/刷新失败|加载失败/)).toHaveCount(0);
    expect(errors).toEqual([]);
    const models = responses.get('/api/v1/models/statistics');
    const quality = responses.get('/api/v1/model-quality/statistics');
    expect(models).toBeDefined(); expect(quality).toBeDefined();
    expect(Number(quality!.passed)+Number(quality!.failed)+Number(quality!.noResult)).toBe(quality!.total);
    expect(quality!.total).toBe(models!.published);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
    expect(overflow).toBe(false);
    await page.screenshot({ path: 'test-results/homepage-desktop.png', fullPage: true });
    await page.getByRole('navigation', { name: '资源入口' }).scrollIntoViewIfNeeded();
    await page.screenshot({ path: 'test-results/homepage-desktop-lower.png', fullPage: true });
    const details = page.waitForResponse(response => response.url().includes('/model-quality/models'));
    await page.getByRole('button', { name: /查看模型质量/ }).click();
    expect((await details).ok()).toBeTruthy();
    await expect(page.getByText('模型质量 · 全部已发布模型')).toBeVisible();
    await expect(page.getByText('质量明细加载失败')).toHaveCount(0);
    await page.keyboard.press('Escape');
    await page.getByText('近7天', { exact: true }).click();
    await expect(page.getByRole('region', { name: '生产运行', exact: true }).locator('.home-update')).toBeVisible();
    await page.setViewportSize({ width: 900, height: 1000 });
    await page.screenshot({ path: 'test-results/homepage-narrow.png', fullPage: true });
    await page.getByRole('navigation', { name: '资源入口' }).scrollIntoViewIfNeeded();
    await page.screenshot({ path: 'test-results/homepage-narrow-lower.png', fullPage: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
    await page.getByRole('region', { name: '模型建设', exact: true }).getByRole('link', { name: /查看未分层/ }).click();
    await expect(page).toHaveURL(/status=PUBLISHED.*layer=unassigned/);
  });

  test('keeps other panels usable when one statistics endpoint fails', async ({ page }) => {
    await page.route('**/api/v1/models/statistics', route => route.fulfill({ status: 503, contentType: 'application/problem+json', body: JSON.stringify({ status: 503, detail: '统计临时不可用', code: 'UNAVAILABLE' }) }));
    await page.goto('/');
    await expect(page.getByText('模型建设刷新失败')).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole('region', { name: '服务交付', exact: true }).locator('.home-primary')).toBeVisible();
    await expect(page.getByRole('region', { name: '模型建设', exact: true }).locator('.home-primary')).toHaveCount(0);
    await page.unroute('**/api/v1/models/statistics');
    await page.getByRole('region', { name: '模型建设', exact: true }).getByRole('button', { name: '重试', exact: true }).click();
    await expect(page.getByRole('region', { name: '模型建设', exact: true }).locator('.home-primary')).toBeVisible();
  });
});
