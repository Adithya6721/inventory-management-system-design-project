import React, { useState, useEffect, useRef } from 'react';
import { Search, Zap, Clock, Package, ArrowRight, CornerDownLeft } from 'lucide-react';
import { globalProductTrie } from '../services/trie';
import { api } from '../services/api';

/**
 * Autocomplete Component — Member 4 System Design Component
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
      if (e.key === '/' && document.activeElement !== inputRef.current) {
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
    <div style={{ position: 'relative', width: '100%', maxWidth: '520px' }}>
      <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
        <Search
          size={18}
          style={{
            position: 'absolute',
            left: '14px',
            color: 'var(--text-secondary)',
            pointerEvents: 'none'
          }}
        />
        <input
          id="autocomplete-search-input"
          ref={inputRef}
          type="text"
          className="form-input"
          value={query}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          onFocus={() => query.trim() && suggestions.length > 0 && setIsOpen(true)}
          placeholder={placeholder}
          style={{
            paddingLeft: '40px',
            paddingRight: latencyMs !== null ? '130px' : '36px',
            background: 'var(--bg-input)',
            borderRadius: varRadius(isOpen ? '10px 10px 0 0' : '10px'),
            height: '42px',
            boxShadow: isOpen ? '0 0 15px rgba(99, 102, 241, 0.25)' : 'none'
          }}
        />

        {/* Live System Design Telemetry Badge */}
        {latencyMs !== null && query.trim() && (
          <div
            style={{
              position: 'absolute',
              right: '10px',
              display: 'flex',
              alignItems: 'center',
              gap: '4px',
              fontSize: '0.7rem',
              padding: '2px 8px',
              borderRadius: '9999px',
              background: searchSource === 'trie' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(99, 102, 241, 0.15)',
              color: searchSource === 'trie' ? '#34d399' : '#a5b4fc',
              border: `1px solid ${searchSource === 'trie' ? 'rgba(16, 185, 129, 0.3)' : 'rgba(99, 102, 241, 0.3)'}`
            }}
            title={`Source: ${searchSource === 'trie' ? 'In-Memory Trie' : 'Spring Boot REST'}`}
          >
            {searchSource === 'trie' ? <Zap size={11} /> : <Clock size={11} />}
            <span>{latencyMs}ms</span>
            <span style={{ opacity: 0.7, textTransform: 'uppercase' }}>{searchSource}</span>
          </div>
        )}
      </div>

      {/* Autocomplete Dropdown List */}
      {isOpen && suggestions.length > 0 && (
        <div
          ref={dropdownRef}
          id="autocomplete-dropdown"
          style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: 0,
            background: '#131b2e',
            border: '1px solid var(--border-glass)',
            borderTop: 'none',
            borderRadius: '0 0 12px 12px',
            boxShadow: '0 12px 28px rgba(0,0,0,0.7)',
            zIndex: 100,
            overflow: 'hidden'
          }}
        >
          <div style={{ maxHeight: '340px', overflowY: 'auto' }}>
            {suggestions.map((item, idx) => {
              const isSelected = idx === selectedIndex;
              return (
                <div
                  key={item.productId || item.sku}
                  onClick={() => handleSelect(item)}
                  onMouseEnter={() => setSelectedIndex(idx)}
                  style={{
                    padding: '10px 16px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    cursor: 'pointer',
                    background: isSelected ? 'rgba(99, 102, 241, 0.2)' : 'transparent',
                    borderLeft: isSelected ? '3px solid var(--primary)' : '3px solid transparent',
                    transition: 'background 100ms ease'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <div
                      style={{
                        width: '32px',
                        height: '32px',
                        borderRadius: '6px',
                        background: 'rgba(255, 255, 255, 0.05)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'var(--primary)'
                      }}
                    >
                      <Package size={16} />
                    </div>
                    <div>
                      <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>
                        {highlightMatch(item.name, query)}
                      </div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', display: 'flex', gap: '8px' }}>
                        <span>SKU: {highlightMatch(item.sku, query)}</span>
                        <span>•</span>
                        <span style={{ color: 'var(--accent-cyan)' }}>{item.category}</span>
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <span style={{ fontSize: '0.875rem', fontWeight: 700, color: '#34d399' }}>
                      ${Number(item.price || 0).toFixed(2)}
                    </span>
                    <ArrowRight size={14} style={{ color: 'var(--text-muted)' }} />
                  </div>
                </div>
              );
            })}
          </div>

          {/* Autocomplete Footer Bar with Metrics */}
          <div
            style={{
              padding: '6px 14px',
              background: 'rgba(0, 0, 0, 0.4)',
              borderTop: '1px solid var(--border-subtle)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              fontSize: '0.72rem',
              color: 'var(--text-muted)'
            }}
          >
            <span>Navigate: <kbd>↑</kbd> <kbd>↓</kbd> Select: <kbd>↵</kbd></span>
            <span>
              Trie Nodes: <strong>{globalProductTrie.nodeCount}</strong> | Saved API Calls: <strong>{requestsSaved}</strong>
            </span>
          </div>
        </div>
      )}
    </div>
  );
}

function varRadius(val) {
  return val;
}
