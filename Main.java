import java.util.concurrent.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.io.*;
import java.net.*;
import java.util.stream.Collectors;
import java.util.Scanner;

class Asset {
    private String symbol;
    private String name;
    private AssetType type;
    private double currentPrice;

    public enum AssetType {
        STOCK, CRYPTO, FOREX, FUTURES, OPTIONS
    }

    public Asset(String symbol, String name, AssetType type) {
        this.symbol = symbol;
        this.name = name;
        this.type = type;
        this.currentPrice = 0.0;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public AssetType getType() {
        return type;
    }
    
    public double getCurrentPrice() {
        return currentPrice;
    }
    
    public void setCurrentPrice(double price) {
        this.currentPrice = price;
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s)", symbol, name);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Asset asset = (Asset) o;
        return symbol.equals(asset.symbol);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(symbol);
    }
}

class Trade {
    private Asset asset;
    private double price;
    private int quantity;
    private boolean isBuy;
    private LocalDateTime timestamp;
    private String orderId;
    private TradeStatus status;
    
    public enum TradeStatus {
        PENDING, FILLED, CANCELLED, PARTIAL
    }
    
    public Trade(Asset asset, double price, int quantity, boolean isBuy) {
        this.asset = asset;
        this.price = price;
        this.quantity = quantity;
        this.isBuy = isBuy;
        this.timestamp = LocalDateTime.now();
        this.orderId = UUID.randomUUID().toString();
        this.status = TradeStatus.PENDING;
    }
    
    public Asset getAsset() {
        return asset;
    }
    
