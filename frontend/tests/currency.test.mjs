import { test } from 'node:test';
import assert from 'node:assert/strict';
import { historyAmounts, money, CURRENCIES } from '../src/utils/currency.ts';

test('history preserves original value, stable base value, and optional approximate display conversion', () => {
  const expense = Object.freeze({ originalAmount: 100, currencyCode: 'USD', baseCurrency: 'AUD', amount: 149 });
  assert.deepEqual(historyAmounts(expense, 'AUD', 1), { original: money(100, 'USD'), converted: money(149, 'AUD') });
  assert.equal(historyAmounts(expense, 'USD', 0.7).converted, null);
  assert.equal(historyAmounts(expense, 'EUR', 0.6).converted, `≈ ${money(89.4, 'EUR')}`);
  assert.equal(expense.amount, 149);
  assert.equal(historyAmounts({ amount: 40 }, 'AUD', 1).converted, null);
  assert.equal(CURRENCIES.length, 10);
});
