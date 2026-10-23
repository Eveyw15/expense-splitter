import type { Expense } from '../api/expenseApi';

export const EXPENSE_CATEGORIES = ['Food', 'Transport', 'Lodging', 'Culture', 'Activities', 'Other'];
export const expenseCategory = (category: string | null) => category?.trim() || 'Uncategorised';

export interface ExpenseFilters {
  query: string;
  category: string;
  payer: string;
  from: string;
  to: string;
}

export const EMPTY_EXPENSE_FILTERS: ExpenseFilters = { query: '', category: '', payer: '', from: '', to: '' };

export function filterExpenses(expenses: Expense[], filters: ExpenseFilters): Expense[] {
  const query = filters.query.trim().toLocaleLowerCase();
  return expenses.filter(expense => {
    const category = expenseCategory(expense.category);
    const matchesSearch = [expense.description, expense.payerName, category]
      .some(value => value.toLocaleLowerCase().includes(query));
    // The API returns LocalDate as YYYY-MM-DD. Compare directly to keep both
    // boundaries inclusive without UTC/local-time conversion.
    return matchesSearch
      && (!filters.category || category === filters.category)
      && (!filters.payer || String(expense.payerId) === filters.payer)
      && (!filters.from || expense.date >= filters.from)
      && (!filters.to || expense.date <= filters.to);
  });
}