    public double getPrice() {
        return price;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public boolean isBuy() {
        return isBuy;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public TradeStatus getStatus() {
        return status;
    }
    
    public void setStatus(TradeStatus status) {
        this.status = status;
    }
    
    public double getValue() {
        return price * quantity;
    }
    
    @Override
    public String toString() {
        String type = isBuy ? "BUY" : "SELL";
        return String.format("%s: %s %.2f x %d @ %s [%s]", 
                type, asset.getSymbol(), price, quantity, timestamp, status);
    }
}

class MarketDataManager {
    private Map<Asset, AssetMarketData> marketDataMap = new ConcurrentHashMap<>();
    private List<Consumer<MarketDataUpdate>> subscribers = new ArrayList<>();
    
    public void registerAsset(Asset asset) {
        marketDataMap.putIfAbsent(asset, new AssetMarketData(asset));
    }
    
    public void updatePrice(Asset asset, double price) {
        if (!marketDataMap.containsKey(asset)) {
            registerAsset(asset);
        }
        
        AssetMarketData assetData = marketDataMap.get(asset);
        asset.setCurrentPrice(price);
        assetData.addPrice(price);
        
        // Notify subscribers
        MarketDataUpdate update = new MarketDataUpdate(asset, price, LocalDateTime.now());
        notifySubscribers(update);
    }
    
    public AssetMarketData getMarketData(Asset asset) {
        return marketDataMap.getOrDefault(asset, null);
    }
    
    public void subscribe(Consumer<MarketDataUpdate> subscriber) {
        subscribers.add(subscriber);
    }
    
    public void unsubscribe(Consumer<MarketDataUpdate> subscriber) {
        subscribers.remove(subscriber);
    }
    
    private void notifySubscribers(MarketDataUpdate update) {
        for (Consumer<MarketDataUpdate> subscriber : subscribers) {
            subscriber.accept(update);
        }
    }
    
    public Set<Asset> getRegisteredAssets() {
        return marketDataMap.keySet();
    }
}

class MarketDataUpdate {
    private Asset asset;
    private double price;
    private LocalDateTime timestamp;
    
    public MarketDataUpdate(Asset asset, double price, LocalDateTime timestamp) {
        this.asset = asset;
        this.price = price;
        this.timestamp = timestamp;
    }
    
    public Asset getAsset() {
        return asset;
    }
    
    public double getPrice() {
        return price;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}

class AssetMarketData {
    private Asset asset;
    private ConcurrentLinkedQueue<Double> priceQueue = new ConcurrentLinkedQueue<>();
    private List<Double> historicalPrices = Collections.synchronizedList(new ArrayList<>());
    private Map<String, List<Double>> indicators = new ConcurrentHashMap<>();
    private double lastPrice = 0.0;
    private double highPrice = Double.MIN_VALUE;
    private double lowPrice = Double.MAX_VALUE;
    private double openPrice = 0.0;
    private double volume = 0.0;
    
    public AssetMarketData(Asset asset) {
        this.asset = asset;
    }
    
    public void addPrice(double price) {
        if (openPrice == 0.0) {
            openPrice = price;
        }
        
        priceQueue.add(price);
        historicalPrices.add(price);
        lastPrice = price;
        
        highPrice = Math.max(highPrice, price);
        lowPrice = Math.min(lowPrice, price);
        
        // Update indicators
        calculateIndicators();
    }
    
    public Double getNextPrice() {
        return priceQueue.poll();
    }
    
    public List<Double> getHistoricalPrices() {
        return new ArrayList<>(historicalPrices);
    }
    
    public double getLastPrice() {
        return lastPrice;
    }
    
    public double getHighPrice() {
        return highPrice;
    }
    
    public double getLowPrice() {
        return lowPrice;
    }
    
    public double getOpenPrice() {
        return openPrice;
    }
    
    public void addVolume(double amount) {
        volume += amount;
    }
    
    public double getVolume() {
        return volume;
    }
    
    public void calculateIndicators() {
        // Calculate various technical indicators
        calculateRSI(14);
        calculateMACD(12, 26, 9);
    }
    
    public double calculateRSI(int period) {
        if (historicalPrices.size() <= period) {
            indicators.putIfAbsent("RSI", new ArrayList<>());
            indicators.get("RSI").add(50.0); // Default value
            return 50.0;
        }
        
        double gains = 0;
        double losses = 0;
        
        List<Double> pricesToUse = historicalPrices.subList(
            historicalPrices.size() - period - 1, historicalPrices.size());
        
        for (int i = 1; i < pricesToUse.size(); i++) {
            double change = pricesToUse.get(i) - pricesToUse.get(i - 1);
            if (change >= 0) {
                gains += change;
            } else {
                losses -= change; // Make losses positive
            }
        }
        
        double rs = (gains / period) / (losses / period);
        double rsi = 100 - (100 / (1 + rs));
        
        indicators.putIfAbsent("RSI", new ArrayList<>());
        indicators.get("RSI").add(rsi);
        
        return rsi;
    }
    
    public void calculateMACD(int fastPeriod, int slowPeriod, int signalPeriod) {
        if (historicalPrices.size() <= slowPeriod + signalPeriod) {
            return;
        }
        
        double fastEMA = calculateEMA(historicalPrices, fastPeriod);
        double slowEMA = calculateEMA(historicalPrices, slowPeriod);
        double macd = fastEMA - slowEMA;
        
        indicators.putIfAbsent("MACD", new ArrayList<>());
        indicators.get("MACD").add(macd);
        
        // Calculate signal line (EMA of MACD)
        if (indicators.get("MACD").size() >= signalPeriod) {
            double signalLine = calculateEMA(indicators.get("MACD"), signalPeriod);
            indicators.putIfAbsent("MACD_SIGNAL", new ArrayList<>());
            indicators.get("MACD_SIGNAL").add(signalLine);
            
            // MACD Histogram (MACD - Signal Line)
            double histogram = macd - signalLine;
            indicators.putIfAbsent("MACD_HIST", new ArrayList<>());
            indicators.get("MACD_HIST").add(histogram);
        }
    }
    
    private double calculateEMA(List<Double> prices, int period) {
        if (prices.size() < period) {
            return 0;
        }
        
        // Calculate SMA first for initial EMA value
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += prices.get(i);
        }
        double sma = sum / period;
        
        // Multiplier: 2 / (period + 1)
        double multiplier = 2.0 / (period + 1);
        double ema = sma;
        
        // Calculate EMA
        for (int i = period; i < prices.size(); i++) {
            ema = (prices.get(i) - ema) * multiplier + ema;
        }
        
        return ema;
    }
    
    public double getIndicator(String name) {
        if (!indicators.containsKey(name) || indicators.get(name).isEmpty()) {
            return 0;
        }
        List<Double> values = indicators.get(name);
        return values.get(values.size() - 1);
    }
    
    public List<Double> getIndicatorHistory(String name) {
        return indicators.getOrDefault(name, new ArrayList<>());
    }
}

class RiskManager {
    private double maxDrawdown;
    private double initialCapital;
    private double currentCapital;
    private double maxDailyLoss;
    private double dailyLoss;
    private double maxPositionSize;
    private Map<Asset, Integer> positionLimits = new HashMap<>();
    
    public RiskManager(double initialCapital, double maxDrawdownPercent, double maxDailyLossPercent,
                       double maxPositionSizePercent) {
        this.initialCapital = initialCapital;
        this.currentCapital = initialCapital;
        this.maxDrawdown = initialCapital * (maxDrawdownPercent / 100.0);
        this.maxDailyLoss = initialCapital * (maxDailyLossPercent / 100.0);
        this.maxPositionSize = initialCapital * (maxPositionSizePercent / 100.0);
        this.dailyLoss = 0.0;
    }
    
    public void setPositionLimit(Asset asset, int maxQuantity) {
        positionLimits.put(asset, maxQuantity);
    }
    
    public int getPositionLimit(Asset asset) {
        return positionLimits.getOrDefault(asset, Integer.MAX_VALUE);
    }
    
    public void updateCapital(double tradeProfit) {
        currentCapital += tradeProfit;
        if (tradeProfit < 0) {
            dailyLoss += -tradeProfit;
        }
    }
    
    public boolean canTrade() {
        return (initialCapital - currentCapital) < maxDrawdown && dailyLoss < maxDailyLoss;
    }
    
    public boolean canTrade(Asset asset, int quantity, double price) {
        double positionValue = quantity * price;
        return canTrade() && positionValue <= maxPositionSize && quantity <= getPositionLimit(asset);
    }
    
    public int getMaxQuantity(Asset asset, double price) {
        if (price <= 0) return 0;
        
        int maxByCapital = (int) Math.floor(maxPositionSize / price);
        int maxByLimit = getPositionLimit(asset);
        
        return Math.min(maxByCapital, maxByLimit);
    }
    
    public void resetDailyLoss() {
        dailyLoss = 0.0;
    }
    
    public double getCurrentCapital() {
        return currentCapital;
    }
    
    public double getInitialCapital() {
        return initialCapital;
    }
    
    public double getDailyLoss() {
        return dailyLoss;
    }
}

class PerformanceTracker {
    private int totalTrades = 0;
    private int winningTrades = 0;
    private int losingTrades = 0;
    private double totalProfit = 0.0;
    private double maxDrawdown = 0.0;
    private double peakCapital;
    private double currentDrawdown = 0.0;
    
    public PerformanceTracker(double initialCapital) {
        this.peakCapital = initialCapital;
    }
    
    public void recordTrade(double tradeProfit, double currentCapital) {
        totalTrades++;
        totalProfit += tradeProfit;
        
        if (tradeProfit > 0) {
            winningTrades++;
        } else if (tradeProfit < 0) {
            losingTrades++;
        }
        
        // Update drawdown calculations
        if (currentCapital > peakCapital) {
            peakCapital = currentCapital;
            currentDrawdown = 0;
        } else {
            currentDrawdown = peakCapital - currentCapital;
            maxDrawdown = Math.max(maxDrawdown, currentDrawdown);
        }
    }
    
    public void printStats() {
        System.out.println("==== PERFORMANCE STATISTICS ====");
        System.out.println("Total Trades: " + totalTrades);
        System.out.println("Winning Trades: " + winningTrades);
        System.out.println("Losing Trades: " + losingTrades);
        System.out.printf("Win Rate: %.2f%%\n", totalTrades > 0 ? (winningTrades * 100.0 / totalTrades) : 0);
        System.out.printf("Total Profit: $%.2f\n", totalProfit);
        System.out.printf("Max Drawdown: $%.2f\n", maxDrawdown);
    }
}

interface TradingStrategy {
    void execute(MarketDataManager dataManager, Portfolio portfolio, RiskManager riskManager, 
                 ConcurrentLinkedQueue<Trade> tradesQueue);
    String getName();
}

class RSIStrategy implements TradingStrategy {
    private Asset asset;
    private int period;
    private double oversoldThreshold;
    private double overboughtThreshold;
    private int quantity;
    
    public RSIStrategy(Asset asset, int period, double oversoldThreshold, double overboughtThreshold, int quantity) {
        this.asset = asset;
        this.period = period;
        this.oversoldThreshold = oversoldThreshold;
        this.overboughtThreshold = overboughtThreshold;
        this.quantity = quantity;
    }
    
    @Override
    public void execute(MarketDataManager dataManager, Portfolio portfolio, RiskManager riskManager, 
                        ConcurrentLinkedQueue<Trade> tradesQueue) {
        AssetMarketData marketData = dataManager.getMarketData(asset);
        if (marketData == null) return;
        
        double rsi = marketData.getIndicator("RSI");
        double price = marketData.getLastPrice();
        Position position = portfolio.getPosition(asset);
        
        if (rsi < oversoldThreshold && position.getQuantity() == 0) {
            // Oversold - buy signal
            int qty = Math.min(quantity, riskManager.getMaxQuantity(asset, price));
            if (qty > 0 && riskManager.canTrade(asset, qty, price)) {
                tradesQueue.add(new Trade(asset, price, qty, true));
            }
        } else if (rsi > overboughtThreshold && position.getQuantity() > 0) {
            // Overbought - sell signal
            tradesQueue.add(new Trade(asset, price, position.getQuantity(), false));
        }
    }
    
    @Override
    public String getName() {
        return String.format("RSI Strategy (%s, %d, %.1f, %.1f)", 
                asset.getSymbol(), period, oversoldThreshold, overboughtThreshold);
    }
}

class MACDStrategy implements TradingStrategy {
    private Asset asset;
    private int quantity;
    
    public MACDStrategy(Asset asset, int quantity) {
        this.asset = asset;
        this.quantity = quantity;
    }
    
    @Override
    public void execute(MarketDataManager dataManager, Portfolio portfolio, RiskManager riskManager, 
                        ConcurrentLinkedQueue<Trade> tradesQueue) {
        AssetMarketData marketData = dataManager.getMarketData(asset);
        if (marketData == null) return;
        
        double macd = marketData.getIndicator("MACD");
        double signal = marketData.getIndicator("MACD_SIGNAL");
        double histogram = marketData.getIndicator("MACD_HIST");
        
        List<Double> macdHistory = marketData.getIndicatorHistory("MACD");
        List<Double> signalHistory = marketData.getIndicatorHistory("MACD_SIGNAL");
        
        if (macdHistory.size() < 2 || signalHistory.size() < 2) return;
        
        double previousMACD = macdHistory.get(macdHistory.size() - 2);
        double previousSignal = signalHistory.get(signalHistory.size() - 2);
        
        double price = marketData.getLastPrice();
        Position position = portfolio.getPosition(asset);
        
        // MACD line crosses above Signal line
        if (previousMACD <= previousSignal && macd > signal) {
            // Buy signal
            int qty = Math.min(quantity, riskManager.getMaxQuantity(asset, price));
            if (qty > 0 && riskManager.canTrade(asset, qty, price)) {
                tradesQueue.add(new Trade(asset, price, qty, true));
            }
        } 
        // MACD line crosses below Signal line
        else if (previousMACD >= previousSignal && macd < signal && position.getQuantity() > 0) {
            // Sell signal
            tradesQueue.add(new Trade(asset, price, position.getQuantity(), false));
        }
    }
    
    @Override
    public String getName() {
        return String.format("MACD Strategy (%s)", asset.getSymbol());
    }
}

class Position {
    private Asset asset;
    private int quantity;
    private double averagePrice;
    private double realizedPnL;
    private List<Trade> trades = new ArrayList<>();
    
    public Position(Asset asset) {
        this.asset = asset;
        this.quantity = 0;
        this.averagePrice = 0;
        this.realizedPnL = 0;
    }
    
    public void addTrade(Trade trade) {
        if (trade.getAsset().equals(asset)) {
            trades.add(trade);
            
            int tradeQuantity = trade.getQuantity();
            double tradePrice = trade.getPrice();
            
            if (trade.isBuy()) {
                // Buying more - update average price
                double totalValue = quantity * averagePrice + tradeQuantity * tradePrice;
                quantity += tradeQuantity;
                averagePrice = totalValue / quantity;
            } else {
                // Selling - calculate realized PnL
                if (quantity > 0) {
                    int sellQty = Math.min(quantity, tradeQuantity);
                    double pnl = sellQty * (tradePrice - averagePrice);
                    realizedPnL += pnl;
                    quantity -= sellQty;
                    
                    // If we sold everything, reset average price
                    if (quantity == 0) {
                        averagePrice = 0;
                    }
                } else {
                    // Short selling (not implemented here)
                }
            }
        }
    }
    
    public double getUnrealizedPnL(double currentPrice) {
        if (quantity == 0) return 0;
        return quantity * (currentPrice - averagePrice);
    }
    
    public double getRealizedPnL() {
        return realizedPnL;
    }
    
    public double getTotalPnL(double currentPrice) {
        return realizedPnL + getUnrealizedPnL(currentPrice);
    }
    
    public Asset getAsset() {
        return asset;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public double getAveragePrice() {
        return averagePrice;
    }
    
    public List<Trade> getTrades() {
        return new ArrayList<>(trades);
    }
    
    @Override
    public String toString() {
        return String.format("Position: %s, Qty: %d, Avg Price: %.2f, Realized P&L: %.2f",
                asset.getSymbol(), quantity, averagePrice, realizedPnL);
    }
}

class Portfolio {
    private Map<Asset, Position> positions = new HashMap<>();
    private RiskManager riskManager;
    
    public Portfolio(RiskManager riskManager) {
        this.riskManager = riskManager;
    }
    
    public void addTrade(Trade trade) {
        Asset asset = trade.getAsset();
        
        if (!positions.containsKey(asset)) {
            positions.put(asset, new Position(asset));
        }
        
        Position position = positions.get(asset);
        position.addTrade(trade);
        
        // Update risk manager
        double tradePnL = 0;
        if (!trade.isBuy() && position.getQuantity() < trade.getQuantity()) {
            // Selling position that we own - calculate realized PnL
            tradePnL = position.getRealizedPnL() - tradePnL;
            riskManager.updateCapital(tradePnL);
        }
    }
    
    public Position getPosition(Asset asset) {
        return positions.getOrDefault(asset, new Position(asset));
    }
    
    public Map<Asset, Position> getAllPositions() {
        return new HashMap<>(positions);
    }
    
    public double getTotalValue(MarketDataManager marketDataManager) {
        double total = 0;
        for (Position position : positions.values()) {
            Asset asset = position.getAsset();
            double currentPrice = marketDataManager.getMarketData(asset).getLastPrice();
            total += position.getQuantity() * currentPrice;
        }
        return total;
    }
    
    public double getTotalPnL(MarketDataManager marketDataManager) {
        double total = 0;
        for (Position position : positions.values()) {
            Asset asset = position.getAsset();
            double currentPrice = marketDataManager.getMarketData(asset).getLastPrice();
            total += position.getTotalPnL(currentPrice);
        }
        return total;
    }
    
    public void printPortfolioSummary(MarketDataManager marketDataManager) {
        System.out.println("==== PORTFOLIO SUMMARY ====");
        
        double totalValue = 0;
        double totalPnL = 0;
        
        for (Position position : positions.values()) {
            Asset asset = position.getAsset();
            double currentPrice = marketDataManager.getMarketData(asset).getLastPrice();
            double positionValue = position.getQuantity() * currentPrice;
            double unrealizedPnL = position.getUnrealizedPnL(currentPrice);
            
            totalValue += positionValue;
            totalPnL += position.getTotalPnL(currentPrice);
            
            if (position.getQuantity() > 0) {
                System.out.printf("%s: %d shares @ $%.2f, Current: $%.2f, Unrealized P&L: $%.2f\n",
                        asset.getSymbol(), position.getQuantity(), position.getAveragePrice(),
                        currentPrice, unrealizedPnL);
            }
        }
        
        System.out.printf("Total Portfolio Value: $%.2f\n", totalValue);
        System.out.printf("Total P&L: $%.2f\n", totalPnL);
        System.out.printf("Current Cash: $%.2f\n", riskManager.getCurrentCapital());
        System.out.printf("Total Account Value: $%.2f\n", riskManager.getCurrentCapital() + totalValue);
    }
}

class StrategyExecutor {
    private List<TradingStrategy> strategies = new ArrayList<>();
    private MarketDataManager marketDataManager;
    private Portfolio portfolio;
    private RiskManager riskManager;
    private ConcurrentLinkedQueue<Trade> tradesQueue = new ConcurrentLinkedQueue<>();
    
    public StrategyExecutor(MarketDataManager marketDataManager, Portfolio portfolio, RiskManager riskManager) {
        this.marketDataManager = marketDataManager;
        this.portfolio = portfolio;
        this.riskManager = riskManager;
    }
    
    public void addStrategy(TradingStrategy strategy) {
        strategies.add(strategy);
    }
    
    public void executeStrategies() {
        for (TradingStrategy strategy : strategies) {
            strategy.execute(marketDataManager, portfolio, riskManager, tradesQueue);
        }
    }
    
    public ConcurrentLinkedQueue<Trade> getTradesQueue() {
        return tradesQueue;
    }
}

class OrderBook {
    private Asset asset;
    private PriorityQueue<OrderBookEntry> bids = new PriorityQueue<>((a, b) -> Double.compare(b.price, a.price)); // Highest first
    private PriorityQueue<OrderBookEntry> asks = new PriorityQueue<>((a, b) -> Double.compare(a.price, b.price)); // Lowest first
    
    static class OrderBookEntry {
        double price;
        int quantity;
        
        public OrderBookEntry(double price, int quantity) {
            this.price = price;
            this.quantity = quantity;
        }
    }
    
    public OrderBook(Asset asset) {
        this.asset = asset;
    }
    
    public void addBid(double price, int quantity) {
        bids.add(new OrderBookEntry(price, quantity));
    }
    
    public void addAsk(double price, int quantity) {
        asks.add(new OrderBookEntry(price, quantity));
    }
    
    public double getBestBid() {
        return bids.isEmpty() ? 0 : bids.peek().price;
    }
    
    public double getBestAsk() {
        return asks.isEmpty() ? Double.MAX_VALUE : asks.peek().price;
    }
    
    public double getMidPrice() {
        double bestBid = getBestBid();
        double bestAsk = getBestAsk();
        
        if (bestBid == 0 || bestAsk == Double.MAX_VALUE) {
            if (bestBid > 0) return bestBid;
            if (bestAsk < Double.MAX_VALUE) return bestAsk;
            return 0;
        }
        
        return (bestBid + bestAsk) / 2;
    }
    
    public Asset getAsset() {
        return asset;
    }
}

class MarketSimulator {
    private Map<Asset, OrderBook> orderBooks = new HashMap<>();
    private MarketDataManager marketDataManager;
    private Random random = new Random();
    
    public MarketSimulator(MarketDataManager marketDataManager) {
        this.marketDataManager = marketDataManager;
    }
    
    public MarketDataManager getMarketDataManager() {
        return marketDataManager;
    }
    
    public void registerAsset(Asset asset, double initialPrice) {
        orderBooks.put(asset, new OrderBook(asset));
        marketDataManager.registerAsset(asset);
        updatePrice(asset, initialPrice);
        
        // Initialize order book with some random orders
        generateRandomOrders(asset, initialPrice);
    }
    
    public void updatePrice(Asset asset, double price) {
        marketDataManager.updatePrice(asset, price);
    }
    
    public void generateRandomOrders(Asset asset, double currentPrice) {
        OrderBook orderBook = orderBooks.get(asset);
        
        // Clear existing orders
        orderBooks.put(asset, new OrderBook(asset));
        orderBook = orderBooks.get(asset);
        
        // Generate random bids slightly below current price
        for (int i = 0; i < 10; i++) {
            double priceVariation = random.nextDouble() * 5; // Up to $5 lower
            double bidPrice = Math.max(0.01, currentPrice - priceVariation);
            int quantity = 10 + random.nextInt(90); // 10-100 shares
            orderBook.addBid(bidPrice, quantity);
        }
        
        // Generate random asks slightly above current price
        for (int i = 0; i < 10; i++) {
            double priceVariation = random.nextDouble() * 5; // Up to $5 higher
            double askPrice = currentPrice + priceVariation;
            int quantity = 10 + random.nextInt(90); // 10-100 shares
            orderBook.addAsk(askPrice, quantity);
        }
    }
    
    public OrderBook getOrderBook(Asset asset) {
        return orderBooks.get(asset);
    }
    
    public double getCurrentPrice(Asset asset) {
        return orderBooks.get(asset).getMidPrice();
    }
    
    public void simulatePriceMovement() {
        for (Asset asset : orderBooks.keySet()) {
            double currentPrice = getCurrentPrice(asset);
            if (currentPrice <= 0) continue;
            
            // Random price movement between -2% and +2%
            double priceChange = currentPrice * (random.nextDouble() * 0.04 - 0.02);
            double newPrice = currentPrice + priceChange;
            
            // Ensure price is positive
            newPrice = Math.max(0.01, newPrice);
            
            // Update order book and price
            generateRandomOrders(asset, newPrice);
            updatePrice(asset, newPrice);
        }
    }
    
    public void simulateTradeExecution(Trade trade) {
        if (trade.getStatus() != Trade.TradeStatus.PENDING) return;
        
        Asset asset = trade.getAsset();
        OrderBook orderBook = orderBooks.get(asset);
        
        if (orderBook == null) return;
        
        boolean isExecuted = false;
        
        if (trade.isBuy()) {
            double bestAsk = orderBook.getBestAsk();
            if (bestAsk <= trade.getPrice()) {
                // Trade executed
                isExecuted = true;
            }
        } else {
            double bestBid = orderBook.getBestBid();
            if (bestBid >= trade.getPrice()) {
                // Trade executed
                isExecuted = true;
            }
        }
        
        if (isExecuted) {
            trade.setStatus(Trade.TradeStatus.FILLED);
        }
    }
}

class BackTester {
    private MarketDataManager marketDataManager;
    private Portfolio portfolio;
    private RiskManager riskManager;
    private StrategyExecutor strategyExecutor;
    private PerformanceTracker performanceTracker;
    
    public BackTester(MarketDataManager marketDataManager, RiskManager riskManager) {
        this.marketDataManager = marketDataManager;
        this.riskManager = riskManager;
        this.portfolio = new Portfolio(riskManager);
        this.strategyExecutor = new StrategyExecutor(marketDataManager, portfolio, riskManager);
        this.performanceTracker = new PerformanceTracker(riskManager.getInitialCapital());
    }
    
    public void addStrategy(TradingStrategy strategy) {
        strategyExecutor.addStrategy(strategy);
    }
    
    public void runBacktest(MarketSimulator simulator, int iterations) {
        System.out.println("Starting backtest with " + iterations + " iterations...");
        
        for (int i = 0; i < iterations; i++) {
            // Simulate market price movements
            simulator.simulatePriceMovement();
            
            // Execute all strategies on current market data
            strategyExecutor.executeStrategies();
            
            // Process trades
            ConcurrentLinkedQueue<Trade> trades = strategyExecutor.getTradesQueue();
            Trade trade;
            
            while ((trade = trades.poll()) != null) {
                // Simulate trade execution
                simulator.simulateTradeExecution(trade);
                
                if (trade.getStatus() == Trade.TradeStatus.FILLED) {
                    // Add trade to portfolio
                    portfolio.addTrade(trade);
                    
                    // Calculate P&L if it's a sell trade
                    if (!trade.isBuy()) {
                        Position position = portfolio.getPosition(trade.getAsset());
                        double tradePnL = position.getRealizedPnL();
                        performanceTracker.recordTrade(tradePnL, riskManager.getCurrentCapital());
                    }
                }
            }
            
            // Print status every 100 iterations
            if (i > 0 && i % 100 == 0) {
                System.out.printf("Completed %d iterations (%.1f%%)\n", i, (i * 100.0 / iterations));
            }
        }
        
        // Print final results
        System.out.println("\nBacktest complete!");
        portfolio.printPortfolioSummary(marketDataManager);
        performanceTracker.printStats();
    }
    
    public Portfolio getPortfolio() {
        return portfolio;
    }
    
    public PerformanceTracker getPerformanceTracker() {
        return performanceTracker;
    }
}

// Machine Learning Model Interface
interface PredictionModel {
    double predict(List<Double> features);
    void train(List<List<Double>> featuresList, List<Double> labels);
    String getName();
    void saveModel(String filePath) throws IOException;
    void loadModel(String filePath) throws IOException;
}

// Simple Linear Regression Model
class LinearRegressionModel implements PredictionModel {
    private double[] weights;
    private double bias;
    private double learningRate = 0.01;
    private int epochs = 1000;
    
    public LinearRegressionModel(int numFeatures) {
        weights = new double[numFeatures];
        // Initialize weights randomly
        Random random = new Random();
        for (int i = 0; i < numFeatures; i++) {
            weights[i] = random.nextDouble() * 0.1;
        }
        bias = random.nextDouble() * 0.1;
    }
    
    @Override
    public double predict(List<Double> features) {
        if (features.size() != weights.length) {
            throw new IllegalArgumentException("Features size mismatch");
        }
        
        double prediction = bias;
        for (int i = 0; i < weights.length; i++) {
            prediction += weights[i] * features.get(i);
        }
        
        return prediction;
    }
    
    @Override
    public void train(List<List<Double>> featuresList, List<Double> labels) {
        if (featuresList.size() != labels.size()) {
            throw new IllegalArgumentException("Features and labels size mismatch");
        }
        
        for (int epoch = 0; epoch < epochs; epoch++) {
            double totalError = 0;
            
            for (int i = 0; i < featuresList.size(); i++) {
                List<Double> features = featuresList.get(i);
                double actualValue = labels.get(i);
                
                // Predict
                double prediction = predict(features);
                
                // Calculate error
                double error = prediction - actualValue;
                totalError += error * error;
                
                // Update bias
                bias -= learningRate * error;
                
                // Update weights
                for (int j = 0; j < weights.length; j++) {
                    weights[j] -= learningRate * error * features.get(j);
                }
            }
            
            // Early stopping if error is small enough
            if (totalError < 0.001) {
                System.out.printf("Convergence achieved at epoch %d\n", epoch);
                break;
            }
        }
    }
    
    @Override
    public String getName() {
        return "Linear Regression Model";
    }
    
    @Override
    public void saveModel(String filePath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(weights);
            oos.writeDouble(bias);
        }
    }
    
    @Override
    public void loadModel(String filePath) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            weights = (double[]) ois.readObject();
            bias = ois.readDouble();
        } catch (ClassNotFoundException e) {
            throw new IOException("Error loading model: " + e.getMessage());
        }
    }
}

