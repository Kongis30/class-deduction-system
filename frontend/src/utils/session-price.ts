/** 按购买课次计算最终报价，向下取整到整美元；提交金额与界面报价共用此结果。 */
export function calculateProratedSessionPrice(fullPackagePrice: number, selectedSessions: number, totalSessions: number): number {
  const fullPriceCents = Math.round(fullPackagePrice * 100);
  return Math.floor(fullPriceCents * selectedSessions / (totalSessions * 100));
}
