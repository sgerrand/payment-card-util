package com.sgerrand.paymentcardutil.card;

/** The Luhn check digit algorithm (ISO/IEC 7812-1), used to catch typing errors in card numbers. */
public final class Luhn {

    private Luhn() {}

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
        return sum(digits, false) % 10 == 0;
    }

    /**
     * Returns the check digit to append to a number that does not yet have one.
     *
     * @param digitsWithoutCheckDigit a string of ASCII digits
     * @throws IllegalArgumentException if the string holds anything but digits
     */
    public static int checkDigit(String digitsWithoutCheckDigit) {
        return (10 - (sum(digitsWithoutCheckDigit, true) % 10)) % 10;
    }

    /**
     * The Luhn sum: right to left, every other digit doubled and folded back under ten.
     *
     * @param startDoubling whether the rightmost digit is one of the doubled ones. It is when
     *     working out a check digit, and is not when checking a number that already has one
     */
    private static int sum(String digits, boolean startDoubling) {
        int sum = 0;
        boolean doubling = startDoubling;
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
        return sum;
    }

    private static int digitAt(String s, int index) {
        char c = s.charAt(index);
        if (c < '0' || c > '9') {
            throw new IllegalArgumentException("Not a digit at index " + index + ": " + c);
        }
        return c - '0';
    }
}
