import type { Expense } from '../api/expenseApi';
import { EXPENSE_CATEGORIES, expenseCategory } from './expenseHistory.ts';

export function spendingByCategory(expenses: Expense[]) {
  const totals = new Map(EXPENSE_CATEGORIES.map(category => [category, 0]));
  let totalCents = 0;
  for (const expense of expenses) {
    // Persisted amounts have two decimal places. Sum integer cents rather
    // than accumulating binary floating-point currency values.
    const cents = Math.round(expense.amount * 100);
    const category = expenseCategory(expense.category);
    totals.set(category, (totals.get(category) ?? 0) + cents);
    totalCents += cents;
  }
  return [...totals].map(([category, cents]) => ({
    category,
    amount: cents / 100,
    percentage: totalCents > 0 ? cents / totalCents * 100 : 0,
  }));
}
