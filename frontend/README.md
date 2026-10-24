# AetherStock frontend

A React and Vite inventory workspace for products, warehouse stock, purchase and
sales orders, transfers, and replenishment alerts.

## Development

```powershell
npm ci
npm run dev
```

The frontend uses port 3000. Its default `/api` proxy targets localhost:8080.
Set `VITE_API_BASE_URL` before starting Vite to use a different API, for example
`http://127.0.0.1:8081/api` for the isolated walkthrough.

```powershell
npm run build
npm run lint
node --test src/services/idempotency.test.mjs
```

## Interface

- The Overview presents real catalog, warehouse and replenishment data, with
  explicit empty states and shortcuts to the existing creation forms.
- The sidebar groups Workspace, Operations and Developer destinations. Collapse
  preference persists locally. Every destination remains available in compact mode.
- Light mode is the default; the header theme control enables a neutral dark mode.
- Global product search retains Trie suggestions, API fallback, arrow/Enter
  selection and the `/` shortcut. Form fields do not lose focus to that shortcut.
- Architecture contains component mappings, the inventory lifecycle and existing
  interactive technical checks. Autocomplete contains the prefix-search tools.
- Tokens and shared controls live in `src/index.css`, overview styling in
  `src/App.css`, and search styling in `src/styles/search.css`.

The backend API and consistency guarantees are unchanged by the visual redesign.
