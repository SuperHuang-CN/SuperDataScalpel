import test from 'node:test';
import assert from 'node:assert/strict';
import { Context } from '@deepseek-ai/cordis';
import { createScope, scopeTarget } from '@deepseek-ai/dsh-scope';

test('Bridge question answerer precedes native UI only for its own Agent scope', async () => {
  const ctx = new Context(), bridgeAgent = {}, nativeAgent = {};
  const scope = createScope(ctx, bridgeAgent);
  const native = ctx.on('user-questions/request', async () => 'native');
  scope.ctx.on('user-questions/request', async (request, next) => request.agent === bridgeAgent ? 'bridge' : next(), { prepend: true });
  assert.equal(await ctx.waterfall(scopeTarget(bridgeAgent, bridgeAgent), 'user-questions/request', { agent: bridgeAgent }, async () => 'none'), 'bridge');
  assert.equal(await ctx.waterfall(scopeTarget(nativeAgent, nativeAgent), 'user-questions/request', { agent: nativeAgent }, async () => 'none'), 'native');
  await scope.dispose();
  assert.equal(await ctx.waterfall(scopeTarget(bridgeAgent, bridgeAgent), 'user-questions/request', { agent: bridgeAgent }, async () => 'none'), 'native');
  native();
});
