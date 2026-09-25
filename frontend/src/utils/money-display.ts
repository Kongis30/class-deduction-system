const roundedDollarFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});

const integerFormatter = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });

/** 仅处理展示；金额计算和提交仍使用原始分值。 */
export function formatRoundedUsd(value: number | null | undefined): string {
  return roundedDollarFormatter.format(Number(value ?? 0));
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
