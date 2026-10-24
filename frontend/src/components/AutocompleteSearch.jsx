import React, { useState, useEffect, useRef } from 'react';
import { Search, Package, ArrowRight, CornerDownLeft } from 'lucide-react';
import { globalProductTrie } from '../services/trie';
import { api } from '../services/api';

/**
 * Autocomplete Component — System Design Component
 * Features:
 *  - High-performance In-Memory Trie lookup (< 2ms)
 *  - Hybrid fallback to debounced Server API queries
 *  - Dynamic prefix highlighting
 *  - Keyboard navigation (ArrowUp, ArrowDown, Enter)
 *  - Real-time search telemetry and query latency display
 */
export default function AutocompleteSearch({ onSelectProduct, placeholder = "Search by SKU, product name, or category... (Press '/' to focus)" }) {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [isOpen, setIsOpen] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const [latencyMs, setLatencyMs] = useState(null);
  const [searchSource, setSearchSource] = useState('trie'); // 'trie' or 'server'
  const [requestsSaved, setRequestsSaved] = useState(0);

  const inputRef = useRef(null);
  const dropdownRef = useRef(null);
  const debounceTimerRef = useRef(null);
  const keystrokeCountRef = useRef(0);

  // Global hotkey '/' to focus search bar
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === '/' && !e.target.closest('input, textarea, select, [contenteditable="true"]')) {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Handle outside clicks to close dropdown
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target) && !inputRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleInputChange = (e) => {
    clearTimeout(debounceTimerRef.current);
    const value = e.target.value;
    setQuery(value);
    keystrokeCountRef.current++;
    setSelectedIndex(-1);

    if (!value.trim()) {
      setSuggestions([]);
      setIsOpen(false);
      setLatencyMs(null);
      return;
    }

    // 1. Instant Trie Prefix Search (Client-side sub-millisecond evaluation)
    const trieResult = globalProductTrie.searchPrefix(value, 8);
    if (trieResult.results.length > 0) {
      setSuggestions(trieResult.results);
      setLatencyMs(trieResult.latencyMs);
      setSearchSource('trie');
      setIsOpen(true);
    } else {
      // 2. Hybrid Debounced Server Query (if Trie hasn't cached or user searches distant substrings)
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = setTimeout(async () => {
        const start = performance.now();
        try {
          const res = await api.getProducts(value, '', 0, 8);
          const serverLatency = Math.round((performance.now() - start) * 100) / 100;
          const items = res.content || [];
          setSuggestions(items);
          setLatencyMs(serverLatency);
          setSearchSource('server');
          setIsOpen(items.length > 0);
          
          // Index newly discovered items into Trie
          items.forEach(p => globalProductTrie.indexProduct(p));
        } catch (err) {
          console.warn('Autocomplete server search failed:', err);
        }
      }, 250); // 250ms debounce window

      // Calculate debounced savings
      setRequestsSaved(prev => prev + 1);
    }
  };

  const handleKeyDown = (e) => {
    if (!isOpen || suggestions.length === 0) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(prev => (prev < suggestions.length - 1 ? prev + 1 : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(prev => (prev > 0 ? prev - 1 : suggestions.length - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (selectedIndex >= 0 && selectedIndex < suggestions.length) {
        handleSelect(suggestions[selectedIndex]);
      } else if (suggestions.length > 0) {
        handleSelect(suggestions[0]);
      }
    } else if (e.key === 'Escape') {
      setIsOpen(false);
    }
  };

  const handleSelect = (product) => {
    setIsOpen(false);
    setQuery(product.name);
    if (onSelectProduct) {
      onSelectProduct(product);
    }
  };

  const highlightMatch = (text, prefix) => {
    if (!prefix || !text) return text;
    const index = text.toLowerCase().indexOf(prefix.toLowerCase());
    if (index === -1) return text;
    return (
      <span>
        {text.substring(0, index)}
        <strong style={{ color: 'var(--accent-cyan)', textDecoration: 'underline' }}>
          {text.substring(index, index + prefix.length)}
        </strong>
        {text.substring(index + prefix.length)}
      </span>
    );
  };

  return (
    <div className="autocomplete-root">
      <div className="search-control">
        <Search size={16} className="search-icon" />
        <input id="autocomplete-search-input" ref={inputRef} type="text" className="form-input search-input"
          role="combobox" aria-label="Search products" aria-expanded={isOpen && suggestions.length > 0}
          aria-controls="autocomplete-dropdown" aria-autocomplete="list"
          aria-activedescendant={isOpen && selectedIndex >= 0 ? `search-option-${selectedIndex}` : undefined}
          value={query} onChange={handleInputChange} onKeyDown={handleKeyDown}
          onFocus={() => query.trim() && suggestions.length > 0 && setIsOpen(true)} placeholder={placeholder}
          style={{ borderRadius: varRadius('5px') }} />
        {!query && <kbd className="search-shortcut">/</kbd>}
      </div>
      {isOpen && suggestions.length > 0 && (
        <div ref={dropdownRef} id="autocomplete-dropdown" className="search-dropdown" role="listbox" aria-label="Product suggestions">
          <div className="search-dropdown-heading"><span>Products</span><span>{suggestions.length} results</span></div>
          <div className="search-results">
            {suggestions.map((item, idx) => <button type="button" key={item.productId || item.sku}
              id={`search-option-${idx}`} className={`search-result${idx === selectedIndex ? ' selected' : ''}`}
              role="option" aria-selected={idx === selectedIndex} onClick={() => handleSelect(item)}
              onMouseEnter={() => setSelectedIndex(idx)}>
              <span className="product-avatar"><Package size={16} /></span>
              <span className="search-result-text"><strong>{highlightMatch(item.name, query)}</strong><small>{highlightMatch(item.sku, query)}{item.category ? ` · ${item.category}` : ''}</small></span>
              <span className="search-price">${Number(item.price || 0).toFixed(2)}</span><ArrowRight size={14} />
            </button>)}
          </div>
          <div className="search-footer" title={`Source: ${searchSource}; lookup: ${latencyMs ?? '—'}ms; saved requests: ${requestsSaved}`}><span><kbd>↑</kbd><kbd>↓</kbd> to navigate</span><span><CornerDownLeft size={12} /> to select</span></div>
        </div>
      )}
    </div>
  );
}

function varRadius(value) { return value; }
