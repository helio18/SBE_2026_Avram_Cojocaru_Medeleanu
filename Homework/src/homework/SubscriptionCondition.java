package homework;

import java.util.Locale;

public class SubscriptionCondition {

    private final String fieldName;
    private final String operator;
    private final String renderedValue;

    private SubscriptionCondition(String fieldName, String operator, String renderedValue) {
        this.fieldName = fieldName;
        this.operator = operator;
        this.renderedValue = renderedValue;
    }

    public static SubscriptionCondition text(String fieldName, String operator, String value) {
        return new SubscriptionCondition(fieldName, operator, "\"" + value + "\"");
    }

    public static SubscriptionCondition number(String fieldName, String operator, double value, int decimals) {
        String format = "%." + decimals + "f";
        return new SubscriptionCondition(fieldName, operator, String.format(Locale.US, format, value));
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOperator() {
        return operator;
    }

    public String toFileToken() {
        return "(" + fieldName + "," + operator + "," + renderedValue + ")";
    }
}
