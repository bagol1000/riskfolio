public class Instrument {
    String name;
    String ticker;

    public Instrument(String name, String ticker) {
        this.name = name;
        this.ticker = ticker;
    }

    @Override
    public String toString() {
        return name;
    }
}