/** 按购买课次计算新报名最终报价：有零头即向上取整到整美元。 */
export function calculateProratedSessionPrice(fullPackagePrice: number, selectedSessions: number, totalSessions: number): number {
  const fullPriceCents = Math.round(fullPackagePrice * 100);
  return Math.ceil(fullPriceCents * selectedSessions / (totalSessions * 100));
}
