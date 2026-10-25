package org.api.stockmarket.modules.stocks.dto;

import lombok.Getter;
import lombok.Setter;
import org.api.stockmarket.modules.stocks.enums.InvestmentStyle;
import org.api.stockmarket.modules.stocks.enums.InvestorRating;
import org.api.stockmarket.modules.stocks.enums.MarketCap;
import org.api.stockmarket.modules.stocks.enums.PriceVolatility;

@Getter
@Setter
public class StockWriteRequest {
    private String companyName;
    private String sector;
    private MarketCap marketCap;
    private InvestorRating investorRating;
    private InvestmentStyle investmentStyle;
    private PriceVolatility volatility;
    private Double price;
}