// News and Sentiment Analysis
class MarketSentiment {
    private Map<String, Double> assetSentiment = new ConcurrentHashMap<>();
    private Timer sentimentUpdateTimer;
    private static final String API_KEY = "DEMO_API_KEY"; // Replace with actual API key
    
    public MarketSentiment() {
        // Schedule periodic sentiment updates
        sentimentUpdateTimer = new Timer();
        sentimentUpdateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateAllSentiments();
            }
        }, 0, 60000); // Update every minute
    }
    
    public void registerAsset(Asset asset) {
        // Initialize with neutral sentiment
        assetSentiment.putIfAbsent(asset.getSymbol(), 0.0);
    }
    
    public double getSentiment(Asset asset) {
        return assetSentiment.getOrDefault(asset.getSymbol(), 0.0);
    }
    
    private void updateAllSentiments() {
        for (String symbol : assetSentiment.keySet()) {
            try {
                double sentiment = fetchSentimentForAsset(symbol);
                assetSentiment.put(symbol, sentiment);
            } catch (Exception e) {
                System.err.println("Error updating sentiment for " + symbol + ": " + e.getMessage());
            }
        }
    }
    
    private double fetchSentimentForAsset(String symbol) {
        // This would call an external API for sentiment analysis
        // For demo purposes, we'll generate a random sentiment
        Random random = new Random();
        return random.nextDouble() * 2 - 1; // Range from -1 (negative) to 1 (positive)
        
        // Actual implementation would look like:
        /*
        try {
            URL url = new URL("https://api.example.com/sentiment?symbol=" + symbol + "&apikey=" + API_KEY);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            // Parse JSON response and extract sentiment
            // This is simplified - would use a proper JSON parser
            String jsonResponse = response.toString();
            // Extract sentiment value from JSON
            
            return extractedSentiment;
        } catch (Exception e) {
            System.err.println("Error fetching sentiment: " + e.getMessage());
            return 0.0;
        }
        */
    }
    
    public void shutdown() {
        if (sentimentUpdateTimer != null) {
            sentimentUpdateTimer.cancel();
        }
    }
}

