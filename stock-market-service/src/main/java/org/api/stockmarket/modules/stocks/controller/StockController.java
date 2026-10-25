package org.api.stockmarket.modules.stocks.controller;

import lombok.AllArgsConstructor;
import org.api.stockmarket.core.utils.Capitalize;
import org.api.stockmarket.modules.stocks.dto.PriceRecordDTO;
import org.api.stockmarket.modules.stocks.dto.StockBasicDTO;
import org.api.stockmarket.modules.stocks.dto.StockDTOMapper;
import org.api.stockmarket.modules.stocks.dto.StockWriteRequest;
import org.api.stockmarket.modules.stocks.entity.Stock;
import org.api.stockmarket.modules.stocks.enums.MarketCap;
import org.api.stockmarket.modules.stocks.enums.StockViewType;
import org.api.stockmarket.modules.stocks.exception.StockNotFoundException;
import org.api.stockmarket.modules.stocks.service.PriceRecordService;
import org.api.stockmarket.modules.stocks.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.net.URI;

@RestController
@RequestMapping(value = "/api/v1/stocks")
@CrossOrigin(origins = "*")
@AllArgsConstructor
@SuppressWarnings("unused")
public class StockController {

    private StockService stockService;
    private final PriceRecordService priceRecordService;

    @GetMapping(value = "/{ticker}")
    public ResponseEntity<?> getIndividualStockData(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "basic") StockViewType view) {
        Stock stock = stockService.getStockByTickerSymbol(ticker);
        return ResponseEntity.ok(StockDTOMapper.map(stock, view));
    }

    @GetMapping
    public List<StockBasicDTO> getAllStockData() {
        return StockDTOMapper.map(stockService.getAllStocks(), StockViewType.BASIC);
    }

    @GetMapping(value = "/by-ticker")
    public List<StockBasicDTO> getStocksByTicker(@RequestParam List<String> tickers) {
        return StockDTOMapper.map(stockService.getStocksByTickerSymbols(tickers), StockViewType.BASIC);
    }

    @PostMapping(value = "/{ticker}")
    public ResponseEntity<StockBasicDTO> createStock(
            @PathVariable String ticker,
            @RequestBody StockWriteRequest request) {
        Stock stock = stockService.createStock(ticker, request);
        return ResponseEntity.created(URI.create("/api/v1/stocks/" + stock.getTicker()))
                .body(new StockBasicDTO(stock));
    }

    @PutMapping(value = "/{ticker}")
    public ResponseEntity<StockBasicDTO> updateStock(
            @PathVariable String ticker,
            @RequestBody StockWriteRequest request) {
        return ResponseEntity.ok(new StockBasicDTO(stockService.updateStock(ticker, request)));
    }

    @DeleteMapping(value = "/{ticker}")
    public ResponseEntity<Void> deleteStock(@PathVariable String ticker) {
        stockService.deleteStock(ticker);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/marketCap/{marketCap}")
    public List<StockBasicDTO> getAllStocksByMarketCap(@PathVariable String marketCap) {
        MarketCap cap = MarketCap.valueOf(Capitalize.capitalize(marketCap));
        return StockDTOMapper.map(stockService.getAllStocksByMarketCap(cap), StockViewType.BASIC);
    }

    @GetMapping(value = "/sector/{sector}")
    public List<StockBasicDTO> getAllStocksBySector(@PathVariable String sector) {
        return StockDTOMapper.map(stockService.getAllStocksBySector(sector), StockViewType.BASIC);
    }

    @GetMapping(value = "/price/{ticker}")
    public double getStockPrice(@PathVariable String ticker) throws StockNotFoundException {
        return stockService.getStockPriceWithTickerSymbol(ticker);
    }

    @GetMapping(value = "/random")
    public StockBasicDTO getRandomStock() {
        Stock stock = stockService.getRandomStock();
        return new StockBasicDTO(stock);
    }
}