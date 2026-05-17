import type { ButtonType } from 'element-plus';

export type WorkspaceTagTone = 'primary' | 'success' | 'warning' | 'danger' | 'info';

export interface WorkspaceHeroData {
  eyebrow: string;
  title: string;
  description: string;
  badge: string;
}

export interface WorkspaceMetricItem {
  label: string;
  value: string;
  hint: string;
  tone?: WorkspaceTagTone;
}

export interface WorkspaceFilterOption {
  label: string;
  value: string;
}

export interface WorkspaceFilterField {
  key: string;
  label: string;
  type: 'input' | 'select' | 'date-range';
  placeholder: string;
  options?: WorkspaceFilterOption[];
}

export interface WorkspaceAction {
  label: string;
  buttonType?: ButtonType;
  plain?: boolean;
}

export interface WorkspaceTagCell {
  kind: 'tag';
  text: string;
  tone?: WorkspaceTagTone;
}

export interface WorkspaceStackCell {
  kind: 'stack';
  primary: string;
  secondary?: string;
}

export interface WorkspaceTagListCell {
  kind: 'tag-list';
  items: WorkspaceTagCell[];
}

export type WorkspaceCellValue =
  | string
  | number
  | WorkspaceTagCell
  | WorkspaceStackCell
  | WorkspaceTagListCell;

export interface WorkspaceTableColumn {
  key: string;
  label: string;
  minWidth?: number;
  width?: number;
  align?: 'left' | 'center' | 'right';
}

export interface WorkspaceTableRow {
  id: string;
  [key: string]: WorkspaceCellValue | string | number | undefined;
}

export interface WorkspaceInfoItem {
  label: string;
  value: string;
  tone?: WorkspaceTagTone;
}

export interface WorkspaceInfoBlock {
  title: string;
  description?: string;
  items: WorkspaceInfoItem[];
}

export interface ListWorkspaceSchema {
  /** 表格上方的简洁标题 */
  tableTitle: string;
  /** 筛选条件配置 */
  filters: WorkspaceFilterField[];
  /** 表格列定义 */
  columns: WorkspaceTableColumn[];
  /** 表格数据行 */
  rows: WorkspaceTableRow[];
  /** 右上角操作按钮 */
  actions?: WorkspaceAction[];
  /** 顶部大标题（已废弃，统一用 tableTitle） */
  hero?: WorkspaceHeroData;
  /** 顶部指标卡片（已废弃，统一用 tableTitle） */
  metrics?: WorkspaceMetricItem[];
  /** 表格上方描述（已废弃，统一用 tableTitle） */
  tableDescription?: string;
  /** 右侧信息块（已废弃） */
  asideBlocks?: WorkspaceInfoBlock[];
}

export interface WorkspaceFormField {
  key: string;
  label: string;
  type: 'input' | 'select' | 'textarea' | 'date' | 'number';
  placeholder: string;
  value?: string;
  span?: number;
  options?: WorkspaceFilterOption[];
}

export interface WorkspaceFormSection {
  title: string;
  description: string;
  fields: WorkspaceFormField[];
}

export interface FormWorkspaceSchema {
  hero: WorkspaceHeroData;
  metrics: WorkspaceMetricItem[];
  sections: WorkspaceFormSection[];
  summaryBlocks: WorkspaceInfoBlock[];
  footerActions: WorkspaceAction[];
  nextSteps: string[];
}

export interface ScheduleBoardCard {
  time: string;
  title: string;
  subtitle: string;
  status: WorkspaceTagCell;
  details: string[];
}

export interface ScheduleBoardColumn {
  title: string;
  subtitle: string;
  cards: ScheduleBoardCard[];
}

export interface ScheduleWorkspaceSchema {
  tableTitle: string;
  filters: WorkspaceFilterField[];
  actions: WorkspaceAction[];
  boardColumns: ScheduleBoardColumn[];
  legend: WorkspaceTagCell[];
  /** @deprecated no longer rendered, kept for backward compatibility */
  hero?: WorkspaceHeroData;
  /** @deprecated no longer rendered, kept for backward compatibility */
  metrics?: WorkspaceMetricItem[];
  /** @deprecated no longer rendered, kept for backward compatibility */
  setupBlocks?: WorkspaceInfoBlock[];
  /** @deprecated no longer rendered, kept for backward compatibility */
  alerts?: WorkspaceInfoBlock[];
}

export interface WorkspaceSettingField {
  label: string;
  value: string;
  hint?: string;
  tone?: WorkspaceTagTone;
}

export interface WorkspaceSettingSection {
  title: string;
  description: string;
  fields: WorkspaceSettingField[];
}

export interface SettingsWorkspaceSchema {
  tableTitle: string;
  sections: WorkspaceSettingSection[];
  actions: WorkspaceAction[];
  /** @deprecated no longer rendered, kept for backward compatibility */
  hero?: WorkspaceHeroData;
  /** @deprecated no longer rendered, kept for backward compatibility */
  metrics?: WorkspaceMetricItem[];
  /** @deprecated no longer rendered, kept for backward compatibility */
  sideBlocks?: WorkspaceInfoBlock[];
}
