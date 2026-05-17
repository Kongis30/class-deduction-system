export interface AppMenuItem {
  key: string;
  title: string;
  icon?: string;
  path?: string;
  children?: AppMenuItem[];
}

