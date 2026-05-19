package by.bsuir.documentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rpa")
@Getter
@Setter
public class RpaProperties {

    private final Templates templates = new Templates();
    private final Python python = new Python();

    @Getter
    @Setter
    public static class Templates {
        private String dir = "document-service/documents template/";
        private String receiptOrder = "Приходной ордер.XLS";
        private String revaluationAct = "акт переоценки.xls";
        private String inventoryReport = "инвентарихационная опись.xls";
        private String writeOffAct = "списание.docx";
        private String waybill = "ттнls.xls";
        private String waybillHorizontal = "ttn-gor.xls";
        private String waybillVertical = "ttn-vert.xls";
        private String receiptActNormal = "Акт приемки.RTF";
        private String receiptActDiscrepancy = "Акт расхождения.xls";
        private String invoice = "blank-invojs.doc";
        private String transportNoteHorizontal = "tn-gor.xls";
        private String transportNoteVertical = "tn-vert.xls";
        private String cmr = "CMR Международная товарно-транспортная накладная.doc";
    }

    @Getter
    @Setter
    public static class Python {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8060";
        private int timeoutSeconds = 120;
    }
}