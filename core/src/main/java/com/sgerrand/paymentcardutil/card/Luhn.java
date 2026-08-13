package com.sgerrand.paymentcardutil.card;

/**
 * The Luhn check digit algorithm (ISO/IEC 7812-1), used to catch typing errors
 * in card numbers.
 */
public final class Luhn {

    private Luhn() {
    }

    /**
     * Returns {@code true} if the digits pass the Luhn check.
     *
     * @param digits a string of ASCII digits, at least two long
     * @throws IllegalArgumentException if the string holds anything but digits
     */
    public static boolean isValid(String digits) {
        if (digits == null || digits.length() < 2) {
            return false;
        }
        int sum = 0;
        boolean doubling = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digitAt(digits, i);
            if (doubling) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubling = !doubling;
        }
        return sum % 10 == 0;
    }

    /**
     * Returns the check digit to append to a number that does not yet have one.
     *
     * @param digitsWithoutCheckDigit a string of ASCII digits
     * @throws IllegalArgumentException if the string holds anything but digits
     */
    public static int checkDigit(String digitsWithoutCheckDigit) {
        int sum = 0;
        boolean doubling = true;
        for (int i = digitsWithoutCheckDigit.length() - 1; i >= 0; i--) {
            int digit = digitAt(digitsWithoutCheckDigit, i);
            if (doubling) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubling = !doubling;
        }
        return (10 - (sum % 10)) % 10;
    }

    private static int digitAt(String s, int index) {
        char c = s.charAt(index);
        if (c < '0' || c > '9') {
            throw new IllegalArgumentException("Not a digit at index " + index + ": " + c);
        }
        return c - '0';
    }
}
