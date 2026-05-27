import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Odpowiada za pobieranie historycznych danych finansowych z serwisu Stooq
 * oraz przetwarzanie ich na procentowe stopy zwrotu.
 */
public class DataService {

    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int TRADING_DAYS = 252;
    private static final int TIMEOUT_MS = 5000; // 5s

    public List<Double> getHistoricalReturns(String ticker, int years) throws Exception {
        String tickerClean = ticker.trim().toLowerCase().replace("^", "%5E");
        String urlString = String.format("https://stooq.pl/q/d/l/?s=%s&i=d", tickerClean);

        try {
            URL url = new URI(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Konfiguracja połączenia
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Serwer zwrócił błąd HTTP: " + responseCode +
                        " (prawdopodobnie błędny ticker: " + ticker + ")");
            }

            List<Double> prices = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                prices = br.lines()
                        .skip(1)
                        .map(line -> line.split(","))
                        .filter(values -> values.length >= 5)
                        .map(values -> {
                            try {
                                return Double.parseDouble(values[4]);
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        })
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
            }

            if (prices.size() < 2) {
                throw new Exception("Brak danych lub błędny ticker: " + ticker);
            }

            int daysNeeded = years * TRADING_DAYS;
            List<Double> finalPrices = prices.size() > daysNeeded
                    ? prices.subList(prices.size() - daysNeeded, prices.size())
                    : prices;

            return IntStream.range(1, finalPrices.size())
                    .mapToObj(i -> (finalPrices.get(i) - finalPrices.get(i - 1)) / finalPrices.get(i - 1))
                    .collect(Collectors.toList());

        } catch (UnknownHostException e) {
            throw new Exception("Brak połączenia z internetem lub serwer jest nieosiągalny.");
        } catch (IOException e) {
            throw new Exception("Błąd podczas pobierania danych: " + e.getMessage());
        }
    }
}