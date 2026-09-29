export const DEFAULT_INITIAL_PASSWORD = '9297019872';

/**
 * 预览新账号的默认用户名；最终用户名由后端生成并处理重名情况。
 */
export function usernameFromName(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length < 2) return '';

  const firstName = normalizeNamePart(parts[0]);
  const lastName = normalizeNamePart(parts[parts.length - 1]);
  if (!firstName || !lastName) return '';

  return `${firstName}${lastName[0]}@jin`.toLowerCase();
}

function normalizeNamePart(value: string): string {
  return Array.from(value)
    .filter((character) => /[\p{L}\p{N}]/u.test(character))
    .join('');
}
