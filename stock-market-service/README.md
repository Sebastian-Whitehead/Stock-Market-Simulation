
## :books: Table of Contents

<ol>
    <li><a href="#design-overview">Market Service Overview</a></li>
    <li><a href="#folder-structure">Folder Structure Guide</a></li>
    <li><a href="#module-architecture">Module Architecture</a></li>
    <li><a href="#data-flow">Data Flow Architecture</a></li>
    <li><a href="#stock-pricing-design">Stock Pricing Design</a></li>
</ol>    

<br/> 
<!-- -------------------------------------------------------------------------------------------------------------------------------------------- -->

<h2> 📂 Folder Structure Guide <a id="folder-structure"></a></h2>

```md
.
└── stock-market-service/src/org/api/stockmarket/
    ├── core/ -> holds interfaces, base classes, and other core features
    ├── engine/ -> the core of the app, including @Scheduled class to handle tick rate and market advancement
    └── modules/
        ├── indexfunds/
        ├── news/
        ├── simulatedmarket/ -> all in-memory market simulation interfaces
        └── stocks/
```

<br/> 
<!-- -------------------------------------------------------------------------------------------------------------------------------------------- -->


<h2> Data Flow Architecture <a id="data-flow"></a></h2>

### High-Level Market Tick Flow

<img src="documentation/MarketTickFlow.jpg" alt="market-tick-img" height="500" width="250"/>


<br/> 
<!-- -------------------------------------------------------------------------------------------------------------------------------------------- -->

## Stock Management API

The stock management endpoints are available at `/api/v1/stocks`:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/v1/stocks/by-ticker?tickers=AAPL,MSFT` | Get stocks matching the requested ticker symbols |
| `POST` | `/api/v1/stocks/{ticker}` | Create a stock |
| `PUT` | `/api/v1/stocks/{ticker}` | Replace a stock's editable company and pricing parameters |
| `DELETE` | `/api/v1/stocks/{ticker}` | Delete a stock |

Create and update requests use JSON with enum values matching their Java enum constants:

```json
{
  "companyName": "Example Corp.",
  "sector": "Technology",
  "marketCap": "MEGA",
  "investorRating": "BUY",
  "investmentStyle": "GROWTH",
  "volatility": "NORMAL",
  "price": 125.50
}
```

The `ticker` is supplied in the URL. `price` must be positive. A missing ticker returns `404`, invalid stock details or an attempt to create a duplicate ticker returns `400`, and successful deletion returns `204`.
