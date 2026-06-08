package project.matching;

import homework.Publication;
import homework.Subscription;
import homework.SubscriptionCondition;

public final class MatchingEngine {

    private static final double NUMERIC_EQUALITY_EPSILON = 1e-6;

    private MatchingEngine() {
    }

    public static boolean matches(Publication publication, Subscription subscription) {
        for (SubscriptionCondition condition : subscription.getConditions()) {
            if (!matchesCondition(publication, condition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesCondition(Publication publication, SubscriptionCondition condition) {
        String fieldName = condition.getFieldName();
        String operator = condition.getOperator();
        String renderedValue = condition.getRenderedValue();

        switch (fieldName) {
            case "company":
                return compareText(publication.getCompany(), operator, stripQuotes(renderedValue));
            case "date":
                return compareText(publication.getDate(), operator, stripQuotes(renderedValue));
            case "value":
                return compareNumber(publication.getValue(), operator, parseNumber(renderedValue));
            case "drop":
                return compareNumber(publication.getDrop(), operator, parseNumber(renderedValue));
            case "variation":
                return compareNumber(publication.getVariation(), operator, parseNumber(renderedValue));
            default:
                throw new IllegalArgumentException("Unknown subscription field: " + fieldName);
        }
    }

    private static boolean compareText(String left, String operator, String right) {
        switch (operator) {
            case "=":
                return left.equals(right);
            case "!=":
                return !left.equals(right);
            default:
                throw new IllegalArgumentException(
                        "Unsupported operator for text field: " + operator);
        }
    }

    private static boolean compareNumber(double left, String operator, double right) {
        switch (operator) {
            case "=":
                return Math.abs(left - right) < NUMERIC_EQUALITY_EPSILON;
            case "!=":
                return Math.abs(left - right) >= NUMERIC_EQUALITY_EPSILON;
            case ">":
                return left > right;
            case ">=":
                return left >= right;
            case "<":
                return left < right;
            case "<=":
                return left <= right;
            default:
                throw new IllegalArgumentException(
                        "Unsupported operator for numeric field: " + operator);
        }
    }

    private static String stripQuotes(String renderedValue) {
        if (renderedValue.length() >= 2
                && renderedValue.charAt(0) == '"'
                && renderedValue.charAt(renderedValue.length() - 1) == '"') {
            return renderedValue.substring(1, renderedValue.length() - 1);
        }
        return renderedValue;
    }

    private static double parseNumber(String renderedValue) {
        return Double.parseDouble(renderedValue);
    }
}
