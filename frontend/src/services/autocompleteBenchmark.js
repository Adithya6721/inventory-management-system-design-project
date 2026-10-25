// Independent linear reference: compare product IDs, including valid zero-match queries.
function tokens(product) {
  const words = value => String(value || '').toLowerCase().replace(/[^a-z0-9\s-]/g, ' ').split(/[\s-]+/).filter(Boolean);
  return [...words(product.sku), String(product.sku || '').toLowerCase().replace(/[^a-z0-9]/g, ''),
    ...words(product.name), ...words(product.category)].filter(Boolean);
}

export function buildLinearReference(products) {
  const indexed = products.map(product => ({ product, tokens: tokens(product) }));
  return prefix => {
    const clean = prefix.trim().toLowerCase();
    return clean ? indexed.filter(row => row.tokens.some(token => token.startsWith(clean))).map(row => row.product) : [];
  };
}

export function checkMatches(expected, actual) {
  const expectedIds = new Set(expected.map(product => product.productId));
  const actualIds = new Set(actual.map(product => product.productId));
  const missing = [...expectedIds].filter(id => !actualIds.has(id));
  const extra = [...actualIds].filter(id => !expectedIds.has(id));
  return { correct: missing.length === 0 && extra.length === 0 && actual.length === actualIds.size,
    expected: expectedIds.size, returned: actualIds.size, missing: missing.length, extra: extra.length };
}

export async function measurePrefix(prefix, trie, scan, fetchProducts, catalogSize) {
  const repetitions = 100;
  let start = performance.now();
  for (let i = 0; i < repetitions; i++) trie.searchPrefix(prefix, 10);
  const trieMs = (performance.now() - start) / repetitions;
  start = performance.now();
  for (let i = 0; i < repetitions; i++) scan(prefix).slice(0, 10);
  const scanMs = (performance.now() - start) / repetitions;
  // Full candidate validation is deliberately outside the timed lookup batch.
  const check = checkMatches(scan(prefix), trie.searchPrefix(prefix, Math.max(1, catalogSize)).results);
  let apiMs = null, apiCount = null, apiError = null;
  try {
    start = performance.now();
    const response = await fetchProducts(prefix);
    apiMs = performance.now() - start;
    apiCount = response.totalElements ?? response.content.length;
  } catch (error) { apiError = error.message || 'API request failed'; }
  return { prefix, trieMs, scanMs, apiMs, apiCount, apiError, check };
}
