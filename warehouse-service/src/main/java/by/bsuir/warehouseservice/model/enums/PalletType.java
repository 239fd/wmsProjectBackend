package by.bsuir.warehouseservice.model.enums;

import java.math.BigDecimal;

public enum PalletType {
    EUR(new BigDecimal("80.00"), new BigDecimal("120.00"), new BigDecimal("14.50")),
    FIN(new BigDecimal("100.00"), new BigDecimal("120.00"), new BigDecimal("14.50")),
    US(new BigDecimal("120.00"), new BigDecimal("120.00"), new BigDecimal("14.50")),
    ASIA(new BigDecimal("110.00"), new BigDecimal("110.00"), new BigDecimal("14.50"));

    private final BigDecimal lengthCm;
    private final BigDecimal widthCm;
    private final BigDecimal heightCm;

    PalletType(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
    }

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public BigDecimal getWidthCm() {
        return widthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }
}
