# LoyPOS (Native Android)

LoyPOS is an offline-first retail Point-of-Sale (POS) and inventory management Android application rewritten in **Kotlin**, **Jetpack Compose**, **Material 3**, and **Room (SQLite)**.

## Features

- **Offline-First Authentication & Roles**
  - Owner sign-up and sign-in with salted 5,000-round SHA-256 credentials persisted locally in Room.
  - Role-based navigation: Owners have full access to Sales, Receipts, Shift, Items, Settings, and Back Office tools; Sellers (cashiers) are pinned to their assigned branch with access to Sales, Receipts, Shift, and Account.
- **Point of Sale (Sales Tab)**
  - Responsive layout: split catalog + side receipt panel on tablets/wide screens (`>= 860dp`) and sticky bottom cart bar on phones.
  - Multi-branch switcher, real-time search (name, SKU, barcode), category filter chips, stock indicators, and item thumbnails.
  - Open/parked tickets (`Save` / `Open tickets`), line quantity editor with numeric keypad, percentage and fixed-amount discounts, customer loyalty attachment, and receipt notes.
- **Checkout & Receipts**
  - Cash and Card payment methods, quick-cash suggestions, real-time change calculation, and full numeric keypad.
  - 80mm thermal receipt PDF generation (`android.graphics.pdf.PdfDocument`) and plain-text receipt sharing via Android `FileProvider`.
  - Receipt archive with search and full refund support (restores tracked stock and records `refund` movements).
- **Cash-Drawer Shifts**
  - Today's sales summary, opening float, cash-in / cash-out movements, expected drawer balance calculation, shift closing count with over/short variance tracking, and shift history.
- **Catalog, Inventory, Customers & Reports**
  - Items, Categories, and Discounts management with zero-permission Android Photo Picker (`PickVisualMedia`) and multi-branch availability filters.
  - Back Office tools accessible via Settings: Inventory & Stock (receive/count adjustments + movement log), Customers CRUD + loyalty points, and Sales Reports (Today / 7 days / 30 days / All KPIs, 7-day bar chart, payment split, category breakdown, and top items).
- **Backup, Health & Cloud Check**
  - Portable `loypos-export` v1 JSON export/import (including base64 item photos), automatic local safety backups (retains 5 copies), SQLite `PRAGMA integrity_check`, `VACUUM`/`ANALYZE`, and optional LoyPOS Cloud endpoint health sync.

## Build

```bash
gradle assembleDebug
```

## GitHub Actions & Releases

The repository includes a GitHub Actions workflow (`.github/workflows/release.yml`) that builds both Debug and Release APKs (`loypos-debug.apk` and `loypos-release.apk`):

- **Branch pushes (`main` / `master`)**: Builds both APKs and uploads them as workflow artifacts.
- **Version tags (`v*`, e.g. `v1.0.0`) or manual `workflow_dispatch`**: Builds both APKs and publishes a GitHub Release with downloadable APK assets.
- **Signing**: Uses `debug.keystore.base64` by default, or optionally signs the release APK when `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` repository secrets are configured.

## License

MIT - see [LICENSE](LICENSE).
