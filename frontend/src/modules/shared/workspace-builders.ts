import type {
  WorkspaceHeroData,
  WorkspaceInfoBlock,
  WorkspaceMetricItem,
  WorkspaceStackCell,
  WorkspaceTagCell,
  WorkspaceTagListCell,
  WorkspaceTagTone,
} from './workspace-types';

// Helper builders keep mock schema files compact and consistent across modules.
export function createHero(
  eyebrow: string,
  title: string,
  description: string,
  badge: string,
): WorkspaceHeroData {
  return {
    eyebrow,
    title,
    description,
    badge,
  };
}

export function createMetric(
  label: string,
  value: string,
  hint: string,
  tone: WorkspaceTagTone = 'primary',
): WorkspaceMetricItem {
  return {
    label,
    value,
    hint,
    tone,
  };
}

export function createTag(text: string, tone: WorkspaceTagTone = 'info'): WorkspaceTagCell {
  return {
    kind: 'tag',
    text,
    tone,
  };
}

export function createStack(primary: string, secondary?: string): WorkspaceStackCell {
  return {
    kind: 'stack',
    primary,
    secondary,
  };
}

export function createTagList(items: WorkspaceTagCell[]): WorkspaceTagListCell {
  return {
    kind: 'tag-list',
    items,
  };
}

export function createInfoBlock(
  title: string,
  description: string | undefined,
  items: WorkspaceInfoBlock['items'],
): WorkspaceInfoBlock {
  return {
    title,
    description,
    items,
  };
}
