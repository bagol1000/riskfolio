import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class InstrumentDatabase {

    private static List<Instrument> instruments = new ArrayList<>();

    public static void loadTickersFromFile(String filePath) {
        instruments.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {

                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(";");

                if (parts.length >= 2) {
                    String name = parts[0].trim();
                    String ticker = parts[1].trim();
                    instruments.add(new Instrument(name, ticker));
                }
            }
            System.out.println("Załadowano " + instruments.size() + " instrumentów.");

        } catch (Exception e) {
            System.err.println("Błąd wczytywania pliku tickers.csv: " + e.getMessage());
            instruments.add(new Instrument("Apple (Fallback)", "AAPL.US"));
            instruments.add(new Instrument("CD Projekt (Fallback)", "CDR.PL"));
        }
    }

    public static List<Instrument> getInstruments() {
        return instruments;
    }
}