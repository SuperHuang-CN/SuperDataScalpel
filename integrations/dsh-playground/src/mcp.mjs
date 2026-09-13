import { randomUUID } from 'node:crypto';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { equal, owners } from './auth.mjs';
export function identityHandler(tokens, readJson, json) {
  return async (req, res) => {
    const owner = owners.find(owner => equal(req.headers.authorization, `Bearer ${tokens[owner]}`));
    if (!owner) return json(res, 401, { code: 'MCP_UNAUTHORIZED', detail: '测试 MCP 凭据无效。' });
    if (req.url !== '/mcp') return json(res, 404, { code: 'NOT_FOUND' });
    const server = new McpServer({ name: 'dsh-playground-identity', version: '0.1.0' });
    server.registerTool('whoami', { description: 'Return the authenticated caller identity from this MCP server. Takes no user ID or credentials.', inputSchema: {}, annotations: { readOnlyHint: true } }, async () => ({
      content: [{ type: 'text', text: JSON.stringify({ authenticatedUser: owner, requestId: randomUUID(), source: 'independent-test-mcp' }) }],
    }));
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
    res.once('close', () => { void transport.close(); void server.close(); });
    try { await server.connect(transport); await transport.handleRequest(req, res, req.method === 'POST' ? await readJson(req) : undefined); }
    catch { if (!res.headersSent) json(res, 400, { code: 'MCP_REQUEST_INVALID' }); else res.end(); }
  };
}