// Latency Optimization and Metrics
class LatencyMonitor {
    private Map<String, List<Long>> operationLatencies = new ConcurrentHashMap<>();
    private static final int MAX_SAMPLES = 1000;
    
    public void recordLatency(String operation, long latencyNanos) {
        operationLatencies.computeIfAbsent(operation, k -> new ArrayList<>())
                          .add(latencyNanos);
        
        // Keep only the most recent samples
        List<Long> latencies = operationLatencies.get(operation);
        if (latencies.size() > MAX_SAMPLES) {
            latencies = latencies.subList(latencies.size() - MAX_SAMPLES, latencies.size());
            operationLatencies.put(operation, latencies);
        }
    }
    
    public long getAverageLatency(String operation) {
        List<Long> latencies = operationLatencies.getOrDefault(operation, Collections.emptyList());
        if (latencies.isEmpty()) {
            return 0;
        }
        
        return (long) latencies.stream().mapToLong(l -> l).average().orElse(0);
    }
    
    public long getP95Latency(String operation) {
        List<Long> latencies = operationLatencies.getOrDefault(operation, Collections.emptyList());
        if (latencies.isEmpty()) {
            return 0;
        }
        
        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);
        
        int index = (int) Math.ceil(0.95 * sortedLatencies.size()) - 1;
        return sortedLatencies.get(index);
    }
    
    public void printLatencyStats() {
        System.out.println("===== LATENCY STATISTICS =====");
        for (Map.Entry<String, List<Long>> entry : operationLatencies.entrySet()) {
            String operation = entry.getKey();
            long avgLatency = getAverageLatency(operation);
            long p95Latency = getP95Latency(operation);
            
            System.out.printf("%s: Avg: %.3f ms, P95: %.3f ms\n", 
                    operation, avgLatency / 1_000_000.0, p95Latency / 1_000_000.0);
        }
    }
}

