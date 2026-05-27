import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class RiskFolioApp extends Application {

    private final MonteCarloEngine engine = new MonteCarloEngine();
    private final RiskService riskService = new RiskService();

    private final TextField weightsInput = new TextField("1.0");
    private final TextField hiddenTickerInput = new TextField();

    private final TextField searchField = new TextField();
    private final FlowPane tagsContainer = new FlowPane();
    private final List<Instrument> activeInstruments = new ArrayList<>();
    private final ContextMenu suggestionsMenu = new ContextMenu();

    private final TextField capitalInput = new TextField("100000");
    private final Slider horizonSlider = new Slider(30, 1095, 252);
    private final Label horizonLabel = new Label("252 dni");
    private final TextField historyInput = new TextField("5");
    private final CheckBox fillCheck = new CheckBox("Interpoluj (uzupełnij) dane");

    private final TextArea logArea = new TextArea();
    private LineChart<Number, Number> chart;


    @Override
    public void start(Stage stage) {

        InstrumentDatabase.loadTickersFromFile("tickers.csv");
        BorderPane root = new BorderPane();
        VBox sidebar = createSidebar();
        createChart();

        root.setLeft(sidebar);
        root.setCenter(chart);
        Scene scene = new Scene(root, 1280, 768);
        stage.setTitle("RiskFolio - Prognozowanie wyników inwestycji wraz z kwantyfikacją ryzyka");
        stage.setScene(scene);
        stage.show();

        if (!InstrumentDatabase.getInstruments().isEmpty()) {
            addInstrument(InstrumentDatabase.getInstruments().getFirst());
        }
    }

    private HBox createChip(Instrument instrument) {
        HBox chip = new HBox(5);
        chip.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        chip.setPadding(new Insets(5, 8, 5, 8));

        chip.setStyle(
                "-fx-background-color: #e1ecf4;" +
                        "-fx-background-radius: 15px;" +
                        "-fx-border-color: #7aa7c7;" +
                        "-fx-border-radius: 15px;"
        );

        Label label = new Label(instrument.name);
        label.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 11px;");

        Label closeBtn = new Label("✕");
        closeBtn.setStyle("-fx-text-fill: #7aa7c7; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 10px;");

        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-text-fill: red; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 10px;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-text-fill: #7aa7c7; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 10px;"));

        closeBtn.setOnMouseClicked(e -> {
            tagsContainer.getChildren().remove(chip);
            activeInstruments.remove(instrument);
            updateInputsFromSelection();
        });

        chip.getChildren().addAll(label, closeBtn);
        return chip;
    }

    private HBox createLabelWithHelp(String labelText, String helpText) {
        HBox box = new HBox(6);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label textLabel = new Label(labelText);
        textLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label helpIcon = new Label("?");
        helpIcon.setStyle(
                "-fx-background-color: #bdc3c7;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 10px;" +
                        "-fx-min-width: 16px; -fx-min-height: 16px;" +
                        "-fx-max-width: 16px; -fx-max-height: 16px;" +
                        "-fx-background-radius: 50%;" +
                        "-fx-alignment: center;" +
                        "-fx-cursor: hand;"
        );

        helpIcon.setOnMouseEntered(e -> helpIcon.setStyle(helpIcon.getStyle().replace("#bdc3c7", "#3498db")));
        helpIcon.setOnMouseExited(e -> helpIcon.setStyle(helpIcon.getStyle().replace("#3498db", "#bdc3c7")));

        Tooltip tooltip = new Tooltip(helpText);
        tooltip.setPrefWidth(300);
        tooltip.setWrapText(true);
        tooltip.setStyle("-fx-font-size: 10px; -fx-background-color: #ababb5; -fx-text-fill: white;");
        tooltip.setShowDelay(Duration.millis(150));
        tooltip.setShowDuration(Duration.seconds(60));
        tooltip.setHideDelay(Duration.millis(150));

        Tooltip.install(helpIcon, tooltip);

        box.getChildren().addAll(textLabel, helpIcon);
        return box;
    }

    private VBox createSidebar() {

        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(20));
        sidebar.setPrefWidth(350);
        sidebar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #bdc3c7; -fx-border-width: 0 1 0 0;");

        HBox searchLabel = createLabelWithHelp("Dodaj aktywa:",
                "Wpisz zwyczajową nazwę spółki, indeksu lub surowca, a aplikacja automatycznie znajdzie odpowiednie indeksy giełdowe. " +
                        "Dla wybranych aktywów zostaną pobrane historyczne dane z serwisu Stooq, dzięki którym możliwa będzie symulacja. Jeżeli interesują Cię" +
                        " aktywa zagranicznych firm, wpisz ich nazwy w języku angielskim."
        );

        searchField.setPromptText("np. Apple, Orlen, Nvidia...");
        setupAutocomplete();

        HBox listLabel = createLabelWithHelp("Twój Portfel:",
                "Lista wybranych aktywów. Możesz usuwać elementy klikając 'x'. " +
                        "Pamiętaj, że im bardziej różnorodne aktywa, tym mniejsze jest całkowite ryzyko twojego portfela."
        );

        tagsContainer.setHgap(5);
        tagsContainer.setPadding(new Insets(5));
        tagsContainer.setPrefWrapLength(300);

        HBox weightsLabel = createLabelWithHelp("Wagi:",
                "Określa, jaką część kapitału inwestujesz w dane aktywa. Wagi odpowiadają aktywom od lewej do prawej. " +
                        "Suma wag musi wynosić 1.0. Aplikacja automatycznie dzieli kapitał po równo, ale możesz to zmienić ręcznie."
        );
        weightsInput.setEditable(true);

        HBox capitalLabel = createLabelWithHelp("Kapitał (PLN):",
                "Kwota początkowa, którą chcesz zainwestować. " +
                        "Symulacja pokaże, ile te pieniądze mogą być warte w przyszłości oraz ile możesz stracić."
        );

        HBox horizonHeaderBox = createLabelWithHelp("Czas inwestycji:",
                "Liczba dni, na jaką symulujemy inwestycję. 252 dni to standardowy rok giełdowy. " +
                        "Pamiętaj, im dłuższy czas, tym większa niepewność wyników."
        );

        horizonLabel.setStyle("-fx-text-fill: #2c3e50; -fx-padding: 0 0 0 5;");
        horizonLabel.setText("252 dni");

        HBox horizonTopLine = new HBox();
        horizonTopLine.getChildren().addAll(horizonHeaderBox, horizonLabel);

        horizonSlider.setShowTickLabels(true);
        horizonSlider.setShowTickMarks(true);
        horizonSlider.setMajorTickUnit(365);
        horizonSlider.setBlockIncrement(30);
        horizonSlider.setSnapToTicks(false);

        horizonSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int days = newVal.intValue();
            horizonLabel.setText(days + " dni");
        });

        HBox historyLabel = createLabelWithHelp("Historia (lata):",
                "Ile lat wstecz analizujemy dane? " +
                        "Dłuższa historia (np. 5 lat) uwzględnia więcej cykli rynkowych (hossy i bessy), co czyni model stabilniejszym, ale też zmniejsza wpływ najnowszych wydarzeń."
        );

        fillCheck.setSelected(true);
        HBox fillCheckLabel = createLabelWithHelp("Uzupełnianie danych:",
                                      "Dla spółek z Polski brakującą historię uzupełnia wynikami WIG20.\n" +
                                              "Dla spółek z USA (.US) brakującą historię uzupełnia wynikami S&P 500.\n" + 
                                              "Dla pozostałych instrumentów oraz w przypadku odznaczenia: analiza jest skracana do najdłuższej dostępnej historii w portfelu."
        );

        Button runButton = new Button("URUCHOM SYMULACJĘ");
        runButton.setMaxWidth(Double.MAX_VALUE);
        runButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        runButton.setOnAction(e -> runSimulation());

        logArea.setEditable(false);
        logArea.setPrefHeight(350);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-family: 'Monospaced';");

        HBox reportLabel = createLabelWithHelp("Raport Ryzyka:",
                "Co pokazuje raport ryzyka:\n" +
                        "• VaR 95% (Value at Risk): Maksymalna strata, jakiej możesz się spodziewać w 95% przypadków. Jeśli VaR = 5000 zł, to istnieje tylko 5% szans, że stracisz więcej.\n\n" +
                        "• CVaR 95% (Conditional VaR): Średnia strata w tych najgorszych 5% przypadków (gdy rynek się np. załamie).\n\n" +
                        "• Parametry modelu GARCH: alpha to reakcja na szok, mówi nam, jak mocno dzisiejszy nagły skok ceny wpłynie na prognozowane ryzyko na jutro. "+
                        "Beta to pamięć rynku, mówi nam, jak długo utrzymuje się stan podwyższonego ryzyka. Wysoka wartość mówi, że okres dużej zmienności będzie trwał długo, tak samo okres spokoju. "+
                        "Omega to pewnego rodzaju szum rynkowy, jest to minimalny poziom zmienności, który występuje zawsze, nawet gdy na rynku nic się nie dzieje."
        );

        sidebar.getChildren().addAll(
                searchLabel, searchField,
                listLabel, tagsContainer,
                new Separator(),
                weightsLabel, weightsInput,
                capitalLabel, capitalInput,
                new Separator(),
                horizonTopLine, horizonSlider,
                historyLabel, historyInput,
                fillCheckLabel, fillCheck,
                new Separator(),
                runButton,
                reportLabel, logArea
        );
        return sidebar;
    }


    private void setupAutocomplete() {
        List<Instrument> allInstruments = InstrumentDatabase.getInstruments();

        PauseTransition pause = new PauseTransition(Duration.millis(350));
        pause.setOnFinished(event -> {
            String query = searchField.getText();
            suggestionsMenu.getItems().clear();

            if (query == null || query.trim().isEmpty()) {
                suggestionsMenu.hide();
                return;
            }

            List<Instrument> matches = allInstruments.parallelStream()
                    .filter(i -> {
                        String q = query.toLowerCase();
                        return i.name.toLowerCase().contains(q) || i.ticker.toLowerCase().contains(q);
                    })
                    .limit(15)
                    .toList();

            if (!matches.isEmpty()) {
                matches.forEach(inst -> {
                    MenuItem item = new MenuItem(inst.name + " (" + inst.ticker + ")");
                    item.setOnAction(e -> {
                        addInstrument(inst);
                        searchField.clear();
                        suggestionsMenu.hide();
                    });
                    suggestionsMenu.getItems().add(item);
                });
                if (!suggestionsMenu.isShowing()) {
                    suggestionsMenu.show(searchField, javafx.geometry.Side.BOTTOM, 0, 0);
                }
            } else {
                suggestionsMenu.hide();
            }
        });

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            suggestionsMenu.hide();
            pause.playFromStart();
        });

        searchField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) suggestionsMenu.hide();
        });

        searchField.setOnAction(e -> {
            String rawInput = searchField.getText().trim();
            suggestionsMenu.hide();
            if (rawInput.isEmpty()) return;

            Instrument knownInstrument = allInstruments.stream()
                    .filter(i -> i.ticker.equalsIgnoreCase(rawInput) || i.name.equalsIgnoreCase(rawInput))
                    .findFirst()
                    .orElse(null);

            if (knownInstrument != null) {
                addInstrument(knownInstrument);
            } else {
                String customTicker = rawInput.toUpperCase();
                Instrument customInst = new Instrument(customTicker, customTicker);
                addInstrument(customInst);
            }

            searchField.clear();
        });
    }

    private void addInstrument(Instrument inst) {

        boolean exists = activeInstruments.stream()
                .anyMatch(i -> i.ticker.equals(inst.ticker));

        if (!exists) {
            activeInstruments.add(inst);

            HBox chip = createChip(inst);
            tagsContainer.getChildren().add(chip);

            updateInputsFromSelection();
        }
    }

    private void updateInputsFromSelection() {
        if (activeInstruments.isEmpty()) {
            hiddenTickerInput.setText("");
            weightsInput.setText("");
            return;
        }

        String tickersStr = activeInstruments.stream()
                .map(i -> i.ticker)
                .collect(Collectors.joining(", "));
        hiddenTickerInput.setText(tickersStr);

        int size = activeInstruments.size();
        if (size == 0) return;

        double baseWeight = Math.floor((1.0 / size) * 100.0) / 100.0;

        List<String> weightStrings = new ArrayList<>();
        double currentSum = 0.0;

        for (int i = 0; i < size - 1; i++) {
            weightStrings.add(String.format(java.util.Locale.US, "%.2f", baseWeight));
            currentSum += baseWeight;
        }

        double lastWeight = 1.0 - currentSum;
        weightStrings.add(String.format(java.util.Locale.US, "%.2f", lastWeight));

        weightsInput.setText(String.join(", ", weightStrings));
    }


    private void createChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Dzień symulacji");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Wartość portfela (PLN)");
        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Prognoza dynamiki portfela z Monte Carlo");
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
    }


    private void runSimulation() {

        logArea.setText("1. Pobieranie danych ze Stooq...\n2. Kalibracja modelu...\n3. Generowanie 10,000 scenariuszy...");
        chart.getData().clear();

        boolean doFilling = fillCheck.isSelected();

        new Thread(() -> {

            try {
                double capital = Double.parseDouble(capitalInput.getText());
                int horizon = (int) horizonSlider.getValue();
                int historyYears = Integer.parseInt(historyInput.getText());
                if (hiddenTickerInput.getText().isEmpty()) throw new IllegalArgumentException("Portfel jest pusty!");
                List<String> tickers = Arrays.asList(hiddenTickerInput.getText().split(",\\s*"));
                List<Double> weights = Arrays.stream(weightsInput.getText().split(",\\s*"))
                        .map(Double::parseDouble)
                        .collect(Collectors.toList());
                if (Math.abs(weights.stream().mapToDouble(d -> d).sum() - 1.0) > 0.01) {
                    throw new IllegalArgumentException("Suma wag musi wynosić 1.0");
                }

                SimulationResult result = engine.runSimulation(capital, horizon, tickers, weights, historyYears, doFilling);
                Platform.runLater(() -> updateUI(result));

            } catch (Exception ex) {
                Platform.runLater(() -> logArea.setText("BŁĄD: " + ex.getMessage()));
                ex.printStackTrace();
            }

        }).start();

    }


    private void updateUI(SimulationResult result) {

        logArea.clear();
        List<Double> finals = result.finalValues();

        double meanVal = result.getMeanFinalValue();
        double minVal = finals.stream().mapToDouble(v -> v).min().orElse(0.0);
        double maxVal = finals.stream().mapToDouble(v -> v).max().orElse(0.0);

        double var95 = riskService.calculateVaR(finals, 0.95);
        double cvar95 = riskService.calculateCVaR(finals, 0.95);

        logArea.appendText("=== WYNIK PORTFELA INWESTYCYJNEGO ===\n\n");
        logArea.appendText(String.format("Średni wynik:     %.2f PLN\n", meanVal));
        logArea.appendText(String.format("Maksymalny wynik: %.2f PLN\n", maxVal));
        logArea.appendText(String.format("Minimalny wynik:  %.2f PLN\n\n", minVal));

        logArea.appendText("=== KWANTYFIKACJA RYZYKA ===\n\n");
        logArea.appendText(String.format("VaR 95%%:          %.2f PLN\n", var95));
        logArea.appendText(String.format("CVaR 95%%:         %.2f PLN\n\n", cvar95));

        logArea.appendText(result.infoLog());

        List<List<Double>> paths = result.samplePaths();
        if(!paths.isEmpty()) {
            try {
                double startCapital = Double.parseDouble(capitalInput.getText());
                int lastDayIndex = paths.getFirst().size() - 1;

                XYChart.Series<Number, Number> capitalSeries = new XYChart.Series<>();
                capitalSeries.setName("Kapitał pocz.");

                XYChart.Data<Number, Number> startPoint = new XYChart.Data<>(0, startCapital);
                XYChart.Data<Number, Number> endPoint = new XYChart.Data<>(lastDayIndex, startCapital);
                capitalSeries.getData().addAll(startPoint, endPoint);

                chart.getData().add(capitalSeries);

                Platform.runLater(() -> {
                    Node lineNode = capitalSeries.getNode();
                    if (lineNode != null) {
                        lineNode.setStyle(
                                "-fx-stroke: #333333;" +
                                        "-fx-stroke-width: 1.0px;" +
                                        "-fx-stroke-dash-array: 8 6;"
                        );
                    }

                    for (XYChart.Data<Number, Number> data : capitalSeries.getData()) {
                        if (data.getNode() != null) data.getNode().setVisible(false);
                    }

                    for (Node node : chart.lookupAll(".chart-legend-item")) {
                        if (node instanceof Label label) {
                            if (label.getText().equals("Kapitał pocz.")) {
                                Node symbol = label.getGraphic();
                                if (symbol != null) {
                                    symbol.setStyle("-fx-background-color: #333333, white;");
                                }
                            }
                        }
                    }
                });
            } catch (Exception e) {
            }
            addSeries("Najlepszy", paths.get(findExtremeIndex(finals, true)));
            addSeries("Średni", paths.get(findClosestIndex(finals, meanVal)));
            addSeries("VaR 95%", paths.get(findClosestIndex(finals, var95)));
            addSeries("CVaR 95%", paths.get(findClosestIndex(finals, cvar95)));
        }
    }


    private void addSeries(String name, List<Double> data) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(name);
        int step = Math.max(1, data.size() / 200);
        for (int i = 0; i < data.size(); i += step) {
            series.getData().add(new XYChart.Data<>(i, data.get(i)));
        }
        chart.getData().add(series);
    }


    private int findClosestIndex(List<Double> values, double target) {
        int idx = 0;
        double minDiff = Double.MAX_VALUE;
        for (int i = 0; i < values.size(); i++) {
            double diff = Math.abs(values.get(i) - target);
            if (diff < minDiff) {
                minDiff = diff;
                idx = i;
            }
        }
        return idx;
    }


    private int findExtremeIndex(List<Double> values, boolean findMax) {
        int idx = 0;
        double val = values.getFirst();
        for (int i = 1; i < values.size(); i++) {
            if (findMax ? values.get(i) > val : values.get(i) < val) {
                val = values.get(i);
                idx = i;
            }
        }
        return idx;
    }


    public static void main(String[] args) {
        launch(args);
    }
}