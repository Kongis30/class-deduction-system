const roundedDollarFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});

const integerFormatter = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });

/** 新订单显示整美元；旧订单若有分值则原样显示，不能再把欠款藏掉。 */
export function formatRoundedUsd(value: number | null | undefined): string {
  const amount = Number(value ?? 0);
  return Number.isInteger(Math.round(amount * 100) / 100)
    ? roundedDollarFormatter.format(amount)
    : formatExactUsd(amount);
}

export function formatRoundedAmount(value: number | null | undefined): string {
  return integerFormatter.format(Number(value ?? 0));
}

/** 金额输入及校验提示使用精确到分的格式，避免整数展示掩盖边界。 */
export function formatExactUsd(value: number | null | undefined): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value ?? 0));
}

/** 金额比较统一按分执行，避免浮点误差及整数展示影响付款上限。 */
export function exceedsMoney(amount: number, limit: number): boolean {
  return Math.round((amount + Number.EPSILON) * 100)
    > Math.round((limit + Number.EPSILON) * 100);
}
