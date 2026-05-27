import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class MonteCarloEngine {

    private final DataService dataService = new DataService();
    private final GarchOptimizer optimizer = new GarchOptimizer();

    private static final int NUM_SIMULATIONS = 10_000;

    private static final String PROXY_PL = "WIG20";
    private static final String PROXY_US = "^SPX";

    public SimulationResult runSimulation(double capital, int horizon, List<String> tickers, List<Double> weights, int historyYears, boolean useInterpolation) throws Exception {

        List<List<Double>> rawReturns = new ArrayList<>();
        for (String t : tickers) {
            rawReturns.add(dataService.getHistoricalReturns(t, historyYears));
        }

        List<List<Double>> processedReturns = new ArrayList<>();
        StringBuilder warnings = new StringBuilder();

        if (useInterpolation) {
            List<Double> wig20 = null;
            List<Double> spx = null;

            int maxLen = rawReturns.stream().mapToInt(List::size).max().orElse(0);

            for (int i = 0; i < tickers.size(); i++) {
                String ticker = tickers.get(i);
                List<Double> assetData = rawReturns.get(i);

                if (assetData.size() < maxLen) {
                    List<Double> filledData = new ArrayList<>();
                    int missingPoints = maxLen - assetData.size();
                    boolean patched = false;

                    if (!ticker.endsWith(".*")) {
                        if (wig20 == null) wig20 = dataService.getHistoricalReturns(PROXY_PL, historyYears);
                        patched = tryPatchData(filledData, wig20, assetData, missingPoints);
                        if(patched) warnings.append(String.format("ℹ %s: Uzupełniono %d dni danymi WIG20.\n", ticker, missingPoints));

                    } else if (ticker.endsWith(".US")) {
                        if (spx == null) spx = dataService.getHistoricalReturns(PROXY_US, historyYears);
                        patched = tryPatchData(filledData, spx, assetData, missingPoints);
                        if(patched) warnings.append(String.format("ℹ %s: Uzupełniono %d dni danymi S&P500.\n", ticker, missingPoints));
                    }

                    if (patched) {
                        processedReturns.add(filledData);
                    } else {
                        processedReturns.add(assetData);
                    }
                } else {
                    processedReturns.add(assetData);
                }
            }
        } else {
            processedReturns = rawReturns;
        }

        PortfolioHistoryResult historyResult = calculatePortfolioHistory(processedReturns, weights);
        List<Double> portfolioReturns = historyResult.returns;

        if (!useInterpolation && historyResult.cutInfo > 0) {
            warnings.append("Analiza ucięta do ").append(historyResult.cutInfo).append(" dni (najmłodszy instrument).\n");
        } else if (useInterpolation) {
            int finalLen = portfolioReturns.size();
            int maxRawLen = rawReturns.stream().mapToInt(List::size).max().orElse(0);
            if (finalLen < maxRawLen) {
                warnings.append("Mimo interpolacji, analiza skrócona do ").append(finalLen).append(" dni.\n");
            }
        }

        double avgDailyReturn = portfolioReturns.stream().mapToDouble(d -> d).average().orElse(0.0);
        GarchParams garch = optimizer.optimize(portfolioReturns);

        String infoLog = buildModelReport(garch, tickers) + "\n=== JAKOŚĆ DANYCH ===\n" + warnings.toString();

        List<List<Double>> paths = Collections.synchronizedList(new ArrayList<>());
        final GarchParams finalGarch = garch;

        IntStream.range(0, NUM_SIMULATIONS).parallel().forEach(i -> {
            Random random = new Random();
            List<Double> path = new ArrayList<>(horizon + 1);

            double price = capital;
            double variance = finalGarch.initialVol();
            path.add(price);

            for (int day = 0; day < horizon; day++) {
                double stdDev = Math.sqrt(variance);
                double shock = stdDev * random.nextGaussian();

                price *= (1 + avgDailyReturn + shock);
                path.add(price);

                variance = finalGarch.omega() + (finalGarch.alpha() * Math.pow(shock, 2)) + (finalGarch.beta() * variance);
            }
            paths.add(path);
        });

        List<Double> finalValues = paths.stream()
                .map(p -> p.get(p.size() - 1))
                .toList();

        return new SimulationResult(paths, finalValues, infoLog);
    }

    private boolean tryPatchData(List<Double> targetList, List<Double> sourceProxy, List<Double> assetData, int missingPoints) {
        if (sourceProxy != null && sourceProxy.size() >= (missingPoints + assetData.size())) {
            for(int i=0; i < missingPoints; i++) {
                targetList.add(sourceProxy.get(i));
            }
            targetList.addAll(assetData);
            return true;
        }
        return false;
    }

    private String buildModelReport(GarchParams g, List<String> tickers) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PARAMETRY MODELU GARCH ===\n");
        sb.append("Dla portfela: ").append(tickers).append("\n");
        sb.append(String.format("• Alpha:    %.6f\n", g.alpha()));
        if (g.alpha() > 0.09) sb.append("  -> Uwaga! Rynek bardzo nerwowy.\n");
        else if (g.alpha() < 0.04) sb.append("  -> Rynek bardzo stabilny.\n");
        sb.append(String.format("• Beta:     %.6f\n", g.beta()));
        if (g.beta() > 0.90) sb.append("  -> Uwaga! Silne trendy zmienności.\n");
        sb.append(String.format("• Omega:    %.6f\n", g.omega()));
        return sb.toString();
    }

    private record PortfolioHistoryResult(List<Double> returns, int cutInfo) {}

    private PortfolioHistoryResult calculatePortfolioHistory(List<List<Double>> assetsReturns, List<Double> weights) {
        int minLength = assetsReturns.stream().mapToInt(List::size).min().orElse(0);

        List<Double> portfolio = new ArrayList<>(minLength);

        for (int i = 0; i < minLength; i++) {
            double dailySum = 0.0;
            for (int j = 0; j < weights.size(); j++) {
                List<Double> assetSeries = assetsReturns.get(j);
                int offset = assetSeries.size() - minLength;
                dailySum += assetSeries.get(offset + i) * weights.get(j);
            }
            portfolio.add(dailySum);
        }
        return new PortfolioHistoryResult(portfolio, minLength);
    }
}