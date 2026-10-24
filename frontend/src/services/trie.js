/**
 * In-Memory Trie Data Structure for Fast Product Search & Autocomplete
 * Search component
 * Provides sub-millisecond prefix search over SKUs, Product Names, and Categories.
 */

class TrieNode {
  constructor() {
    this.children = new Map();
    this.isEndOfWord = false;
    this.products = []; // Array of product references
    this.frequency = 0; // Term frequency for ranking
  }
}

export class ProductTrie {
  constructor() {
    this.root = new TrieNode();
    this.totalWords = 0;
    this.nodeCount = 1;
    this.lookupHistory = [];
  }

  /**
   * Tokenize text into words/sub-tokens (e.g., "Apple MacBook Pro M3" -> ["apple", "macbook", "pro", "m3"])
   */
  tokenize(text) {
    if (!text) return [];
    return text
      .toLowerCase()
      .replace(/[^a-z0-9\s-]/g, ' ')
      .split(/[\s-]+/)
      .filter(t => t.length > 0);
  }

  /**
   * Insert a word into the Trie with its product reference
   */
  insertWord(word, product) {
    let current = this.root;
    for (const char of word) {
      if (!current.children.has(char)) {
        current.children.set(char, new TrieNode());
        this.nodeCount++;
      }
      current = current.children.get(char);
    }
    current.isEndOfWord = true;
    current.frequency++;
    
    // Add product if not already present
    if (!current.products.some(p => p.productId === product.productId)) {
      current.products.push(product);
    }
    this.totalWords++;
  }

  /**
   * Index an entire product across SKU, Name, and Category
   */
  indexProduct(product) {
    if (!product) return;
    
    // Index full SKU
    if (product.sku) {
      const skuTokens = this.tokenize(product.sku);
      skuTokens.forEach(t => this.insertWord(t, product));
      // Also insert contiguous SKU without hyphens
      const rawSku = product.sku.toLowerCase().replace(/[^a-z0-9]/g, '');
      if (rawSku) this.insertWord(rawSku, product);
    }

    // Index Name tokens
    if (product.name) {
      const nameTokens = this.tokenize(product.name);
      nameTokens.forEach(t => this.insertWord(t, product));
    }

    // Index Category tokens
    if (product.category) {
      const categoryTokens = this.tokenize(product.category);
      categoryTokens.forEach(t => this.insertWord(t, product));
    }
  }

  /**
   * Bulk index an array of products
   */
  buildIndex(products) {
    this.root = new TrieNode();
    this.totalWords = 0;
    this.nodeCount = 1;
    products.forEach(p => this.indexProduct(p));
  }

  /**
   * Prefix search returning matched products and search latency
   */
  searchPrefix(prefix, limit = 8) {
    const startTime = performance.now();
    const cleanPrefix = prefix.trim().toLowerCase();
    
    if (!cleanPrefix) {
      return { results: [], latencyMs: 0, prefix: '' };
    }

    let current = this.root;
    for (const char of cleanPrefix) {
      if (!current.children.has(char)) {
        const latencyMs = performance.now() - startTime;
        return { results: [], latencyMs, prefix: cleanPrefix };
      }
      current = current.children.get(char);
    }

    // Collect all products under this subtree using DFS
    const matchedProductsMap = new Map();
    this._collectAllProducts(current, matchedProductsMap);

    const results = Array.from(matchedProductsMap.values()).slice(0, limit);
    const latencyMs = Math.round((performance.now() - startTime) * 100) / 100;

    // Track history for metrics report
    this.lookupHistory.push({
      prefix: cleanPrefix,
      latencyMs,
      resultCount: results.length,
      timestamp: Date.now()
    });
    if (this.lookupHistory.length > 50) this.lookupHistory.shift();

    return {
      results,
      latencyMs,
      prefix: cleanPrefix
    };
  }

  _collectAllProducts(node, map) {
    if (node.products && node.products.length > 0) {
      for (const p of node.products) {
        if (!map.has(p.productId)) {
          map.set(p.productId, p);
        }
      }
    }
    for (const childNode of node.children.values()) {
      this._collectAllProducts(childNode, map);
    }
  }

  /**
   * Metrics and statistics for search performance
   */
  getStats() {
    const avgLatency = this.lookupHistory.length > 0
      ? (this.lookupHistory.reduce((sum, h) => sum + h.latencyMs, 0) / this.lookupHistory.length).toFixed(3)
      : '0.000';

    return {
      nodeCount: this.nodeCount,
      totalWords: this.totalWords,
      totalQueries: this.lookupHistory.length,
      avgLatencyMs: avgLatency,
      recentHistory: [...this.lookupHistory].reverse()
    };
  }
}

// Global Singleton for in-browser client caching
export const globalProductTrie = new ProductTrie();
