package jp.mamekun.memories.api.service;

import org.springframework.mail.MailException;

public interface EmailService {
    void sendTextMail(String from, String to, String subject, String text) throws MailException;
    void sendHtmlMail(String from, String fromName, String to, String subject, String text) throws MailException;
}
