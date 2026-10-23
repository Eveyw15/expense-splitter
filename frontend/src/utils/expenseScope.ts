import type { Expense } from '../api/expenseApi';

export function scopeExpenses(expenses: Expense[], personal: boolean, userId: number | undefined) {
  return personal ? expenses.filter(e => e.shares.some(s => s.userId === userId)) : expenses;
}
export function personalSpending(expenses: Expense[]) {
  return expenses.reduce((cents, e) => cents + Math.round(e.currentUserShare * 100), 0) / 100;
}
export function budgetUsage(spending: number, budget: number | null) {
  return {
    remaining: budget === null ? null : (Math.round(budget * 100) - Math.round(spending * 100)) / 100,
    percentage: budget === null || budget <= 0 ? null : Math.round(spending / budget * 10000) / 100,
  };
}
