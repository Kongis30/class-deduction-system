import type { DataSummaryRow } from '@/api/data-summary';

export type SaturdayRosterSeason = 'FALL' | 'SPRING' | 'FULL_YEAR';
export type SaturdayRosterView = 'FALL' | 'SPRING' | 'ALL';

/** 优先使用报名时保存的学期代码，再从历史课程文字识别季节。 */
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

  if (/全年|full[\s-]*year|annual/.test(labels) || (isFall && isSpring)) return 'FULL_YEAR';
  if (isFall) return 'FALL';
  if (isSpring) return 'SPRING';
  return undefined;
}

/** 全年班同时属于 Fall 和 Spring；未标记季节的旧数据只保留在 All。 */
export function filterSaturdayRosterRows(rows: DataSummaryRow[], view: SaturdayRosterView): DataSummaryRow[] {
  if (view === 'ALL') return rows;
  return rows.filter((row) => {
    const season = getSaturdayRosterSeason(row);
    return season === view || season === 'FULL_YEAR';
  });
}

export function countUnclassifiedSaturdayRows(rows: DataSummaryRow[]): number {
  return rows.filter(row => getSaturdayRosterSeason(row) == null).length;
}
