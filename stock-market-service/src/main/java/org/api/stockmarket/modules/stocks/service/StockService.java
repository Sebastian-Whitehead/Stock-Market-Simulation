package org.api.stockmarket.modules.stocks.service;

import org.api.stockmarket.core.exception.BadRequestException;
import org.api.stockmarket.modules.news.entity.NewsRelease;
import org.api.stockmarket.modules.news.repository.NewsReleaseRepository;
import org.api.stockmarket.modules.stocks.csv.StockCSVObject;
import org.api.stockmarket.modules.stocks.dto.StockWriteRequest;
import org.api.stockmarket.modules.stocks.entity.Stock;
import org.api.stockmarket.modules.stocks.entity.Company;
import org.api.stockmarket.modules.stocks.entity.PricingAttributes;
import org.api.stockmarket.modules.stocks.enums.MarketCap;
import org.api.stockmarket.modules.stocks.enums.InvestmentStyle;
import org.api.stockmarket.modules.stocks.enums.InvestorRating;
import org.api.stockmarket.modules.stocks.enums.PriceVolatility;
import org.api.stockmarket.modules.stocks.exception.StockNotFoundException;
import org.api.stockmarket.modules.stocks.factory.StockFactory;
import org.api.stockmarket.modules.stocks.repository.PricingModelRepository;
import org.api.stockmarket.modules.stocks.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.api.stockmarket.modules.stocks.utils.StockFactoryUtils.*;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final PricingModelRepository pricingModelRepository;
    private final NewsReleaseRepository newsReleaseRepository;
    private final StockFactory stockFactory;

    private final AtomicInteger ticksSinceSave = new AtomicInteger(0);
    private final AtomicInteger totalStocks = new AtomicInteger(0);
    private final ConcurrentHashMap<Long, Stock> stockCache = new ConcurrentHashMap<>();

    public StockService(StockRepository s, PricingModelRepository p,
                        NewsReleaseRepository newsReleaseRepository, StockFactory stockFactory){
        this.stockRepository = s;
        this.pricingModelRepository = p;
        this.newsReleaseRepository = newsReleaseRepository;
        this.stockFactory = stockFactory;
        reloadCacheFromDatabase();
    }

    public List<Stock> getAllStocks() {
        return new ArrayList<>(stockCache.values());
    }

    //this method is used to generate random news events
    public Stock getRandomStock() {
        List<Stock> stocks = getAllStocks();
        if(stocks.isEmpty()){
            throw new StockNotFoundException("Stock not found");
        }
        Collections.shuffle(stocks);
        return stocks.get(0);
    }

    public synchronized void runPriceChanges(){
        if(stockCache.isEmpty() || stockCache.size() != totalStocks.get()){
            reloadCacheFromDatabase();
        }
        stockCache.forEach((key, value) -> value.runPriceChange());

        if(ticksSinceSave.incrementAndGet() > 9){
            pricingModelRepository.saveAll(stockCache.values().stream()
                    .map(Stock::getPricingModel).toList());
            ticksSinceSave.set(0);
        }
    }

    public List<Stock> getAllStocksByMarketCap(MarketCap marketCap) {
        return getAllStocks().stream()
                .filter(stock -> stock.getCompany().getMarketCap()
                        .equals(marketCap)).collect(Collectors.toList());
    }

    public List<Stock> getAllStocksBySector(String sector) {
        return getAllStocks().stream()
                .filter(stock -> stock.getCompany().getSector()
                        .equalsIgnoreCase(sector)).collect(Collectors.toList());
    }

    public Stock getStockByTickerSymbol(String ticker) {
        return getAllStocks().stream()
                .filter(stock -> stock.getTicker().equalsIgnoreCase(ticker))
                .findFirst().orElseThrow(() ->
                        new StockNotFoundException("No stock with ticker symbol " + ticker + " exists"));
    }

    public List<Stock> getStocksByTickerSymbols(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            throw new BadRequestException("At least one ticker is required");
        }
        return tickers.stream().map(this::getStockByTickerSymbol).toList();
    }

    public double getStockPriceWithTickerSymbol(String ticker) {
        if (!stockTickerExists(ticker)) {
            throw new StockNotFoundException("No stock with ticker symbol " + ticker + " exists");
        }
        return getStockByTickerSymbol(ticker).getPricingModel().getPrice().doubleValue();
    }

    @Transactional
    public synchronized Stock createStock(String ticker, StockWriteRequest request) {
        String normalizedTicker = normalizeTicker(ticker);
        validateRequest(request);
        if (stockTickerExists(normalizedTicker)) {
            throw new BadRequestException("A stock with ticker symbol " + normalizedTicker + " already exists");
        }
        StockCSVObject stockData = new StockCSVObject(
                normalizedTicker,
                request.getCompanyName().trim(),
                request.getMarketCap(),
                request.getSector().trim(),
                request.getVolatility(),
                request.getInvestorRating(),
                request.getInvestmentStyle(),
                request.getPrice());
        Stock savedStock = stockRepository.save(stockFactory.create(stockData));
        reloadCacheFromDatabase();
        return savedStock;
    }

    @Transactional
    public synchronized Stock updateStock(String ticker, StockWriteRequest request) {
        validateRequest(request);
        Stock stock = findManagedStockByTicker(ticker);
        Company company = stock.getCompany();
        company.setCompanyName(request.getCompanyName().trim());
        company.setSector(request.getSector().trim());
        company.setMarketCap(request.getMarketCap());
        company.setInvestorRating(request.getInvestorRating());
        company.setInvestmentStyle(request.getInvestmentStyle());

        PricingAttributes attributes = stock.getPricingModel().getAttributes();
        attributes.setVolatility(request.getVolatility());
        stock.getPricingModel().setPrice(java.math.BigDecimal.valueOf(request.getPrice()));
        attributes.setInvestorConfidenceFactor(defaultInvestorConfidence(company));
        attributes.setInnovationFactor(defaultInnovationFactor(company));
        attributes.setLiquidityFactor(defaultLiquidity(company));
        attributes.setTradingDemandFactor(defaultTradingDemand(company));

        Stock savedStock = stockRepository.save(stock);
        reloadCacheFromDatabase();
        return savedStock;
    }

    @Transactional
    public synchronized void deleteStock(String ticker) {
        Stock stock = findManagedStockByTicker(ticker);
        List<NewsRelease> linkedReleases = new ArrayList<>(stock.getNewsReleases());
        linkedReleases.forEach(release -> release.getStocks().remove(stock));
        newsReleaseRepository.saveAll(linkedReleases);
        stock.getNewsReleases().clear();
        stockRepository.delete(stock);
        reloadCacheFromDatabase();
    }

    public int findStockRowCount() {
        return (int) stockRepository.count();
    }

    /*
    This method does NOT override or delete any stocks. We only add stocks that do not
    show up in the database already. A separate method may be needed if we want to delete/modify stocks
    upon startup.
     */
    public void saveDefaultStockToDatabase(List<Stock> defaultStocks) {
        stockRepository.saveAll(defaultStocks);
        reloadCacheFromDatabase();
    }

    public boolean stockTickerExists(String ticker){
        return stockCache.values().stream()
                .anyMatch(stock -> stock.getTicker().equalsIgnoreCase(ticker));
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new BadRequestException("Ticker is required");
        }
        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        if (!normalizedTicker.matches("[A-Z0-9.-]+")) {
            throw new BadRequestException("Ticker may contain only letters, numbers, periods, and hyphens");
        }
        return normalizedTicker;
    }

    private Stock findManagedStockByTicker(String ticker) {
        Stock cachedStock = getStockByTickerSymbol(ticker);
        return stockRepository.findById(cachedStock.getId())
                .orElseThrow(() -> new StockNotFoundException(
                        "No stock with ticker symbol " + ticker + " exists"));
    }

    private void validateRequest(StockWriteRequest request) {
        if (request == null
                || request.getCompanyName() == null || request.getCompanyName().isBlank()
                || request.getSector() == null || request.getSector().isBlank()
                || request.getMarketCap() == null || request.getMarketCap() == MarketCap.UNKNOWN
                || request.getInvestorRating() == null || request.getInvestorRating() == InvestorRating.UNKNOWN
                || request.getInvestmentStyle() == null
                || request.getInvestmentStyle() == InvestmentStyle.UNKNOWN
                || request.getVolatility() == null || request.getVolatility() == PriceVolatility.UNKNOWN
                || request.getPrice() == null || !Double.isFinite(request.getPrice())
                || request.getPrice() <= 0) {
            throw new BadRequestException("Stock details are invalid; provide companyName, sector, marketCap, "
                    + "investorRating, investmentStyle, volatility, and a positive price");
        }
    }

    public void reloadCacheFromDatabase(){
        List<Stock> stocks = stockRepository.findAll();
        stockCache.clear();
        stockCache.putAll(stocks.stream().collect(Collectors.toMap(Stock::getId,st -> st)));
        totalStocks.set(stocks.size());
    }

    public void updatePricingAttributesAfterNews(List<NewsRelease> releases){

    }
}
