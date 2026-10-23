export const CURRENCIES = [
  ['AUD', 'A$', 'A$'], ['USD', '$', 'US$'], ['CNY', '¥', 'CN¥'], ['EUR', '€', '€'], ['GBP', '£', '£'],
  ['JPY', '¥', 'JP¥'], ['NZD', 'NZ$', 'NZ$'], ['CAD', 'C$', 'C$'], ['SGD', 'S$', 'S$'], ['HKD', 'HK$', 'HK$'],
] as const;

export function money(amount: number, currency = 'AUD') {
  const symbol = CURRENCIES.find(([code]) => code === currency)?.[2] ?? currency;
  return new Intl.NumberFormat('en-AU', {
    style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).formatToParts(amount).map(part => part.type === 'currency' ? symbol : part.value).join('');
}

export function historyAmounts(expense: { amount: number; originalAmount?: number; currencyCode?: string; baseCurrency?: string }, currency: string, rate: number) {
  const originalCurrency = expense.currencyCode ?? expense.baseCurrency ?? 'AUD';
  return {
    original: money(expense.originalAmount ?? expense.amount, originalCurrency),
    converted: originalCurrency === currency ? null
      : `${currency === (expense.baseCurrency ?? 'AUD') ? '' : '≈ '}${money(expense.amount * rate, currency)}`,
  };
}
