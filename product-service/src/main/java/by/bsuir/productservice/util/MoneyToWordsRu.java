package by.bsuir.productservice.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyToWordsRu {

    private MoneyToWordsRu() {}

    private static final String[] UNITS_M = {
        "", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"
    };
    private static final String[] UNITS_F = {
        "", "одна", "две", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"
    };
    private static final String[] TEENS = {
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
    };
    private static final String[] TENS = {
        "", "", "двадцать", "тридцать", "сорок", "пятьдесят",
        "шестьдесят", "семьдесят", "восемьдесят", "девяносто"
    };
    private static final String[] HUNDREDS = {
        "", "сто", "двести", "триста", "четыреста", "пятьсот",
        "шестьсот", "семьсот", "восемьсот", "девятьсот"
    };

    public static String rubles(BigDecimal amount) {
        BigDecimal value = (amount != null ? amount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        long rub = value.longValue();
        int kop = value.subtract(BigDecimal.valueOf(rub)).movePointRight(2).abs().intValue();

        String words = integerWords(rub)
                + " " + plural(rub, "рубль", "рубля", "рублей")
                + " " + String.format("%02d", kop)
                + " " + plural(kop, "копейка", "копейки", "копеек");
        return capitalize(words.trim().replaceAll(" +", " "));
    }

    private static String integerWords(long n) {
        if (n == 0) return "ноль";
        StringBuilder sb = new StringBuilder();
        long millions = n / 1_000_000;
        long thousands = (n / 1000) % 1000;
        long rest = n % 1000;
        if (millions > 0) {
            sb.append(tripletWords((int) millions, true))
              .append(' ').append(plural(millions, "миллион", "миллиона", "миллионов")).append(' ');
        }
        if (thousands > 0) {
            sb.append(tripletWords((int) thousands, false))
              .append(' ').append(plural(thousands, "тысяча", "тысячи", "тысяч")).append(' ');
        }
        if (rest > 0) {
            sb.append(tripletWords((int) rest, true));
        }
        return sb.toString().trim().replaceAll(" +", " ");
    }

    private static String tripletWords(int n, boolean masculine) {
        StringBuilder sb = new StringBuilder();
        int h = n / 100;
        int t = (n % 100) / 10;
        int u = n % 10;
        if (h > 0) sb.append(HUNDREDS[h]).append(' ');
        if (t == 1) {
            sb.append(TEENS[u]).append(' ');
        } else {
            if (t > 0) sb.append(TENS[t]).append(' ');
            if (u > 0) sb.append((masculine ? UNITS_M : UNITS_F)[u]).append(' ');
        }
        return sb.toString().trim();
    }

    private static String plural(long n, String one, String few, String many) {
        long abs = Math.abs(n) % 100;
        if (abs >= 11 && abs <= 14) return many;
        long last = abs % 10;
        if (last == 1) return one;
        if (last >= 2 && last <= 4) return few;
        return many;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
