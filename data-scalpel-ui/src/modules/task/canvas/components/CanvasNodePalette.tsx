import {
  BranchesOutlined,
  CloseOutlined,
  DatabaseOutlined,
  HolderOutlined,
  PlusOutlined,
  SaveOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Button, Empty, Input, Tooltip, Typography } from 'antd';
import {
  forwardRef,
  useEffect,
  useMemo,
  useRef,
  useState,
  type MouseEvent,
  type ReactNode,
} from 'react';
import type { CanvasNodeTemplate } from '../canvasRegistry';
import {
  CanvasNodeCategory,
  CanvasNodeType,
  type CanvasExecutionMode,
} from '../canvasTypes';

interface CategoryPresentation {
  label: string;
  icon: ReactNode;
}

const categoryOrder: readonly CanvasNodeCategory[] = [
  CanvasNodeCategory.Input,
  CanvasNodeCategory.Processor,
  CanvasNodeCategory.Output,
];

const categoryPresentation: Record<CanvasNodeCategory, CategoryPresentation> = {
  [CanvasNodeCategory.Input]: { label: '输入', icon: <DatabaseOutlined /> },
  [CanvasNodeCategory.Processor]: { label: '处理器', icon: <BranchesOutlined /> },
  [CanvasNodeCategory.Output]: { label: '输出', icon: <SaveOutlined /> },
};

const paletteNodeLabel = (
  template: CanvasNodeTemplate,
  executionMode: CanvasExecutionMode,
) => (
  executionMode === 'STREAMING' && template.type === CanvasNodeType.JdbcInput
    ? 'JDBC 静态维表'
    : template.label
);

const matchesSearch = (
  template: CanvasNodeTemplate,
  label: string,
  normalizedQuery: string,
) => {
  if (!normalizedQuery) return true;
  return [
    label,
    template.type,
    template.description,
    ...template.searchKeywords,
  ].some((candidate) => candidate.toLocaleLowerCase().includes(normalizedQuery));
};

export interface CanvasNodePaletteProps {
  templates: readonly CanvasNodeTemplate[];
  executionMode: CanvasExecutionMode;
  activeCategory: CanvasNodeCategory | null;
  inspectorDirty: boolean;
  onActiveCategoryChange: (category: CanvasNodeCategory | null) => void;
  onAddNode: (template: CanvasNodeTemplate) => void;
  onStartNodeDrag: (event: MouseEvent<HTMLElement>, template: CanvasNodeTemplate) => void;
  onBlockedDrag: () => void;
}

