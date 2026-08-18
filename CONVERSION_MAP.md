# Dragon View Standalone Android Map

## Architecture

- Reference web repository: `C:\AndreiC\dragon-view`
- Standalone Android application: `C:\AndreiCopy`
- Local database: Room over Android SQLite
- Authentication: local account with PBKDF2 password hashing
- Network requirement: none
- Data relationship: mobile and website records are intentionally independent

The Android application does not connect to the website, Node.js API, MySQL,
Retrofit, or ADB reverse. Every local table is scoped by the signed-in user ID.

## Feature map

| Feature | Android implementation |
|---|---|
| Registration and login | Local Room user plus encrypted session |
| Dashboard | Aggregates local inventory, sales, and planting |
| Inventory | FIFO lots stored in Room |
| Harvests | Multi-combination atomic Room transaction |
| Inventory details | Local transaction history |
| Adjustments and regrading | Local transactional operations |
| Prices | Per-account active price history |
| Sales | Local payment validation and FIFO deduction |
| Analytics | Local daily, monthly, and annual summaries |
| Planting | Local records with calculated 45-day lifecycle |
| Scanner | UI placeholder; model artifact is still required |

## Account access

- Anyone may create a local account on the device.
- Every local account can use every implemented feature.
- Each account sees only records associated with its local user ID.
- Accounts and records do not transfer to another phone automatically.

