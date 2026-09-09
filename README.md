# ☕ BLANC COFFEE — Shop Manager

**BLANC COFFEE** is a fully offline, local-first shop management app built for a small
Myanmar coffee shop selling **Coffee**, **Green Tea**, and **Macadamia Nuts**.
All data lives on the device in a Room SQLite database (`blanc_coffee.db`) —
no cloud sync, no authentication, no AI features, no network permissions.

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 (light & dark coffee-inspired palette) |
| Database | Room (SQLite), schema v1 |
| Architecture | Repository → ViewModel (`AndroidViewModel` + `StateFlow`) → Compose screens |
| Currency | MMK (Myanmar Kyat), seeded with Myanmar sample customers |

---

## Features

### 📊 Dashboard
- Net profit hero card with income / outcome / margin for the selected period
- Time-period filters: Today - 7 Days - 30 Days - All Time
- Category sales breakdown (Coffee / Green Tea / Macadamia) with units sold & revenue share
- Live inventory alert banner for low-stock and out-of-stock products
- **Expiry banner** for expired / expiring-this-week products and raw ingredients
- **7-day sales trend chart** (net revenue per day) with week total
- **Day close-out report**: net sales, cash in/out, per-method split, top sellers,
  raw used, tabs opened/collected/outstanding — with one-tap shareable text
- **Backup card**: export the whole database to a versioned JSON file, restore it
  here or on a new phone (fully offline, no permissions needed)
- Recent activity feed (orders + finance entries)

### 🧾 Orders
- Full order lifecycle: `PENDING → PREPARING → COMPLETED` (plus `CANCELLED`)
- Real-time stock deduction when an order is created
- Payment tracking: `PAID / UNPAID` with cash / card / mobile payment methods
- **Credit tabs** — UNPAID orders stay open; **Collect** records partial or full
  payments (over-payment rejected), and settling in full flips the order to PAID
  with exactly one income entry. Customer directory shows each customer's open
  **Owes** balance.
- **Discounts / promos** — optional MMK-off amount + reason per order; income,
  receipts and reports always use the net (gross − discount, never negative)
- **Idempotent income recording** — an order can never produce two income entries
- Cancelling an order restores exact stock quantities and, if it was paid,
  automatically records a compensating **refund** transaction
- Order numbers are collision-free (`ORD-1001, ORD-1002, …`) even after deletions
- Search by customer name / phone / order number, filter chips per status with live counts
- Customer directory tab with per-customer total spent and last order date
- Confirmation dialog before cancelling (with refund warning for paid orders)
- Instant text **order slip / receipt** after creating an order (and from any order card)
  with one-tap sharing through messaging apps (Viber/Telegram/Messenger) or any
  Bluetooth-printing share target

### 📦 Inventory
- Product catalog with SKU, unit, cost price, selling price and profit margin
- Search + category filters + a dedicated "Low Stock" filter, with live counts
- Sort products by name, stock level, or category
- Prominent low-stock (⚠ orange) and out-of-stock (● red) badges with icons
- Quick stock adjuster, full restock flow (validated quantity > 0, cost ≥ 0) that
  automatically logs a `RESTOCKING` expense transaction
- Edit / delete products (delete requires confirmation)
- **Expiry dates** — optional `YYYY-MM-DD` per product and per raw ingredient;
  expired (⛔) and expiring-this-week (⏳) badges on cards plus a dashboard banner
- **Raw Materials tab** — track ingredients that go *into* products
  (e.g. raw macadamia kernels in bags, green coffee beans, matcha powder in kg):
  each card shows **Bought / Used / Remaining**, each **Buy** records the date,
  quantity and cost (and logs a `RESTOCKING` expense), each **Use** records what
  was consumed and why, and **History** shows the full dated ledger per ingredient.
  **🔗 Recipe** links a raw to finished products (qty used per 1 unit sold) so
  every order **auto-deducts** raw stock — manual Use and auto-deduct share the
  same ledger, and raw shortage never blocks a sale (stock clamps at zero).

