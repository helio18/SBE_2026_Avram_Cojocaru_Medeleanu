package homework;

import java.util.Locale;

public class SubscriptionCondition {

    private final String fieldName;
    private final String operator;
    private final String rawValue;
    private final boolean quotedValue;

    private SubscriptionCondition(String fieldName, String operator, String rawValue, boolean quotedValue) {
        this.fieldName = fieldName;
        this.operator = operator;
        this.rawValue = rawValue;
        this.quotedValue = quotedValue;
    }

    public static SubscriptionCondition text(String fieldName, String operator, String value) {
        return new SubscriptionCondition(fieldName, operator, value, true);
    }

    public static SubscriptionCondition number(String fieldName, String operator, double value, int decimals) {
        String format = "%." + decimals + "f";
        return new SubscriptionCondition(fieldName, operator, String.format(Locale.US, format, value), false);
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOperator() {
        return operator;
    }

    public String getRawValue() {
        return rawValue;
    }

    public boolean matches(Publication publication) {
        switch (fieldName) {
            case "company":
                return compareText(publication.getCompany(), rawValue);
            case "date":
                return compareText(publication.getDate(), rawValue);
            case "value":
                return compareNumber(publication.getValue(), Double.parseDouble(rawValue));
            case "drop":
                return compareNumber(publication.getDrop(), Double.parseDouble(rawValue));
            case "variation":
                return compareNumber(publication.getVariation(), Double.parseDouble(rawValue));
            default:
                return false;
        }
    }

    public String toFileToken() {
        if (quotedValue) {
            return "(" + fieldName + "," + operator + ",\"" + rawValue + "\")";
        }
        return "(" + fieldName + "," + operator + "," + rawValue + ")";
    }

    private boolean compareText(String publicationValue, String subscriptionValue) {
        int comparison = publicationValue.compareTo(subscriptionValue);

        switch (operator) {
            case "=":
                return publicationValue.equals(subscriptionValue);
            case "!=":
                return !publicationValue.equals(subscriptionValue);
            case ">":
                return comparison > 0;
            case ">=":
                return comparison >= 0;
            case "<":
                return comparison < 0;
            case "<=":
                return comparison <= 0;
            default:
                return false;
        }
    }

    private boolean compareNumber(double publicationValue, double subscriptionValue) {
        switch (operator) {
            case "=":
                return Double.compare(publicationValue, subscriptionValue) == 0;
            case "!=":
                return Double.compare(publicationValue, subscriptionValue) != 0;
            case ">":
                return publicationValue > subscriptionValue;
            case ">=":
                return publicationValue >= subscriptionValue;
            case "<":
                return publicationValue < subscriptionValue;
            case "<=":
                return publicationValue <= subscriptionValue;
            default:
                return false;
        }
    }
}
