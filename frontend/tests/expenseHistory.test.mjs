import assert from 'node:assert/strict';
import test from 'node:test';
import { EMPTY_EXPENSE_FILTERS, filterExpenses } from '../src/utils/expenseHistory.ts';

const expenses = [
  { expenseId: 1, description: 'Dinner at Quay', payerId: 10, payerName: 'Amy', category: 'Food', date: '2026-09-01' },
  { expenseId: 2, description: 'Dinner with friends', payerId: 20, payerName: 'Ben', category: 'Food', date: '2026-09-15' },
  { expenseId: 3, description: 'Airport train', payerId: 10, payerName: 'Amy', category: 'Transport', date: '2026-08-31' },
  { expenseId: 4, description: 'Museum', payerId: 20, payerName: 'Ben', category: 'Culture', date: '2026-09-16' },
  { expenseId: 5, description: 'Miscellaneous', payerId: 10, payerName: 'Amy', category: null, date: '2026-09-15', activity: 'secret metadata', createdByName: 'Hidden creator' },
];
const ids = filters => filterExpenses(expenses, { ...EMPTY_EXPENSE_FILTERS, ...filters }).map(e => e.expenseId);

test('search matches only description, payer and category, ignoring case and surrounding whitespace', () => {
  assert.deepEqual(ids({ query: ' DINNER ' }), [1, 2]);
  assert.deepEqual(ids({ query: 'aMy' }), [1, 3, 5]);
  assert.deepEqual(ids({ query: 'fooD' }), [1, 2]);
  assert.deepEqual(ids({ query: 'secret metadata' }), []);
  assert.deepEqual(ids({ query: 'Hidden creator' }), []);
  assert.deepEqual(ids({ query: '2026' }), []);
  assert.deepEqual(ids({ query: '10' }), []);
});

test('category and payer use exact selection; missing categories are safe', () => {
  assert.deepEqual(ids({ category: 'Food' }), [1, 2]);
  assert.deepEqual(ids({ payer: '10' }), [1, 3, 5]);
  assert.deepEqual(ids({ category: 'Uncategorised' }), [5]);
});

test('date filters accept either or both inclusive boundaries without timezone conversion', () => {
  assert.deepEqual(ids({ from: '2026-09-01' }), [1, 2, 4, 5]);
  assert.deepEqual(ids({ to: '2026-09-15' }), [1, 2, 3, 5]);
  assert.deepEqual(ids({ from: '2026-09-01', to: '2026-09-15' }), [1, 2, 5]);
  assert.deepEqual(ids({ from: '2026-09-15', to: '2026-09-15' }), [2, 5]);
  assert.deepEqual(ids({ from: '2026-09-16', to: '2026-09-01' }), []);
});

test('all combinations of supported dimensions apply AND logic', () => {
  assert.deepEqual(ids({ query: 'dinner', category: 'Food' }), [1, 2]);
  assert.deepEqual(ids({ query: 'dinner', payer: '10' }), [1]);
  assert.deepEqual(ids({ query: 'dinner', from: '2026-09-15', to: '2026-09-16' }), [2]);
  assert.deepEqual(ids({ category: 'Food', payer: '20', from: '2026-09-01', to: '2026-09-15' }), [2]);
  assert.deepEqual(ids({ query: 'dinner', category: 'Food', payer: '10', from: '2026-09-01', to: '2026-09-15' }), [1]);
  assert.deepEqual(ids({ query: 'dinner', category: 'Transport' }), []);
});

test('reset restores original order without mutating records; empty input stays empty', () => {
  const original = structuredClone(expenses);
  ids({ query: 'nothing matches' });
  assert.deepEqual(ids(EMPTY_EXPENSE_FILTERS), [1, 2, 3, 4, 5]);
  assert.deepEqual(expenses, original);
  assert.deepEqual(filterExpenses([], EMPTY_EXPENSE_FILTERS), []);
});
