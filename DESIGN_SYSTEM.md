# MindSpark Design System (Unified Cosmic Midnight & Liquid Glass)

> Comprehensive visual, typographic, token, and layout specification for the `SFE7/MindSpark` Angular 22 frontend (`frontend/`), unified under the **Cosmic Midnight & Liquid Glass** design language across every route, drawer, modal, and admin console view.

---

## 1. Tech Stack & Styling Architecture

| Layer | Technology | Details | Source |
| :--- | :--- | :--- | :--- |
| **Framework** | Angular (Standalone Components & Signals) | `^22.2.0` (`@angular/core`, `@angular/router`, `@angular/forms`) | [package.json](file:///u:/yahia/9raya/coss%20platform%20tp%202%20dart/cross%20platform%20tp2%20dart/MindSpark/frontend/package.json) |
| **Build System** | Angular CLI / Application Builder | `@angular/build` `^22.2.2`, TypeScript `~6.0.2` | [angular.json](file:///u:/yahia/9raya/coss%20platform%20tp%202%20dart/cross%20platform%20tp2%20dart/MindSpark/frontend/angular.json) |
| **Styling Engine** | Component-scoped **SCSS** + Global `:root` CSS Custom Properties | `inlineStyleLanguage: "scss"`; zero external CSS frameworks | [styles.scss](file:///u:/yahia/9raya/coss%20platform%20tp%202%20dart/cross%20platform%20tp2%20dart/MindSpark/frontend/src/styles.scss) |
| **Canvas / Graphics** | Native SVG (`viewBox="0 0 1000 500"`) + HTML/CSS Overlays | Spatial Studio layout with dark dot-grid & glass Idea Cards | [workspace-canvas.html](file:///u:/yahia/9raya/coss%20platform%20tp%202%20dart/cross%20platform%20tp2%20dart/MindSpark/frontend/src/app/features/rooms/components/workspace-canvas/workspace-canvas.html) |
| **Authentication** | Keycloak JS | `keycloak-js` `^26.2.4` | [package.json](file:///u:/yahia/9raya/coss%20platform%20tp%202%20dart/cross%20platform%20tp2%20dart/MindSpark/frontend/package.json) |
| **Testing** | Vitest + JSDOM | `vitest` `^5.0.0`, `jsdom` `^30.0.0` | [package.json](file:///u:/yahia/9raya/coss%20platform%20tp%202%20dart/cross%20platform%20tp2%20dart/MindSpark/frontend/package.json) |

### Unified Aesthetic
Every route (`/`, `/room/:roomId`, `/sessions`, `/sessions/:id`, `/billing`, `/admin/*`) shares a single dark **Cosmic Midnight & Liquid Glass** token system defined in `:root` in [styles.scss](file:///u:/yahia/9raya/coss%20platform%20tp%202%20dart/cross%20platform%20tp2%20dart/MindSpark/frontend/src/styles.scss). The only intentional light-surface exception is the `@media print` stylesheet in [session-detail.scss](file:///u:/yahia/9raya/coss%20platform%20tp%202%20dart/cross%20platform%20tp2%20dart/MindSpark/frontend/src/app/features/sessions/pages/session-detail/session-detail.scss) so printed/PDF session reports render cleanly on white paper.

---

## 2. Color Palette & Theme Tokens (`:root`)

### Backgrounds & Surfaces
| Token | Value | Role |
| :--- | :--- | :--- |
| `--ms-bg` | `#060A22` | Deep Cosmic Base (all page backgrounds) |
| `--ms-canvas-bg` | `#080D2B` | Collaborative Room Canvas stage |
| `--ms-canvas-dot` | `#1D2766` | `radial-gradient` dot-grid (`1.5px` dots at `28px 28px`) |
| `--ms-surface` | `#0D1438` | Cards, modals, admin sidebar, panels |
| `--ms-surface-raised` | `#141D4F` | Inputs, nested panels, badges, table headers |
| `--ms-surface-focus` | `#172159` | Focused input background |
| `--ms-surface-glass` | `linear-gradient(135deg, rgba(16, 24, 66, 0.72), rgba(10, 16, 48, 0.58), rgba(22, 32, 86, 0.68))` | Floating header, tool dock, filter bar, left ideas rail |
| `--ms-surface-glass-heavy` | `linear-gradient(135deg, rgba(16, 24, 66, 0.92), rgba(10, 16, 48, 0.88), rgba(22, 32, 86, 0.92))` | AI Drawer, Comments Drawer, Notifications & Export Popovers |
| `--ms-backdrop-blur` | `blur(20px) saturate(180%)` | Liquid-glass backdrop filter |
| `--ms-modal-backdrop` | `rgba(4, 7, 24, 0.75)` | Modal overlay dim + `blur(8px)` |

### Brand & Accent Tokens
| Token | Value | Role |
| :--- | :--- | :--- |
| `--ms-primary` | `#6F82F2` | Primary CTAs, active tabs/pills, focus borders |
| `--ms-primary-hover` | `#8294F8` | Primary hover state |
| `--ms-primary-light` | `#B4BEFF` | Links, code badges, focus outlines, subtle highlights |
| `--ms-gold` | `#FFC83D` | Hero sparks, AI Co-pilot accents, KPI highlights, Upgrade CTA |
| `--ms-gold-hover` | `#FFE17D` | Gold CTA hover |
| `--ms-coral` | `#FF9A8F` | Join room CTA, vote badges, unread dots, destructive hover |
| `--ms-coral-hover` | `#FFB0A7` | Coral CTA hover |
| `--ms-violet` | `#8B5CF6` | Super-admin & AI synthesis accents |
| `--ms-mint` | `#34D399` | Positive metrics, checkmarks, connected status |

### Typography, Borders & Semantic Status
| Token | Value | Role |
| :--- | :--- | :--- |
| `--ms-text` | `#F4F6FF` | Primary headings & body text |
| `--ms-text-secondary` | `#C5CEEE` | Secondary copy, table cells, nav links |
| `--ms-text-muted` | `#9AA5CE` | Labels, timestamps, placeholders |
| `--ms-text-subtle` | `#6F7AA8` | Captions, footer text |
| `--ms-border` | `#27316E` | Standard card & input borders |
| `--ms-border-glass` | `rgba(180, 190, 255, 0.22)` | Liquid-glass panel borders |
| `--ms-success` / `-bg` / `-border` | `#10B981` / `rgba(16,185,129,0.14)` / `rgba(16,185,129,0.4)` | Connected status, active plans, vote pool |
| `--ms-warning` / `-bg` / `-border` | `#F59E0B` / `rgba(245,158,11,0.14)` / `rgba(245,158,11,0.42)` | Reconnecting, offline banners, quota warnings |
| `--ms-error` / `-bg` / `-border` | `#F43F5E` / `rgba(244,63,94,0.14)` / `rgba(244,63,94,0.42)` | Closed badges, errors, delete actions |
| `--ms-info` / `-bg` / `-border` | `#38BDF8` / `rgba(56,189,248,0.14)` / `rgba(56,189,248,0.4)` | Polar mode, provider busy, admin info banners |

### Canvas Drawing & Idea Card Palette
Defined in [workspace-canvas.ts](file:///u:/yahia/9raya/coss%20platform%20tp%202%20dart/cross%20platform%20tp2%20dart/MindSpark/frontend/src/app/features/rooms/components/workspace-canvas/workspace-canvas.ts):
- **Mist:** `#E2E8F0` (also used as automatic dark-mode render fallback for legacy `#273b3b` strokes)
- **Periwinkle:** `#6F82F2` (default idea card accent)
- **Coral:** `#FF9A8F`
- **Gold:** `#FFC83D`
- **Mint:** `#34D399`
- **Lilac:** `#A78BFA`
- Idea cards render with `color-mix(in srgb, var(--idea-color, #6F82F2) 18%, #0D1438)` + `backdrop-filter: blur(12px)` and `1.5px` glowing borders.

---

## 3. Typography & Elevation

- **Display Headings (`--ms-font-display`):** `'Bricolage Grotesque', system-ui, sans-serif` (weights `600`, `700`, `800`)
- **UI & Body (`--ms-font-sans`):** `'Inter', system-ui, sans-serif` (weights `400`–`700`)
- **Monospace (`--ms-font-mono`):** `'JetBrains Mono', 'DM Mono', ui-monospace, monospace` (room codes, KPI values, timestamps, badges)
- **Elevation & Glows:**
  - `--ms-shadow-glass` for floating islands, drawers, and modals
  - `--ms-glow-primary`, `--ms-glow-gold`, `--ms-glow-coral` for interactive focus/hover states

---

## 4. Spatial Studio Layout & Responsive Breakpoints

- **Winning Workspace Architecture (Spatial Studio):** Full-bleed `#080D2B` dot-grid canvas (`100vw` × `100dvh`), floating liquid-glass top bar, centered glass Filter/Search pill bar, collapsible left Room Ideas rail, bottom-center liquid-glass Tool Dock, and right-docked glass drawers for **AI Co-pilot** and **Comments**.
- **Breakpoints:** `960px` (2-col rooms grid), `900px` (workspace responsive dock/rail), `860px` (1-col home actions & admin split), `768px` (header mobile drawer & admin vertical stack), `640px` (1-col rooms & compact workspace), `@media print` (white-paper PDF report export).
