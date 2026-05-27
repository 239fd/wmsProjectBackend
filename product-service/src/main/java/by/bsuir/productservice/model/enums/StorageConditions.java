package by.bsuir.productservice.model.enums;

public enum StorageConditions {
    ROOM("комнатной температуры (15–25 °C)"),
    COOL("прохладного хранения (5–15 °C)"),
    FRIDGE("холодильника (0–5 °C)"),
    FREEZER("морозильника (−18…−24 °C)");

    private final String label;

    StorageConditions(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
