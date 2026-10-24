import test from 'node:test';
import assert from 'node:assert/strict';
import { pendingCreate, completeCreate } from './idempotency.js';

test('unchanged retry retains its key until success', () => {
  const payload = { items: [{ productId: 'p', quantity: 3 }] };
  const first = pendingCreate('retry-test', payload);
  assert.equal(pendingCreate('retry-test', payload).idempotencyKey, first.idempotencyKey);
  completeCreate('retry-test', first.idempotencyKey);
  assert.notEqual(pendingCreate('retry-test', payload).idempotencyKey, first.idempotencyKey);
});

test('changed payload and different action scopes get distinct keys', () => {
  const first = pendingCreate('payload-test', { quantity: 3 });
  assert.notEqual(pendingCreate('payload-test', { quantity: 4 }).idempotencyKey, first.idempotencyKey);
  assert.notEqual(pendingCreate('other-action', { quantity: 3 }).idempotencyKey, first.idempotencyKey);
});

test('late acknowledgement cannot clear a newer form intent', () => {
  const old = pendingCreate('late-test', { quantity: 3 });
  const current = pendingCreate('late-test', { quantity: 4 });
  completeCreate('late-test', old.idempotencyKey);
  assert.equal(pendingCreate('late-test', { quantity: 4 }).idempotencyKey, current.idempotencyKey);
});

test('saved session intent is reused after reload and removed after success', () => {
  const values = new Map();
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
  } });
  try {
    values.set('inventory:pending-create:reload-test', JSON.stringify({ fingerprint: '{"quantity":3}', key: 'persisted-key' }));
    const request = pendingCreate('reload-test', { quantity: 3 });
    assert.equal(request.idempotencyKey, 'persisted-key');
    completeCreate('reload-test', request.idempotencyKey);
    assert.equal(values.size, 0);
  } finally { delete globalThis.sessionStorage; }
});
