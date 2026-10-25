import test from 'node:test';
import assert from 'node:assert/strict';
import { ProductTrie } from './trie.js';
import { buildLinearReference, checkMatches, measurePrefix } from './autocompleteBenchmark.js';

const products = [
  { productId: '1', sku: 'DEMO-MOUSE-001', name: 'Wireless Mouse', category: 'Peripherals' },
  { productId: '2', sku: 'DEMO-SSD-002', name: 'Portable SSD', category: 'Electronics' },
];
test('Match check validates token, category and contiguous SKU prefixes, including genuine misses', () => {
  const trie = new ProductTrie(); trie.buildIndex(products);
  const scan = buildLinearReference(products);
  for (const prefix of ['mou', 'peri', 'demomouse', 'demo', 'appl', 'nonexistentxyz', ''])
    assert.equal(checkMatches(scan(prefix), trie.searchPrefix(prefix, 100).results).correct, true);
  assert.deepEqual(scan('mou').map(product => product.productId), ['1']);
  assert.deepEqual(scan('appl'), []);
});
test('Wrong, missing, extra and duplicate products fail the match check', () => {
  assert.equal(checkMatches([products[0]], [products[1]]).correct, false);
  assert.equal(checkMatches([products[0]], []).missing, 1);
  assert.equal(checkMatches([], [products[0]]).extra, 1);
  assert.equal(checkMatches([products[0]], [products[0], products[0]]).correct, false);
});
test('Offline API stays an error without invented timing while Trie correctness is still checked', async () => {
  const trie = new ProductTrie(); trie.buildIndex(products);
  const row = await measurePrefix('apple', trie, buildLinearReference(products), async () => { throw Error('Offline'); }, products.length);
  assert.equal(row.apiMs, null);
  assert.equal(row.apiCount, null);
  assert.equal(row.apiError, 'Offline');
  assert.equal(row.check.correct, true);
  assert.equal(row.check.expected, 0);
});
test('API count is reported separately from the prefix oracle', async () => {
  const trie = new ProductTrie(); trie.buildIndex(products);
  const row = await measurePrefix('mou', trie, buildLinearReference(products), async () => ({ content: products, totalElements: 27 }), products.length);
  assert.equal(row.apiCount, 27);
  assert.equal(row.check.returned, 1);
  assert.ok(row.apiMs >= 0);
});
