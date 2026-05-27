/**
 * Kontener na parametry modelu GARCH(1,1).
 */
public record GarchParams(double omega, double alpha, double beta, double initialVol) {
    @Override
    public String toString() {
        return String.format("α=%.6f, β=%.6f, ω=%.6f", alpha, beta, omega);
    }
}