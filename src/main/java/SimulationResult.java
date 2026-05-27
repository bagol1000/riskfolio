import java.util.List;

/**
 * Niemodyfikowalny kontener (DTO) przechowujący wyniki symulacji.
 */
public record SimulationResult(
        List<List<Double>> samplePaths,
        List<Double> finalValues,
        String infoLog
) {
    public double getMeanFinalValue() {
        return finalValues.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }
}