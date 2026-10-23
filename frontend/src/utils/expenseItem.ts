// UI preview only. Decimal strings are sent to the backend for authoritative validation.
export function cents(value: string): number | null {
  if (!/^\d{1,8}(\.\d{1,2})?$/.test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
}

export function itemLineCents(amount: string, quantity: string): number | null {
  const unit = cents(amount);
  const count = quantity === '' ? 1 : Number(quantity);
  const total = unit === null ? null : unit * count;
  return Number.isSafeInteger(count) && count > 0 && total !== null && total <= 9_999_999_999 ? total : null;
}
