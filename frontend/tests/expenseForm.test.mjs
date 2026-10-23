import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

test('expense form renders every field', async () => {
  const server = await createServer({ logLevel: 'silent', server: { middlewareMode: true, hmr: false },
    optimizeDeps: { noDiscovery: true }, appType: 'custom' });
  try {
    const { default: ExpenseForm } = await server.ssrLoadModule('/src/components/ExpenseForm.tsx');
    const { itemLineCents } = await server.ssrLoadModule('/src/utils/expenseItem.ts');
    assert.equal(itemLineCents('37', '2'), 7400);
    assert.equal(itemLineCents('37', ''), 3700);
    assert.equal(itemLineCents('37', '1.5'), null);
    const html = renderToStaticMarkup(React.createElement(ExpenseForm, {
      tripId: 1, baseCurrency: 'AUD', members: [{ userId: 2, username: 'Amy' }], onSaved() {}, onCancel() {},
    }));
    for (const label of ['Description', 'Category', 'Date', 'Amount', 'Paid by', 'Equal', 'Custom', 'By Item', 'Receipt', 'Upload'])
      assert.ok(html.includes(label), `missing ${label}`);
    assert.match(html, /aria-label="Receipt file"[^>]*type="file"|type="file"[^>]*aria-label="Receipt file"/);
  } finally { await server.close(); }
});
