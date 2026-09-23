/** 统一电话展示：仅格式化标准美国号码，其余号码保留原始内容。 */
export function formatUsPhoneDisplay(value?: string | null): string {
  const phone = value?.trim();
  if (!phone) return '';

  const digits = phone.replace(/\D/g, '');
  if (digits.length === 10) {
    return `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6)}`;
  }
  if (digits.length === 11 && digits.startsWith('1')) {
    return `+1 (${digits.slice(1, 4)}) ${digits.slice(4, 7)}-${digits.slice(7)}`;
  }
  return phone;
}

/** 电话筛选同时接受带格式和纯数字输入，底层原始值不变。 */
export function matchesPhoneSearch(value: string | null | undefined, query: string): boolean {
  const normalizedValue = value?.toLowerCase() ?? '';
  const normalizedQuery = query.trim().toLowerCase();
  if (!normalizedQuery) return true;
  if (normalizedValue.includes(normalizedQuery)) return true;

  const queryDigits = normalizedQuery.replace(/\D/g, '');
  return queryDigits.length > 0 && (value ?? '').replace(/\D/g, '').includes(queryDigits);
}