export const CanvasNodePalette = forwardRef<HTMLDivElement, CanvasNodePaletteProps>(({
  templates,
  executionMode,
  activeCategory,
  inspectorDirty,
  onActiveCategoryChange,
  onAddNode,
  onStartNodeDrag,
  onBlockedDrag,
}, forwardedRef) => {
  const [searchText, setSearchText] = useState('');
  const searchInputRef = useRef<React.ComponentRef<typeof Input>>(null);
  const categoryButtonRefs = useRef<Partial<Record<CanvasNodeCategory, HTMLElement>>>({});
  const previousCategoryRef = useRef<CanvasNodeCategory | null>(null);

  const availableTemplates = useMemo(
    () => templates.filter((template) => template.supportedModes.includes(executionMode)),
    [executionMode, templates],
  );
  const categoryCounts = useMemo(
    () => Object.fromEntries(categoryOrder.map((category) => [
      category,
      availableTemplates.filter((template) => template.category === category).length,
    ])) as Record<CanvasNodeCategory, number>,
    [availableTemplates],
  );
  const normalizedQuery = searchText.trim().toLocaleLowerCase();
  const visibleTemplates = useMemo(() => {
    if (!activeCategory) return [];
    return availableTemplates
      .filter((template) => template.category === activeCategory)
      .filter((template) => matchesSearch(
        template,
        paletteNodeLabel(template, executionMode),
        normalizedQuery,
      ));
  }, [activeCategory, availableTemplates, executionMode, normalizedQuery]);

  useEffect(() => {
    setSearchText('');
    if (activeCategory) {
      previousCategoryRef.current = activeCategory;
      requestAnimationFrame(() => searchInputRef.current?.focus());
      return;
    }
    const previousCategory = previousCategoryRef.current;
    if (previousCategory) {
      requestAnimationFrame(() => categoryButtonRefs.current[previousCategory]?.focus());
    }
  }, [activeCategory, executionMode]);

  useEffect(() => {
    if (!activeCategory) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      onActiveCategoryChange(null);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [activeCategory, onActiveCategoryChange]);

  const activePresentation = activeCategory ? categoryPresentation[activeCategory] : null;

  return (
    <div
      ref={forwardedRef}
      className="canvas-node-palette"
      aria-label="Canvas 节点库"
      onMouseDown={(event) => event.stopPropagation()}
      onClick={(event) => event.stopPropagation()}
    >
      <div className="canvas-node-palette-categories" role="toolbar" aria-label="节点分类">
        {categoryOrder.map((category) => {
          const presentation = categoryPresentation[category];
          const active = category === activeCategory;
          return (
            <Button
              key={category}
              ref={(element) => {
                if (element) {
                  categoryButtonRefs.current[category] = element;
                } else {
                  delete categoryButtonRefs.current[category];
                }
              }}
              type="text"
              className={`canvas-node-palette-category canvas-node-palette-category-${category.toLowerCase()}${active ? ' is-active' : ''}`}
              icon={presentation.icon}
              aria-label={`${presentation.label} ${categoryCounts[category]}`}
              aria-pressed={active}
              aria-expanded={active}
              onClick={() => onActiveCategoryChange(active ? null : category)}
            >
              <span>{presentation.label}</span>
              <span className="canvas-node-palette-count">{categoryCounts[category]}</span>
            </Button>
          );
        })}
      </div>

      {activeCategory && activePresentation && (
        <section
          className={`canvas-node-palette-panel canvas-node-palette-panel-${activeCategory.toLowerCase()}`}
          aria-label={`${activePresentation.label}节点`}
        >
          <header className="canvas-node-palette-header">
            <div className="canvas-node-palette-heading">
              <span className="canvas-node-palette-heading-icon">{activePresentation.icon}</span>
              <Typography.Text strong>{activePresentation.label}</Typography.Text>
              <Typography.Text type="secondary">{categoryCounts[activeCategory]} 个节点</Typography.Text>
            </div>
            <Button
              type="text"
              size="small"
              icon={<CloseOutlined />}
              aria-label="关闭节点库"
              onClick={() => onActiveCategoryChange(null)}
            />
          </header>
          <div className="canvas-node-palette-search">
            <Input
              ref={searchInputRef}
              allowClear
              prefix={<SearchOutlined />}
              placeholder={`搜索${activePresentation.label}节点`}
              aria-label={`搜索${activePresentation.label}节点`}
              value={searchText}
              onChange={(event) => setSearchText(event.target.value)}
            />
          </div>
          <div className="canvas-node-palette-list">
            {visibleTemplates.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="没有匹配的节点"
                className="canvas-node-palette-empty"
              />
            ) : visibleTemplates.map((template) => {
              const label = paletteNodeLabel(template, executionMode);
              return (
                <div key={template.type} className="canvas-node-palette-item">
                  <div
                    className="canvas-node-palette-drag-area"
                    role="button"
                    tabIndex={0}
                    aria-label={`拖拽${label}到画布`}
                    title="按住拖动到画布"
                    onMouseDown={(event) => {
                      if (event.button !== 0) return;
                      if (inspectorDirty) {
                        event.preventDefault();
                        onBlockedDrag();
                        return;
                      }
                      onStartNodeDrag(event, template);
                    }}
                    onKeyDown={(event) => {
                      if (event.key !== 'Enter' && event.key !== ' ') return;
                      event.preventDefault();
                      onAddNode(template);
                    }}
                  >
                    <span className="canvas-node-palette-drag-handle"><HolderOutlined /></span>
                    <span className="canvas-node-palette-item-icon">{activePresentation.icon}</span>
                    <span className="canvas-node-palette-item-content">
                      <span className="canvas-node-palette-item-title">{label}</span>
                      <span className="canvas-node-palette-item-description">{template.description}</span>
                    </span>
                  </div>
                  <Tooltip title="添加到画布中心">
                    <Button
                      type="text"
                      className="canvas-node-palette-add"
                      icon={<PlusOutlined />}
                      aria-label={`添加${label}到画布中心`}
                      onMouseDown={(event) => event.stopPropagation()}
                      onClick={() => onAddNode(template)}
                    />
                  </Tooltip>
                </div>
              );
            })}
          </div>
        </section>
      )}
    </div>
  );
});

CanvasNodePalette.displayName = 'CanvasNodePalette';
