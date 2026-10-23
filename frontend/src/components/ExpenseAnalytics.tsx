import { useMemo } from 'react';
import { type Expense } from '../api/expenseApi';
import { spendingByCategory } from '../utils/expenseAnalytics';

export default function ExpenseAnalytics({ expenses, money, personal = false }: { personal?: boolean; expenses: Expense[]; money: (amount: number) => string }) {
  const categories = useMemo(() => spendingByCategory(expenses), [expenses]);
  return <section className="expense-analytics" aria-labelledby="expense-analytics-title">
    <h2 id="expense-analytics-title" className="expense-section-title">{personal ? 'Personal Analytics' : 'Expense Analytics'}</h2>
    <div className="expense-card">
      <h3 className="expense-label">Spending by Category</h3>
      <p className="expense-card-note">{personal ? 'Your allocated shares across all trip expenses.' : 'All recorded expenses for this trip.'}</p>
      {!expenses.length ? <p className="expense-card-note">Add an expense to see spending by category.</p>
        : <ul className="expense-category-list">{categories.map(({ category, amount, percentage }) =>
          <li key={category}>
            <div className="expense-category-values"><span>{category}</span><span className="expense-money">{money(amount)}</span><span className="expense-category-percent">{percentage.toFixed(1)}%</span></div>
            <progress aria-label={`${category} share of ${personal ? 'personal' : 'trip'} spending`} max={100} value={percentage} />
          </li>)}</ul>}
    </div>
  </section>;
}
