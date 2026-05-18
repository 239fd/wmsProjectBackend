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

    private final Office office = new Office();
    private final Templates templates = new Templates();
    private final Onec onec = new Onec();

    @Getter
    @Setter
    public static class Office {
        private boolean enabled;
        private String driverUrl = "http://127.0.0.1:4723";
        private String excelExe = "C:\\Program Files\\Microsoft Office\\root\\Office16\\EXCEL.EXE";
        private String wordExe = "C:\\Program Files\\Microsoft Office\\root\\Office16\\WINWORD.EXE";
        private String downloadsDir = "logs/rpa-office";
        private int waitSeconds = 20;
    }

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
    public static class Onec {
        private boolean enabled;
        private String executable = "C:\\Program Files\\1cv8\\common\\1cestart.exe";
        private String baseConnection = "";
        private String username = "";
        private String password = "";
        private String journalName = "Поступление товаров и услуг";
    }
}
