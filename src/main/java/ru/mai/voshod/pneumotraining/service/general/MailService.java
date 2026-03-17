package ru.mai.voshod.pneumotraining.service.general;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String username;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean send(String emailTo, String subject, String message) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(username);
            mailMessage.setTo(emailTo);
            mailMessage.setSubject(subject);
            mailMessage.setText(message);
            mailSender.send(mailMessage);
            return true;
        } catch (MailException e) {
            log.error("Ошибка при отправке email на адрес {}: {}", emailTo, e.getMessage(), e);
            return false;
        }
    }

    public boolean sendHTML(String emailTo, String subject, String message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(username);
            helper.setTo(emailTo);
            helper.setSubject(subject);
            helper.setText(message, true);
            mailSender.send(mimeMessage);
            return true;
        } catch (MessagingException | MailException e) {
            log.error("Ошибка при отправке HTML-сообщения: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean sendOneTimePasswordMail(String username, String oneTimePassword) {
        String message = String.format("Здравствуйте. Ваш одноразовый пароль: %s", oneTimePassword);
        return send(username, "Код активации", message);
    }

    public boolean sendResetPasswordMail(String username, String oneTimePassword) {
        String message = String.format("Здравствуйте. Ваш пароль был сброшен. Новый пароль: %s", oneTimePassword);
        return send(username, "Пароль был сброшен", message);
    }

}
