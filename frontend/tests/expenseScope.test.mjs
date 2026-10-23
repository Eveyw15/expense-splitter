import { test } from 'node:test';
import assert from 'node:assert/strict';
import { scopeExpenses, personalSpending, budgetUsage } from '../src/utils/expenseScope.ts';
import { spendingByCategory } from '../src/utils/expenseAnalytics.ts';
import { filterExpenses, EMPTY_EXPENSE_FILTERS } from '../src/utils/expenseHistory.ts';

test('personal scope uses participation even with zero share, not payer or creator', () => {
  const expenses = [
    { expenseId: 1, payerId: 1, createdById: 1, amount: 300, currentUserShare: 100, category: 'Food', shares: [{ userId: 1 }] },
    { expenseId: 2, payerId: 2, createdById: 2, amount: 180, currentUserShare: 60, category: 'Food', shares: [{ userId: 1 }] },
    { expenseId: 3, payerId: 1, createdById: 1, amount: 400, currentUserShare: 0, category: 'Lodging', shares: [{ userId: 2 }] },
    { expenseId: 4, payerId: 2, createdById: 2, amount: 1, currentUserShare: 0, category: 'Other', shares: [{ userId: 1 }] },
  ].map(e => ({ description: `Expense ${e.expenseId}`, payerName: `User ${e.payerId}`, date: "2026-09-15", ...e }));
  const personal = scopeExpenses(expenses, true, 1);
  assert.deepEqual(personal.map(e => e.expenseId), [1, 2, 4]);
  assert.equal(personalSpending(personal), 160);
  assert.equal(spendingByCategory(personal.map(e => ({ ...e, amount: e.currentUserShare }))).find(c => c.category === 'Food').amount, 160);
  assert.equal(scopeExpenses(expenses, false, 1), expenses);
  assert.equal(filterExpenses(personal, { ...EMPTY_EXPENSE_FILTERS, category: 'Food' }).length, 2);
  assert.deepEqual(budgetUsage(160, 1000), { remaining: 840, percentage: 16 });
  assert.deepEqual(budgetUsage(160, null), { remaining: null, percentage: null });
  assert.deepEqual(budgetUsage(160, 0), { remaining: -160, percentage: null });
  assert.equal(budgetUsage(0.2, 0.3).remaining, 0.1);
});
