package by.bsuir.documentservice.util;

public final class FullNameFormatter {

    private FullNameFormatter() {}

    public static String shortName(String fullName) {
        if (fullName == null) return "";
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) return "";
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) return parts[0];
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length && i <= 2; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            sb.append(' ').append(Character.toUpperCase(part.charAt(0))).append('.');
        }
        return sb.toString();
    }

    public static String shortNameWithPosition(String fullName, String position) {
        String name = shortName(fullName);
        if (name.isEmpty() && (position == null || position.isEmpty())) return "";
        if (name.isEmpty()) return position;
        if (position == null || position.isEmpty()) return name;
        return name + ", " + position;
    }
}