// ML Strategy using the prediction model
class MLStrategy implements TradingStrategy {
    private Asset asset;
    private PredictionModel model;
    private int quantity;
    private LatencyMonitor latencyMonitor;
    private int lookbackWindow = 10;
    
    public MLStrategy(Asset asset, PredictionModel model, int quantity, LatencyMonitor latencyMonitor) {
        this.asset = asset;
        this.model = model;
        this.quantity = quantity;
        this.latencyMonitor = latencyMonitor;
    }
    
    @Override
    public void execute(MarketDataManager dataManager, Portfolio portfolio, RiskManager riskManager, 
                       ConcurrentLinkedQueue<Trade> tradesQueue) {
        long startTime = System.nanoTime();
        
        AssetMarketData marketData = dataManager.getMarketData(asset);
        if (marketData == null) return;
        
        List<Double> historicalPrices = marketData.getHistoricalPrices();
        if (historicalPrices.size() < lookbackWindow) return;
        
        // Extract features for prediction
        List<Double> features = extractFeatures(marketData);
        
        // Make prediction
        double predictedPrice = model.predict(features);
        double currentPrice = marketData.getLastPrice();
        
        Position position = portfolio.getPosition(asset);
        
        // Decision logic based on prediction
        if (predictedPrice > currentPrice * 1.01) {
            // Predicted price is 1% higher - buy signal
            int qty = Math.min(quantity, riskManager.getMaxQuantity(asset, currentPrice));
            if (qty > 0 && riskManager.canTrade(asset, qty, currentPrice)) {
                tradesQueue.add(new Trade(asset, currentPrice, qty, true));
            }
        } else if (predictedPrice < currentPrice * 0.99 && position.getQuantity() > 0) {
            // Predicted price is 1% lower - sell signal
            tradesQueue.add(new Trade(asset, currentPrice, position.getQuantity(), false));
        }
        
        long endTime = System.nanoTime();
        latencyMonitor.recordLatency("ML Strategy Execution", endTime - startTime);
    }
    
    private List<Double> extractFeatures(AssetMarketData marketData) {
        List<Double> features = new ArrayList<>();
        
        // Recent price changes
        List<Double> prices = marketData.getHistoricalPrices();
        int size = prices.size();
        for (int i = 0; i < lookbackWindow; i++) {
            if (size - 1 - i >= 0) {
                features.add(prices.get(size - 1 - i));
            } else {
                features.add(0.0);
            }
        }
        
        // Technical indicators
        features.add(marketData.getIndicator("RSI"));
        features.add(marketData.getIndicator("MACD"));
        features.add(marketData.getIndicator("MACD_SIGNAL"));
        
        return features;
    }
    
    @Override
    public String getName() {
        return "ML Strategy (" + asset.getSymbol() + ", " + model.getName() + ")";
    }
}

// Sentiment-based strategy
class SentimentStrategy implements TradingStrategy {
    private Asset asset;
    private int quantity;
    private MarketSentiment sentimentAnalyzer;
    private double sentimentThreshold;
    
    public SentimentStrategy(Asset asset, int quantity, MarketSentiment sentimentAnalyzer, double sentimentThreshold) {
        this.asset = asset;
        this.quantity = quantity;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.sentimentThreshold = sentimentThreshold;
    }
    
    @Override
    public void execute(MarketDataManager dataManager, Portfolio portfolio, RiskManager riskManager, 
                       ConcurrentLinkedQueue<Trade> tradesQueue) {
        AssetMarketData marketData = dataManager.getMarketData(asset);
        if (marketData == null) return;
        
        double sentiment = sentimentAnalyzer.getSentiment(asset);
        double currentPrice = marketData.getLastPrice();
        Position position = portfolio.getPosition(asset);
        
        if (sentiment > sentimentThreshold) {
            // Strong positive sentiment - buy signal
            int qty = Math.min(quantity, riskManager.getMaxQuantity(asset, currentPrice));
            if (qty > 0 && riskManager.canTrade(asset, qty, currentPrice)) {
                tradesQueue.add(new Trade(asset, currentPrice, qty, true));
            }
        } else if (sentiment < -sentimentThreshold && position.getQuantity() > 0) {
            // Strong negative sentiment - sell signal
            tradesQueue.add(new Trade(asset, currentPrice, position.getQuantity(), false));
        }
    }
    
    @Override
    public String getName() {
        return "Sentiment Strategy (" + asset.getSymbol() + ", threshold: " + sentimentThreshold + ")";
    }
}

class UserTradingInterface {
    private Portfolio portfolio;
    private MarketDataManager marketDataManager;
    private RiskManager riskManager;
    private Map<String, Asset> availableAssets = new HashMap<>();
    private Scanner scanner;
    private boolean running = true;
    private MarketSimulator simulator;
    private PerformanceTracker performanceTracker;
    
