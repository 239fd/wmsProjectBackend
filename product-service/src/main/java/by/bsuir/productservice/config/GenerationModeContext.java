package by.bsuir.productservice.config;

public final class GenerationModeContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private GenerationModeContext() { }

    public static void set(String mode) {
        CURRENT.set(mode);
    }

    public static String current() {
        String mode = CURRENT.get();
        return mode != null ? mode : "auto";
    }

    public static void clear() {
        CURRENT.remove();
    }
}
