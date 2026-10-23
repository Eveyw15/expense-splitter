import assert from 'node:assert/strict';
import test from 'node:test';
import { spendingByCategory } from '../src/utils/expenseAnalytics.ts';
import { EXPENSE_CATEGORIES } from '../src/utils/expenseHistory.ts';

test('category totals and percentages use all records and preserve exact cents', () => {
  const records = [{ category: 'Food', amount: 0.1 }, { category: 'Food', amount: 0.2 }, { category: 'Transport', amount: 0.7 }];
  const rows = spendingByCategory(records);
  assert.deepEqual(rows.find(row => row.category === 'Food'), { category: 'Food', amount: 0.3, percentage: 30 });
  assert.deepEqual(rows.find(row => row.category === 'Transport'), { category: 'Transport', amount: 0.7, percentage: 70 });
  assert.equal(rows.reduce((sum, row) => sum + Math.round(row.amount * 100), 0), 100);
  assert.equal(records.length, 3);
  assert.equal(spendingByCategory([{ category: 'Food', amount: 99999999.99 }])[0].amount, 99999999.99);
});

test('zero expenses, zero totals and unused categories never produce NaN or Infinity', () => {
  for (const records of [[], [{ category: 'Food', amount: 0 }]]) {
    const rows = spendingByCategory(records);
    assert.deepEqual(rows.map(row => row.category), EXPENSE_CATEGORIES);
    assert.ok(rows.every(row => row.amount === 0 && row.percentage === 0));
  }
});

test('unknown categories retain their amounts; missing and blank categories group as Uncategorised', () => {
  const rows = spendingByCategory([{ category: 'Local fare', amount: 10 }, { category: null, amount: 15 }, { category: ' ', amount: 5 }]);
  assert.equal(rows.find(row => row.category === 'Local fare').amount, 10);
  assert.equal(rows.find(row => row.category === 'Uncategorised').amount, 20);
  assert.equal(rows.reduce((sum, row) => sum + row.amount, 0), 30);
  assert.ok(rows.every(row => Number.isFinite(row.percentage)));
});

test('fresh derivation reflects create, amount/category edits, deletion and new trip data', () => {
  const records = [{ category: 'Food', amount: 10 }];
  const total = (input, category) => spendingByCategory(input).find(row => row.category === category).amount;
  assert.equal(total([...records, { category: 'Food', amount: 2.35 }], 'Food'), 12.35);
  assert.equal(total([{ ...records[0], amount: 20 }], 'Food'), 20);
  assert.equal(total([{ ...records[0], category: 'Transport' }], 'Food'), 0);
  assert.equal(total([{ ...records[0], category: 'Transport' }], 'Transport'), 10);
  assert.equal(total([], 'Food'), 0);
  assert.equal(total([{ category: 'Lodging', amount: 250 }], 'Food'), 0);
  assert.deepEqual(records, [{ category: 'Food', amount: 10 }]);
});
