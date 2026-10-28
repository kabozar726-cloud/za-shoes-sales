# ZA Shoes — Shop Sales Software (Android app)

Android app for the ZA Shoes shop: sales billing, stock by size, khata
(udhaar) tracking and daily/monthly profit reports.

## Features (v1, all offline)

- **Home (Dashboard):** today's sales total, today's profit, total udhaar,
  low-stock count, recent sales.
- **Nayi Sale:** pick article + size + quantity, add to cart, enter customer
  name and amount paid. Stock decreases automatically when a sale is saved.
  If paid < total, the remaining amount becomes udhaar for that customer.
- **Stock:** add new articles (name, purchase price, sale price) and add
  stock per size (e.g. 7, 8, 9, 10, 11).
- **Khata:** customers with pending balances, sale-wise detail, and
  "Wusooli" to record recovered payments.

## Data

All data is stored in a local Room database on the phone (`za_shoes.db`).
No server, no account, no internet needed. Honest limitation: if the phone
is lost or the app is uninstalled without a backup, the data is lost —
a backup/export feature is a planned v2 addition.

## Build

- AGP 8.5.2, Kotlin 1.9.24, compileSdk 34, minSdk 26, Java 17.
- Built by GitHub Actions (`gradle assembleDebug`) from this repo.
- Build files use Groovy-compatible syntax (the same setup proven by the
  Game Fast Booster build): `minifyEnabled false`, never `isMinifyEnabled`.
