package homework;

import java.util.Locale;

public class Publication {

    private final String company;
    private final double value;
    private final double drop;
    private final double variation;
    private final String date;

    public Publication(String company, double value, double drop, double variation, String date) {
        this.company = company;
        this.value = value;
        this.drop = drop;
        this.variation = variation;
        this.date = date;
    }

    public String getCompany() {
        return company;
    }

    public double getValue() {
        return value;
    }

    public double getDrop() {
        return drop;
    }

    public double getVariation() {
        return variation;
    }

    public String getDate() {
        return date;
    }

    public String toFileLine(int index) {
        return String.format(
                Locale.US,
                "Publication %d: {(company,\"%s\");(value,%.2f);(drop,%.2f);(variation,%.4f);(date,\"%s\")}",
                index,
                company,
                value,
                drop,
                variation,
                date);
    }
}