### 💰 Finance
- Unified ledger of income & expense transactions with running totals
  (filtered income, filtered outcome, and net) plus all-time summary cards
- Filter by type (All / Income / Expenses), search across title, category and note
- Add, **edit** (amount, title, note, category) and delete entries
  (delete requires confirmation)
- Order-linked income shows its source order

---

## Package structure

```
com.blanccoffee.app
├── MainActivity.kt              # Bottom navigation, badges, error snackbar, write indicator
├── data/
│   ├── di/DatabaseModule.kt     # Builds the Room database & repository singletons
│   ├── local/                   # AppDatabase + DAOs (Product, Order, Transaction, Inventory…)
│   ├── model/                   # Entities & enums (Product, CustomerOrder, Transaction…)
│   └── repository/ShopRepository.kt   # All business logic, every write on Dispatchers.IO
└── ui/
    ├── ShopViewModel.kt         # StateFlows + write operations with loading & error handling
    ├── components/              # Shared composables (badges, empty states, dialogs, formatting)
    ├── screens/                 # DashboardScreen, OrdersScreen, InventoryScreen, FinanceScreen
    └── theme/                   # Coffee-inspired Material 3 palette, light & dark themes
```

## Business rules enforced by the repository

1. **No overselling** — orders whose quantity exceeds available stock are rejected
   (`IllegalArgumentException`, surfaced as a snackbar by the ViewModel).
2. **Single income per order** — before inserting an `ORDER_SALE` income row, the
   repository checks `TransactionDao.countPositiveIncomeForOrder(orderId)`; income is
   recorded when an order is created paid, when a tab is settled in full, or when
   payment is manually collected — never twice. Income is always the **net**
   (gross − discount).
3. **Tabs stay open** — completing an UNPAID order does NOT mark it paid; the tab
   stays collectible (partial payments allowed, over-payment rejected) until the
   net is covered.
4. **Cancel = restore + refund** — cancelling restores each `OrderItem`'s exact quantity
   back to stock. If the order was paid, a compensating **negative-income refund
   transaction** (same `referenceOrderId`, category `ORDER_REFUND`) is inserted exactly
   once, keeping the ledger net-accurate.
5. **Robust order numbers** — derived from `MAX(numeric suffix of ORD-*)` instead of row
   counts, with a uniqueness re-check loop.
6. **Raw ledger integrity** — manual Use rejects quantities above remaining stock;
   auto-deduct on orders clamps at zero (never negative) and always writes a dated
   `USAGE` row, so Bought − Used always reconciles with Remaining.
7. **Backup discipline** — restore validates the app tag + version and runs atomically;
   a corrupt file never leaves a half-restored database.
8. **Smart seeding** — sample data (MMK prices, Myanmar customer names, a realistic mix
   of healthy and low-stock items) is only inserted when the product table is completely
   empty; real shop data is never wiped.

## Build & run

**Prerequisites:** Android Studio (Ladybug or newer) or a local JDK 17+ + Android SDK
(min SDK 24, target/compile SDK 36).

### Android Studio
1. **File → Open** and select this project directory.
2. Let Gradle sync (Gradle 9.3.1, AGP via `gradle/libs.versions.toml`).
3. Press **Run** ▶ on an emulator or device.

### Command line
```bash
./gradlew :app:assembleDebug        # debug APK  → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease      # release APK (R8 minified + resource shrunk)
./gradlew :app:testDebugUnitTest    # unit tests
```

Release builds are signed using `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD`
environment variables (falls back to `my-upload-key.jks` in the project root).

## Notes & limitations

- Database schema is v3 (`customer_payments` + order discount columns + product/raw
  expiry columns, via `MIGRATION_2_3`; v2 added the raw-material tables via
  `MIGRATION_1_2`. All migrations are additive, so real shop data is preserved on
  upgrade; `fallbackToDestructiveMigration` remains only as a last-resort safety net).
- Single-device, single-user: no staff accounts/PIN yet (backup/restore covers device
  loss). All data stays offline on the device.
- Receipts/slips ship in-app (text + share). Barcode scanning and staff PINs are
  planned future enhancements.

