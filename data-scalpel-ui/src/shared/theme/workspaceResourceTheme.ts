import type { TagProps, ThemeConfig } from 'antd';
import './workspace-resource.css';

export const workspaceFontFamily = '"Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Segoe UI", sans-serif';

/** Resource operations share the lake-blue workspace typography and controls. */
export const workspaceResourceTheme: ThemeConfig = {
  token: {
    fontFamily: workspaceFontFamily,
    fontFamilyCode: workspaceFontFamily,
    colorPrimary: '#287f9e',
    colorLink: '#287f9e',
    colorLinkHover: '#1e607b',
    colorLinkActive: '#174e65',
    colorText: '#263d4c',
    colorTextSecondary: '#5b7180',
    colorTextDisabled: '#71808c',
    colorBgContainerDisabled: '#edf1f4',
    colorBorder: '#d5e4eb',
    colorTextPlaceholder: '#687f8e',
    // Explicit semantic surfaces prevent muted seed colors from generating
    // muddy backgrounds for status tags across lists, details and overlays.
    colorSuccess: '#268467',
    colorSuccessBg: '#eaf7f1',
    colorSuccessBgHover: '#dcefe5',
    colorSuccessBorder: '#bfe1d2',
    colorSuccessBorderHover: '#9bcdb7',
    colorSuccessText: '#18755f',
    colorError: '#c04e5b',
    colorErrorBg: '#fff0f2',
    colorErrorBgHover: '#ffe3e8',
    colorErrorBorder: '#f1c7ce',
    colorErrorBorderHover: '#e5a4b0',
    colorErrorText: '#b6384b',
    colorWarning: '#a97022',
    colorWarningBg: '#fff7e8',
    colorWarningBgHover: '#ffedcc',
    colorWarningBorder: '#ecd4a8',
    colorWarningBorderHover: '#dfbd7b',
    colorWarningText: '#8c5c19',
    colorInfo: '#287f9e',
    colorInfoBg: '#edf6fb',
    colorInfoBgHover: '#dfeef7',
    colorInfoBorder: '#bfdbe9',
    colorInfoBorderHover: '#9cc6db',
    colorInfoText: '#216c88',
    fontSize: 14,
    controlHeight: 36,
    borderRadius: 4,
    borderRadiusXS: 2,
    borderRadiusSM: 3,
    borderRadiusLG: 6,
  },
  components: {
    Table: {
      headerBg: 'var(--ds-table-header-background)',
      headerColor: 'var(--ds-table-header-color)',
      headerSplitColor: 'var(--ds-table-header-border)',
      headerSortActiveBg: 'var(--ds-table-header-active-background)',
      headerSortHoverBg: 'var(--ds-table-header-active-background)',
      fixedHeaderSortActiveBg: 'var(--ds-table-header-active-background)',
    },
    Message: { contentBg: '#fff', contentPadding: '10px 14px' },
    // Tag uses the semantic base color for text, rather than color*Text.
    Tag: {
      colorSuccess: '#18755f',
      colorError: '#b6384b',
      colorWarning: '#8c5c19',
      colorInfo: '#216c88',
    },
  },
};

/** Ant Design 6 filled tags omit their border by default. Use the same explicit
 * semantic tokens for the surface, text and outline in every rendering root. */
export const workspaceStatusTagStyles = ({ props }: { props: TagProps }) => {
  const token = workspaceResourceTheme.token!;
  const colors = props.color === 'success' ? [token.colorSuccessBg, token.colorSuccessText, token.colorSuccessBorder]
    : props.color === 'error' ? [token.colorErrorBg, token.colorErrorText, token.colorErrorBorder]
      : props.color === 'warning' ? [token.colorWarningBg, token.colorWarningText, token.colorWarningBorder]
        : props.color === 'processing' ? [token.colorInfoBg, token.colorInfoText, token.colorInfoBorder] : undefined;
  return colors ? { root: { background: colors[0], color: colors[1], border: `1px solid ${colors[2]}` } } : {};
};
