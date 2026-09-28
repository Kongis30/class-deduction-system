/** 按购买课次计算最终报价：向下取整到整美元，正价课包最低收 $1。 */
export function calculateProratedSessionPrice(fullPackagePrice: number, selectedSessions: number, totalSessions: number): number {
  const fullPriceCents = Math.round(fullPackagePrice * 100);
  const wholeDollars = Math.floor(fullPriceCents * selectedSessions / (totalSessions * 100));
  return fullPriceCents > 0 ? Math.max(1, wholeDollars) : 0;
}
