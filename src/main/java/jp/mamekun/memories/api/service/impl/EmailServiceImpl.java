package jp.mamekun.memories.api.service.impl;

import jp.mamekun.memories.api.service.EmailService;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender emailSender;

    public EmailServiceImpl(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public void sendTextMail(String from, String to, String subject, String text) throws MailException {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        emailSender.send(message);
    }

    @Override
    public void sendHtmlMail(String from, String fromName, String to, String subject, String text) throws MailException {
        MimeMailMessage message = new MimeMailMessage(emailSender.createMimeMessage());
        MimeMessageHelper helper = message.getMimeMessageHelper();

        try {
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, true);

            emailSender.send(message.getMimeMessage());
        } catch (Exception e) {
            throw new MailException("Failed to send email") {};
        }
    }
}
