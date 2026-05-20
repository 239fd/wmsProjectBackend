package by.bsuir.documentservice.rpa;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Slf4j
@Service
public class HtmlPdfRenderer {

    private static final String TEMPLATE_PREFIX = "templates/pdf/";
    private static final String TEMPLATE_SUFFIX = ".html";

    private final TemplateEngine templateEngine = new TemplateEngine();
    private Path regularFontFile;
    private Path boldFontFile;
    private String baseCss = "";

    @PostConstruct
    void init() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(TEMPLATE_PREFIX);
        resolver.setSuffix(TEMPLATE_SUFFIX);
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        templateEngine.setTemplateResolver(resolver);

        try {
            regularFontFile = extractFontToTemp("fonts/DejaVuSans.ttf", "DejaVuSans");
            boldFontFile = extractFontToTemp("fonts/DejaVuSans-Bold.ttf", "DejaVuSans-Bold");
            log.info("PDF fonts extracted: regular={} bold={}", regularFontFile, boldFontFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract embedded PDF fonts", e);
        }

        try (InputStream in = new ClassPathResource("templates/pdf/styles/base.css").getInputStream()) {
            baseCss = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to load base.css: {}", e.getMessage());
        }
    }

    public byte[] render(String templateName, Map<String, Object> data) {
        Context context = new Context();
        context.setVariables(data);
        context.setVariable("cssBase", baseCss);
        String html = templateEngine.process(templateName, context);
        return htmlToPdf(html);
    }

    public String renderHtml(String templateName, Map<String, Object> data) {
        Context context = new Context();
        context.setVariables(data);
        context.setVariable("cssBase", baseCss);
        return templateEngine.process(templateName, context);
    }

    private byte[] htmlToPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(regularFontFile.toFile(), "DejaVuSans", 400,
                    com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(boldFontFile.toFile(), "DejaVuSans", 700,
                    com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PDF", e);
        }
    }

    private Path extractFontToTemp(String classpath, String prefix) throws IOException {
        ClassPathResource res = new ClassPathResource(classpath);
        Path tmp = Files.createTempFile(prefix + "-", ".ttf");
        tmp.toFile().deleteOnExit();
        try (InputStream in = res.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }
}
