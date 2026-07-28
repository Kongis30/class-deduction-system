export interface AppMenuItem {
  key: string;
  title: string;
  description?: string;
  icon?: string;
  path?: string;
  children?: AppMenuItem[];
}
