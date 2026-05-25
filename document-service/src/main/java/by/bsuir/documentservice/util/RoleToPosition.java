package by.bsuir.documentservice.util;

public final class RoleToPosition {

    private RoleToPosition() {}

    public static String label(String role) {
        if (role == null) return "";
        return switch (role.toUpperCase()) {
            case "WORKER" -> "Кладовщик";
            case "ACCOUNTANT" -> "Бухгалтер";
            case "DIRECTOR" -> "Заведующий складом";
            default -> "";
        };
    }
}
