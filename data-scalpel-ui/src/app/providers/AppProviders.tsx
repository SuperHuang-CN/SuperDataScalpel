import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CheckCircleOutlined, CloseCircleOutlined, InfoCircleOutlined, WarningOutlined } from '@ant-design/icons';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { PropsWithChildren } from 'react';
import { workspaceResourceTheme, workspaceStatusTagStyles } from '../../shared/theme/workspaceResourceTheme';

const feedbackIcons = {
  successIcon: <CheckCircleOutlined style={{ color: '#268467' }} />,
  errorIcon: <CloseCircleOutlined style={{ color: '#c04e5b' }} />,
  warningIcon: <WarningOutlined style={{ color: '#a97022' }} />,
  infoIcon: <InfoCircleOutlined style={{ color: '#287f9e' }} />,
};

// Static message/notification/modal calls mount outside the application provider.
ConfigProvider.config({
  holderRender: (children) => (
    <ConfigProvider locale={zhCN} theme={workspaceResourceTheme} modal={feedbackIcons} alert={feedbackIcons} tag={{ styles: workspaceStatusTagStyles }}>
      {children}
    </ConfigProvider>
  ),
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export const AppProviders = ({ children }: PropsWithChildren) => (
  <ConfigProvider
    locale={zhCN}
    modal={feedbackIcons}
    alert={feedbackIcons}
    tag={{ styles: workspaceStatusTagStyles }}
    componentSize="small"
    theme={{
      token: {
        ...workspaceResourceTheme.token,
        controlHeightSM: 24,
      },
      components: {
        ...workspaceResourceTheme.components,
        Card: {
          bodyPadding: 12,
          bodyPaddingSM: 12,
        },
        Menu: {
          darkItemBg: 'transparent',
          darkSubMenuItemBg: 'rgba(4, 18, 61, 0.22)',
          darkItemColor: 'rgba(235, 241, 255, 0.76)',
          darkItemHoverBg: 'rgba(255, 255, 255, 0.10)',
          darkItemSelectedBg: 'rgba(255, 255, 255, 0.17)',
          darkItemSelectedColor: '#FFFFFF',
          itemHeight: 38,
          itemMarginBlock: 3,
          itemBorderRadius: 4,
        },
        Table: {
          ...workspaceResourceTheme.components?.Table,
          cellPaddingBlockSM: 6,
          cellPaddingInlineSM: 10,
          rowHoverBg: '#f0f7fa',
          rowSelectedBg: '#dceef5',
          rowSelectedHoverBg: '#cde5f0',
          bodySortBg: '#f7fafc',
          borderColor: '#e1ebf0',
        },
      },
    }}
  >
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  </ConfigProvider>
);