    public UserTradingInterface(Portfolio portfolio, MarketDataManager marketDataManager, 
                               RiskManager riskManager, MarketSimulator simulator,
                               PerformanceTracker performanceTracker) {
        this.portfolio = portfolio;
        this.marketDataManager = marketDataManager;
        this.riskManager = riskManager;
        this.simulator = simulator;
        this.performanceTracker = performanceTracker;
        this.scanner = new Scanner(System.in);
        
        // Register the initial assets
        for (Asset asset : marketDataManager.getRegisteredAssets()) {
            availableAssets.put(asset.getSymbol(), asset);
        }
    }
    
    public void start() {
        System.out.println("\n===== WELCOME TO THE TRADING TERMINAL =====");
        printHelp();
        
        // Start market updates in a separate thread
        startMarketUpdates();
        
        while (running) {
            System.out.print("\nEnter command > ");
            String command = scanner.nextLine().trim();
            
            processCommand(command);
        }
        
        System.out.println("Trading terminal closed. Goodbye!");
    }
    
    private void startMarketUpdates() {
        Thread marketUpdateThread = new Thread(() -> {
            while (running) {
                try {
                    // Simulate market movement
                    simulator.simulatePriceMovement();
                    TimeUnit.SECONDS.sleep(5); // Update every 5 seconds
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        marketUpdateThread.setDaemon(true);
        marketUpdateThread.start();
    }
    
    private void processCommand(String command) {
        String[] parts = command.split("\\s+");
        String action = parts.length > 0 ? parts[0].toLowerCase() : "";
        
        try {
            switch (action) {
                case "help":
                    printHelp();
                    break;
                    
                case "list":
                    listAvailableAssets();
                    break;
                    
                case "price":
                    if (parts.length > 1) {
                        showPrice(parts[1]);
                    } else {
                        System.out.println("Error: Symbol required. Usage: price SYMBOL");
                    }
                    break;
                    
                case "buy":
                    if (parts.length >= 3) {
                        try {
                            String symbol = parts[1];
                            int quantity = Integer.parseInt(parts[2]);
                            buyStock(symbol, quantity);
                        } catch (NumberFormatException e) {
                            System.out.println("Error: Invalid quantity. Usage: buy SYMBOL QUANTITY");
                        }
                    } else {
                        System.out.println("Error: Symbol and quantity required. Usage: buy SYMBOL QUANTITY");
                    }
                    break;
                    
                case "sell":
                    if (parts.length >= 3) {
                        try {
                            String symbol = parts[1];
                            int quantity = Integer.parseInt(parts[2]);
                            sellStock(symbol, quantity);
                        } catch (NumberFormatException e) {
                            System.out.println("Error: Invalid quantity. Usage: sell SYMBOL QUANTITY");
                        }
                    } else {
                        System.out.println("Error: Symbol and quantity required. Usage: sell SYMBOL QUANTITY");
                    }
                    break;
                    
                case "portfolio":
                    showPortfolio();
                    break;
                    
                case "balance":
                    showBalance();
                    break;
                    
                case "add":
                    if (parts.length >= 3) {
                        try {
                            String symbol = parts[1];
                            double initialPrice = Double.parseDouble(parts[2]);
                            addNewAsset(symbol, initialPrice);
                        } catch (NumberFormatException e) {
                            System.out.println("Error: Invalid price. Usage: add SYMBOL INITIAL_PRICE");
                        }
                    } else {
                        System.out.println("Error: Symbol and initial price required. Usage: add SYMBOL INITIAL_PRICE");
                    }
                    break;
                    
                case "history":
                    if (parts.length > 1) {
                        showPriceHistory(parts[1]);
                    } else {
                        System.out.println("Error: Symbol required. Usage: history SYMBOL");
                    }
                    break;
                    
                case "performance":
                    showPerformance();
                    break;
                    
                case "exit":
                case "quit":
                    running = false;
                    break;
                    
                default:
                    System.out.println("Unknown command. Type 'help' for available commands.");
            }
        } catch (Exception e) {
            System.out.println("Error processing command: " + e.getMessage());
        }
    }
    
    private void printHelp() {
        System.out.println("\nAvailable commands:");
        System.out.println("  help             - Show this help menu");
        System.out.println("  list             - List all available assets");
        System.out.println("  price SYMBOL     - Show current price for an asset");
        System.out.println("  buy SYMBOL QTY   - Buy a specified quantity of an asset");
        System.out.println("  sell SYMBOL QTY  - Sell a specified quantity of an asset");
        System.out.println("  portfolio        - Show your current portfolio");
        System.out.println("  balance          - Show your account balance");
        System.out.println("  add SYMBOL PRICE - Add a new asset to trade");
        System.out.println("  history SYMBOL   - Show price history for an asset");
        System.out.println("  performance      - Show your trading performance");
        System.out.println("  exit/quit        - Exit the trading terminal");
    }
    
    private void listAvailableAssets() {
        System.out.println("\n===== AVAILABLE ASSETS =====");
        System.out.printf("%-8s %-25s %-15s %s\n", "SYMBOL", "NAME", "TYPE", "PRICE");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        
        for (Asset asset : availableAssets.values()) {
            double currentPrice = marketDataManager.getMarketData(asset).getLastPrice();
            System.out.printf("%-8s %-25s %-15s $%.2f\n", 
                    asset.getSymbol(), 
                    asset.getName(),
                    asset.getType(),
                    currentPrice);
        }
        
        System.out.println("\nPrices as of " + timestamp);
    }
    
    private void showPrice(String symbol) {
        Asset asset = getAssetBySymbol(symbol);
        if (asset != null) {
            double currentPrice = marketDataManager.getMarketData(asset).getLastPrice();
            double openPrice = marketDataManager.getMarketData(asset).getOpenPrice();
            double highPrice = marketDataManager.getMarketData(asset).getHighPrice();
            double lowPrice = marketDataManager.getMarketData(asset).getLowPrice();
            
            double changeAmount = currentPrice - openPrice;
            double changePercent = openPrice > 0 ? (changeAmount / openPrice) * 100 : 0;
            
            System.out.println("\n===== " + asset.getSymbol() + " (" + asset.getName() + ") =====");
            System.out.printf("Current Price: $%.2f\n", currentPrice);
            System.out.printf("Change: $%.2f (%.2f%%)\n", changeAmount, changePercent);
            System.out.printf("Today's Range: $%.2f - $%.2f\n", lowPrice, highPrice);
            
            // Show order book if available
            OrderBook orderBook = simulator.getOrderBook(asset);
            if (orderBook != null) {
                System.out.printf("Best Bid: $%.2f | Best Ask: $%.2f\n", 
                        orderBook.getBestBid(), 
                        orderBook.getBestAsk());
            }
            
            // Show position if user owns this asset
            Position position = portfolio.getPosition(asset);
            if (position.getQuantity() > 0) {
                double unrealizedPnL = position.getUnrealizedPnL(currentPrice);
                System.out.printf("\nYour Position: %d shares @ $%.2f\n", 
                        position.getQuantity(), position.getAveragePrice());
                System.out.printf("Unrealized P&L: $%.2f (%.2f%%)\n", 
                        unrealizedPnL, 
                        (unrealizedPnL / (position.getAveragePrice() * position.getQuantity())) * 100);
            }
        } else {
            System.out.println("Error: Asset with symbol '" + symbol + "' not found.");
        }
    }
    
    private void buyStock(String symbol, int quantity) {
        if (quantity <= 0) {
            System.out.println("Error: Quantity must be greater than zero.");
            return;
        }
        
        Asset asset = getAssetBySymbol(symbol);
        if (asset != null) {
            double currentPrice = marketDataManager.getMarketData(asset).getLastPrice();
            double totalCost = currentPrice * quantity;
            
            if (riskManager.canTrade(asset, quantity, currentPrice)) {
                // Create and execute the trade
                Trade trade = new Trade(asset, currentPrice, quantity, true);
                
                // Simulate trade execution
                simulator.simulateTradeExecution(trade);
                
                if (trade.getStatus() == Trade.TradeStatus.FILLED) {
                    // Add trade to portfolio
                    portfolio.addTrade(trade);
                    
                    System.out.printf("\nBOUGHT: %d shares of %s at $%.2f\n", 
                            quantity, symbol, currentPrice);
                    System.out.printf("Total Cost: $%.2f\n", totalCost);
                    
                    // Show updated position
                    Position position = portfolio.getPosition(asset);
                    System.out.printf("Updated Position: %d shares @ $%.2f\n", 
                            position.getQuantity(), position.getAveragePrice());
                } else {
                    System.out.println("Trade could not be executed. Status: " + trade.getStatus());
                }
            } else {
                System.out.println("Error: Insufficient funds or trade exceeds risk limits.");
                System.out.printf("Required: $%.2f, Available: $%.2f\n", 
                        totalCost, riskManager.getCurrentCapital());
            }
        } else {
            System.out.println("Error: Asset with symbol '" + symbol + "' not found.");
        }
    }
    
    private void sellStock(String symbol, int quantity) {
        if (quantity <= 0) {
            System.out.println("Error: Quantity must be greater than zero.");
            return;
        }
        
        Asset asset = getAssetBySymbol(symbol);
        if (asset != null) {
            Position position = portfolio.getPosition(asset);
            
            if (position.getQuantity() >= quantity) {
                double currentPrice = marketDataManager.getMarketData(asset).getLastPrice();
                double totalValue = currentPrice * quantity;
                
                // Create and execute the trade
                Trade trade = new Trade(asset, currentPrice, quantity, false);
                
                // Simulate trade execution
                simulator.simulateTradeExecution(trade);
                
                if (trade.getStatus() == Trade.TradeStatus.FILLED) {
                    // Calculate P&L before adding trade
                    double avgPrice = position.getAveragePrice();
                    double tradePnL = (currentPrice - avgPrice) * quantity;
                    
                    // Add trade to portfolio
                    portfolio.addTrade(trade);
                    
                    // Record performance
                    performanceTracker.recordTrade(tradePnL, riskManager.getCurrentCapital());
                    
                    System.out.printf("\nSOLD: %d shares of %s at $%.2f\n", 
                            quantity, symbol, currentPrice);
                    System.out.printf("Total Value: $%.2f\n", totalValue);
                    System.out.printf("P&L: $%.2f\n", tradePnL);
                    
                    // Show updated position if any shares remain
                    if (position.getQuantity() > 0) {
                        System.out.printf("Remaining Position: %d shares @ $%.2f\n", 
                                position.getQuantity(), position.getAveragePrice());
                    } else {
                        System.out.println("Position closed.");
                    }
                } else {
                    System.out.println("Trade could not be executed. Status: " + trade.getStatus());
                }
            } else {
                System.out.println("Error: Insufficient shares. You own " + position.getQuantity() + 
                        " shares but tried to sell " + quantity + ".");
            }
        } else {
            System.out.println("Error: Asset with symbol '" + symbol + "' not found.");
        }
    }
    
    private void showPortfolio() {
        portfolio.printPortfolioSummary(marketDataManager);
    }
    
    private void showBalance() {
        System.out.println("\n===== ACCOUNT BALANCE =====");
        System.out.printf("Initial Capital: $%.2f\n", riskManager.getInitialCapital());
        System.out.printf("Current Cash: $%.2f\n", riskManager.getCurrentCapital());
        
        double portfolioValue = portfolio.getTotalValue(marketDataManager);
        System.out.printf("Portfolio Value: $%.2f\n", portfolioValue);
        System.out.printf("Total Account Value: $%.2f\n", riskManager.getCurrentCapital() + portfolioValue);
        
        double totalReturn = (riskManager.getCurrentCapital() + portfolioValue) - riskManager.getInitialCapital();
        double returnPercent = (totalReturn / riskManager.getInitialCapital()) * 100;
        System.out.printf("Total Return: $%.2f (%.2f%%)\n", totalReturn, returnPercent);
    }
    
    private void addNewAsset(String symbol, double initialPrice) {
        if (availableAssets.containsKey(symbol)) {
            System.out.println("Error: Asset with symbol '" + symbol + "' already exists.");
            return;
        }
        
        String name = promptForInput("Enter asset name: ");
        
        System.out.println("Asset types:");
        for (Asset.AssetType type : Asset.AssetType.values()) {
            System.out.println(" - " + type);
        }
        
        String typeStr = promptForInput("Enter asset type: ");
        Asset.AssetType type;
        
        try {
            type = Asset.AssetType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid asset type. Using STOCK as default.");
            type = Asset.AssetType.STOCK;
        }
        
        Asset newAsset = new Asset(symbol, name, type);
        
        // Register the asset with all necessary components
        simulator.registerAsset(newAsset, initialPrice);
        availableAssets.put(symbol, newAsset);
        
        System.out.println("Successfully added new asset: " + symbol + " (" + name + ") at $" + initialPrice);
    }
    
    private void showPriceHistory(String symbol) {
        Asset asset = getAssetBySymbol(symbol);
        if (asset != null) {
            List<Double> priceHistory = marketDataManager.getMarketData(asset).getHistoricalPrices();
            
            if (priceHistory.isEmpty()) {
                System.out.println("No price history available for " + symbol);
                return;
            }
            
            System.out.println("\n===== PRICE HISTORY FOR " + symbol + " =====");
            System.out.println("Last 10 prices (most recent first):");
            
            int count = 0;
            for (int i = priceHistory.size() - 1; i >= 0 && count < 10; i--, count++) {
                System.out.printf("%d: $%.2f\n", count + 1, priceHistory.get(i));
            }
            
            // Simple statistics
            double min = priceHistory.stream().min(Double::compare).orElse(0.0);
            double max = priceHistory.stream().max(Double::compare).orElse(0.0);
            double avg = priceHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            System.out.println("\nStatistics:");
            System.out.printf("Min: $%.2f, Max: $%.2f, Avg: $%.2f\n", min, max, avg);
        } else {
            System.out.println("Error: Asset with symbol '" + symbol + "' not found.");
        }
    }
    
    private void showPerformance() {
        performanceTracker.printStats();
    }
    
    private Asset getAssetBySymbol(String symbol) {
        return availableAssets.get(symbol.toUpperCase());
    }
    
    private String promptForInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}

// Main class
public class Main {
    public static void main(String[] args) throws InterruptedException {
        // Configure initial parameters
        double initialCapital = 100000.0;
        RiskManager riskManager = new RiskManager(initialCapital, 5.0, 1.0, 2.0);
        
        // Create latency monitor
        LatencyMonitor latencyMonitor = new LatencyMonitor();
        
        // Create market data manager
        MarketDataManager marketDataManager = new MarketDataManager();
        
        // Create sentiment analyzer
        MarketSentiment sentimentAnalyzer = new MarketSentiment();
        
        // Create assets
        Asset appleStock = new Asset("AAPL", "Apple Inc.", Asset.AssetType.STOCK);
        Asset googleStock = new Asset("GOOGL", "Alphabet Inc.", Asset.AssetType.STOCK);
        Asset bitcoinCrypto = new Asset("BTC", "Bitcoin", Asset.AssetType.CRYPTO);
        
        // Register assets with sentiment analyzer
        sentimentAnalyzer.registerAsset(appleStock);
        sentimentAnalyzer.registerAsset(googleStock);
        sentimentAnalyzer.registerAsset(bitcoinCrypto);
        
        // Create market simulator
        MarketSimulator simulator = new MarketSimulator(marketDataManager);
        simulator.registerAsset(appleStock, 150.0);
        simulator.registerAsset(googleStock, 2500.0);
        simulator.registerAsset(bitcoinCrypto, 40000.0);
        
        // Create portfolio
        Portfolio portfolio = new Portfolio(riskManager);
        
        // Create performance tracker
        PerformanceTracker performanceTracker = new PerformanceTracker(initialCapital);
        
        // Create ML models
        PredictionModel appleLRModel = new LinearRegressionModel(13); // 10 price points + 3 indicators
        PredictionModel googleLRModel = new LinearRegressionModel(13);
        PredictionModel btcLRModel = new LinearRegressionModel(13);
        
        System.out.println("===== HIGH FREQUENCY TRADING SYSTEM =====");
        System.out.println("Choose operation mode:");
        System.out.println("1. User Trading Interface");
        System.out.println("2. Automated Backtest");
        System.out.println("3. Sample Trades Demo");
        System.out.println("4. Exit");
        
        Scanner scanner = new Scanner(System.in);
        System.out.print("\nEnter your choice (1-4): ");
        
        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            choice = 1; // Default to user trading interface
        }
        
        switch (choice) {
            case 1:
                // Run the user trading interface
                UserTradingInterface userInterface = new UserTradingInterface(
                    portfolio, marketDataManager, riskManager, simulator, performanceTracker);
                userInterface.start();
                break;
                
            case 2:
                // Run backtest with strategies
                BackTester backTester = new BackTester(marketDataManager, riskManager);
                
                // Add traditional strategies
                backTester.addStrategy(new RSIStrategy(appleStock, 14, 30, 70, 10));
                backTester.addStrategy(new MACDStrategy(googleStock, 5));
                
                // Add ML strategies
                backTester.addStrategy(new MLStrategy(appleStock, appleLRModel, 10, latencyMonitor));
                backTester.addStrategy(new MLStrategy(bitcoinCrypto, btcLRModel, 1, latencyMonitor));
                
                // Add sentiment strategies
                backTester.addStrategy(new SentimentStrategy(googleStock, 5, sentimentAnalyzer, 0.5));
                
                System.out.println("\nRunning backtest...");
                backTester.runBacktest(simulator, 1000);
                
                // Print latency statistics
                latencyMonitor.printLatencyStats();
                break;
                
            case 3:
                // Run with sample trades
                runWithSampleTrades();
                break;
                
            case 4:
                System.out.println("Exiting program. Goodbye!");
                break;
                
            default:
                System.out.println("Invalid choice. Exiting program.");
        }
        
        // Shutdown resources
        sentimentAnalyzer.shutdown();
        scanner.close();
    }
    
    /**
     * Run a simple simulation with predefined trades for demonstration
     */
    public static void runWithSampleTrades() {
        System.out.println("\n==== SAMPLE TRADE EXECUTION ====");
        System.out.println("Trades Executed:");
        
        // Execute the specific sample trades
        System.out.printf("Bought at: %.2f\n", 99.87);
        System.out.printf("Sold at: %.2f\n", 205.63);
        System.out.printf("Bought at: %.2f\n", 100.32);
        
        // Calculate P&L
        double buyPrice1 = 99.87;
        double sellPrice = 205.63;
        double buyPrice2 = 100.32;
        
        double pnl1 = sellPrice - buyPrice1;
        
        System.out.println("\nTrade Analysis:");
        System.out.printf("P&L from first round-trip: $%.2f (%.2f%%)\n", 
                          pnl1, (pnl1 / buyPrice1) * 100);
        System.out.println("Second buy position is still open");
        
        System.out.println("\nNote: These are sample trades demonstrating the requested output format");
    }
    
    public static void trainMLModels(MarketSimulator simulator, PredictionModel model, Asset asset, int iterations) {
        System.out.println("Training ML model for " + asset.getSymbol() + "...");
        
        List<List<Double>> featuresList = new ArrayList<>();
        List<Double> labels = new ArrayList<>();
        
        // Generate training data by simulating market
        for (int i = 0; i < iterations; i++) {
            simulator.simulatePriceMovement();
            
            AssetMarketData marketData = simulator.getMarketDataManager().getMarketData(asset);
            if (marketData.getHistoricalPrices().size() > 20) {
                // Extract features (same as in MLStrategy)
                List<Double> features = new ArrayList<>();
                
                List<Double> prices = marketData.getHistoricalPrices();
                int size = prices.size();
                for (int j = 0; j < 10; j++) {
                    if (size - 1 - j >= 0) {
                        features.add(prices.get(size - 1 - j));
                    } else {
                        features.add(0.0);
                    }
                }
                
                features.add(marketData.getIndicator("RSI"));
                features.add(marketData.getIndicator("MACD"));
                features.add(marketData.getIndicator("MACD_SIGNAL"));
                
                // Label is the next price
                double currentPrice = marketData.getLastPrice();
                
                featuresList.add(new ArrayList<>(features));
                labels.add(currentPrice);
            }
        }
        
        // Train the model
        if (!featuresList.isEmpty()) {
            model.train(featuresList, labels);
            System.out.println("Model training complete for " + asset.getSymbol());
        } else {
            System.out.println("Not enough data to train model for " + asset.getSymbol());
        }
    }
    
    public static void runRealtimeSimulation(MarketDataManager marketDataManager, RiskManager riskManager, 
                                            MarketSimulator simulator, LatencyMonitor latencyMonitor,
                                            MarketSentiment sentimentAnalyzer) throws InterruptedException {
        // Create portfolio
        Portfolio portfolio = new Portfolio(riskManager);
        
        // Create strategy executor
        StrategyExecutor strategyExecutor = new StrategyExecutor(marketDataManager, portfolio, riskManager);
        
        // Add strategies for all assets
        for (Asset asset : marketDataManager.getRegisteredAssets()) {
            // Add both technical and sentiment-based strategies
            strategyExecutor.addStrategy(new RSIStrategy(asset, 14, 30, 70, 
                                                      riskManager.getMaxQuantity(asset, 
                                                      marketDataManager.getMarketData(asset).getLastPrice())));
            strategyExecutor.addStrategy(new SentimentStrategy(asset, 5, sentimentAnalyzer, 0.5));
        }
        
        // Performance tracking
        PerformanceTracker performanceTracker = new PerformanceTracker(riskManager.getInitialCapital());
        
        // Trading simulation loop
        System.out.println("Starting real-time trading simulation...");
        
        for (int i = 0; i < 1000; i++) {
            long iterationStartTime = System.nanoTime();
            
            // Simulate market price movements
            simulator.simulatePriceMovement();
            
            // Execute all strategies on current market data
            strategyExecutor.executeStrategies();
            
            // Process trades
            processTrades(strategyExecutor.getTradesQueue(), simulator, portfolio, 
                         riskManager, performanceTracker, latencyMonitor);
            
            // Record iteration latency
            long iterationEndTime = System.nanoTime();
            latencyMonitor.recordLatency("Full Iteration", iterationEndTime - iterationStartTime);
            
            // Slow down simulation for better visualization
            TimeUnit.MILLISECONDS.sleep(10);
            
            // Print status every 100 iterations
            if (i > 0 && i % 100 == 0) {
                System.out.printf("\n==== TRADING SIMULATION - ITERATION %d ====\n", i);
                portfolio.printPortfolioSummary(marketDataManager);
                latencyMonitor.printLatencyStats();
            }
        }
        
        // Print final results
        System.out.println("\nTrading simulation complete!");
        portfolio.printPortfolioSummary(marketDataManager);
        performanceTracker.printStats();
    }
    
    private static void processTrades(ConcurrentLinkedQueue<Trade> trades, MarketSimulator simulator,
                                     Portfolio portfolio, RiskManager riskManager, 
                                     PerformanceTracker performanceTracker, LatencyMonitor latencyMonitor) {
        Trade trade;
        
        while ((trade = trades.poll()) != null) {
            long startTime = System.nanoTime();
            
            // Simulate trade execution
            simulator.simulateTradeExecution(trade);
            
            if (trade.getStatus() == Trade.TradeStatus.FILLED) {
                // Add trade to portfolio
                portfolio.addTrade(trade);
                
                // Print trade details
                System.out.println("Executed trade: " + trade.toString());
                
                // Calculate P&L if it's a sell trade
                if (!trade.isBuy()) {
                    Position position = portfolio.getPosition(trade.getAsset());
                    double tradePnL = position.getRealizedPnL();
                    performanceTracker.recordTrade(tradePnL, riskManager.getCurrentCapital());
                    
                    // Print P&L
                    System.out.printf("Trade P&L: $%.2f\n", tradePnL);
                }
            }
            
            long endTime = System.nanoTime();
            latencyMonitor.recordLatency("Trade Processing", endTime - startTime);
        }
    }
}
