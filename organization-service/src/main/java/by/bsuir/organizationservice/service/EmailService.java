package by.bsuir.organizationservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.mail.from-name:WMS}")
    private String fromName;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public void sendInvitation(String toEmail, String organizationName, String role, String invitationToken) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException(
                    "spring.mail.username не настроен — задайте MAIL_USERNAME в .env");
        }

        String inviteLink = frontendUrl + "/register/invitation?token=" + invitationToken;
        String subject = "Приглашение в организацию " + organizationName;
        String body = buildInvitationEmailBody(organizationName, role, inviteLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, false);

            mailSender.send(message);
            log.info("Invitation email sent via SMTP to: {}", toEmail);
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send invitation email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить приглашение на email: " + e.getMessage(), e);
        }
    }

    private String buildInvitationEmailBody(String organizationName, String role, String inviteLink) {
        return String.format("""
                Здравствуйте!

                Вы приглашены присоединиться к организации "%s" в системе управления складом (WMS).

                Ваша роль: %s

                Для завершения регистрации перейдите по ссылке:
                %s

                Ссылка действительна в течение 7 дней.

                Если вы не ожидали это приглашение, просто проигнорируйте это письмо.

                С уважением,
                Команда WMS
                """, organizationName, translateRole(role), inviteLink);
    }

    private String translateRole(String role) {
        return switch (role) {
            case "WORKER" -> "Кладовщик";
            case "ACCOUNTANT" -> "Бухгалтер";
            case "DIRECTOR" -> "Директор";
            default -> role;
        };
    }
}
