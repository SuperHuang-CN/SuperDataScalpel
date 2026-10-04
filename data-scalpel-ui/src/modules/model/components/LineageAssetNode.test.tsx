import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { Node } from '@antv/x6';
import type { ComponentType } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import workspaceStyles from './lineage-workspace.css?raw';
import { LINEAGE_ASSET_SIZE } from '../model/lineageAppearance';

const registry = vi.hoisted(() => ({ component: undefined as ComponentType<{ node: Node }> | undefined }));
vi.mock('@antv/x6-react-shape', () => ({ register: (entry: { component: ComponentType<{ node: Node }> }) => { registry.component = entry.component; } }));
import { registerLineageAssetNode } from './LineageAssetNode';

afterEach(() => { cleanup(); document.body.replaceChildren(); });

describe('lineage HTML node size isolation', () => {
  it('isolates the embedded XHTML body from page height and renders a bounded clickable node', () => {
    const styles = document.createElement('style');
    styles.textContent = `html, body, #root { min-height: 100vh; }\n${workspaceStyles}`;
    document.body.append(styles);
    const workspace = document.createElement('div');
    workspace.className = 'lineage-resource-workspace';
    // Match X6 React-shape markup: SVG > g.x6-node > foreignObject > body > div.
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    const group = document.createElementNS(svg.namespaceURI, 'g');
    group.setAttribute('class', 'x6-node');
    const foreign = document.createElementNS(svg.namespaceURI, 'foreignObject');
    foreign.setAttribute('width', String(LINEAGE_ASSET_SIZE.width));
    foreign.setAttribute('height', String(LINEAGE_ASSET_SIZE.height));
    const embeddedBody = document.createElementNS('http://www.w3.org/1999/xhtml', 'body');
    const content = document.createElement('div');
    embeddedBody.append(content); foreign.append(embeddedBody); group.append(foreign); svg.append(group); workspace.append(svg); document.body.append(workspace);
    const onSelect = vi.fn();
    const data = { id: 'meter', kind: 'MODEL', current: true, stale: false, label: 'meter_readings', subtitle: 'meter_readings', onSelect };
    const node = { getData: () => data, getSize: () => LINEAGE_ASSET_SIZE } as unknown as Node;
    registerLineageAssetNode();
    const View = registry.component!;
    render(<View node={node} />, { container: content });
    const button = screen.getByRole('button', { name: '查看数据模型：meter_readings' });
    expect(getComputedStyle(document.body).minHeight).toBe('100vh');
    expect(getComputedStyle(embeddedBody).minHeight).toBe('0');
    expect(getComputedStyle(embeddedBody).overflow).toBe('hidden');
    expect(button).toHaveStyle({ width: '272px', height: '92px' });
    expect(screen.getAllByText('数据模型')).toHaveLength(1);
    fireEvent.click(button);
    expect(onSelect).toHaveBeenCalledWith(data);
  });
});
