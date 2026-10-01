import type { DataSummaryRow } from '@/api/data-summary';

export type SaturdayRosterSeason = 'FALL' | 'SPRING' | 'FULL_YEAR';
export type SaturdayRosterView = 'FALL' | 'SPRING' | 'ALL';

/** 根据报名项已存的课程/周期文字分类，不按日期猜季节。 */
export function getSaturdayRosterSeason(row: DataSummaryRow): SaturdayRosterSeason | undefined {
  const termCode = row.registrationTermCode?.trim().toUpperCase();
  if (termCode === 'FALL') return 'FALL';
  if (termCode === 'SPRING') return 'SPRING';
  if (termCode === 'FULL_YEAR') return 'FULL_YEAR';

  const labels = [row.gradeBand, row.periodLabel, row.className, row.registerClass, row.detail, row.courseProductName]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
  const isFall = /秋季|\bfall\b|\bautumn\b/.test(labels);
  const isSpring = /春季|\bspring\b/.test(labels);

  if (/全年|full[\s-]*year|annual/.test(labels) || (isFall && isSpring)) {
    return 'FULL_YEAR';
  }
  if (isFall) return 'FALL';
  if (isSpring) return 'SPRING';
  return undefined;
}

/** 全年班会同时匹配两个季节视图；全部人员视图始终保留原始报名行且不复制。 */
export function filterSaturdayRosterRows(rows: DataSummaryRow[], view: SaturdayRosterView): DataSummaryRow[] {
  if (view === 'ALL') return rows;
  return rows.filter((row) => {
    const season = getSaturdayRosterSeason(row);
    return season === view || season === 'FULL_YEAR';
  });
}

/** 未标出季节的记录只在全部人员视图中显示，供页面提示管理人员补齐课程标签。 */
export function countUnclassifiedSaturdayRows(rows: DataSummaryRow[]): number {
  return rows.filter(row => getSaturdayRosterSeason(row) == null).length;
}
