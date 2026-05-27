import java.util.List;

public class RiskService {

    public double calculateVaR(List<Double> results, double confidence) {
        return results.stream()
                .sorted()
                .skip((long) ((1 - confidence) * results.size()))
                .findFirst()
                .orElse(0.0);
    }

    public double calculateCVaR(List<Double> results, double confidence) {
        double varThreshold = calculateVaR(results, confidence);
        return results.stream()
                .filter(v -> v <= varThreshold)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(varThreshold);
    }
}