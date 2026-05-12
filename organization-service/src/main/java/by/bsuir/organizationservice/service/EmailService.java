package by.bsuir.organizationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @Autowired
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public void sendInvitation(String toEmail, String organizationName, String role, String invitationToken) {
        try {
            String inviteLink = frontendUrl + "/register?invite=" + invitationToken;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Приглашение в организацию " + organizationName);
            message.setText(buildInvitationEmailBody(organizationName, role, inviteLink));

            mailSender.send(message);
            log.info("Invitation email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить приглашение на email", e);
        }
    }

    private String buildInvitationEmailBody(String organizationName, String role, String inviteLink) {
        return String.format("""
                Здравствуйте!

                Вы приглашены присоединиться к организации "%s" в системе управления складом.

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