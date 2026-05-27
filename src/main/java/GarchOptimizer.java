import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;


/**
 * Optymalizator typu Brute-Force / Random Search.
 * Próbuje tysiące kombinacji parametrów, aby znaleźć te,
 * które najlepiej pasują do historycznych danych.
 */
public class GarchOptimizer {

    private static final int ITERATIONS = 2000;

    public GarchParams optimize(List<Double> returns) {

        double variance = calculateVariance(returns);

        final double[] bestScore = {Double.MAX_VALUE};
        final GarchParams[] bestParams = {new GarchParams(variance * 0.05, 0.05, 0.90, variance)};

        IntStream.range(0, ITERATIONS).parallel().forEach(i -> {

            Random rand = new Random();

            double alpha = 0.01 + (0.25 * rand.nextDouble());
            double beta = 0.50 + (0.49 * rand.nextDouble());

            if (alpha + beta < 0.999) {

                double omega = variance * (1.0 - alpha - beta);
                double score = calculateErrorScore(returns, alpha, beta, omega, variance);

                synchronized (bestScore) {
                    if (score < bestScore[0]) {
                        bestScore[0] = score;
                        bestParams[0] = new GarchParams(omega, alpha, beta, variance);
                    }
                }
            }
        });

        return bestParams[0];
    }


    /**
     * Funkcja celu (Fitness Function).
     * Oblicza "błąd" modelu. Im mniejszy wynik, tym parametry lepiej opisują historię.
     * Używamy uproszczonego ujemnego Log-Likelihood.
     */
    private double calculateErrorScore(List<Double> returns,
                                       double alpha, double beta, double omega,
                                       double initialVar) {

        double currentVar = initialVar;
        double totalLogLikelihood = 0.0;

        for (Double r : returns) {
            if (currentVar <= 0) currentVar = 0.000001;
            totalLogLikelihood += Math.log(currentVar) + (Math.pow(r, 2) / currentVar);
            currentVar = omega + alpha * Math.pow(r, 2) + beta * currentVar;
        }
        return totalLogLikelihood;
    }


    private double calculateVariance(List<Double> data) {
        double mean = data.stream().mapToDouble(d -> d).average().orElse(0.0);
        return data.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0.0);
    }
}