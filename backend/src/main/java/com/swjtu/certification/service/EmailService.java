package com.swjtu.certification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * 异步发送 HTML 邮件
     */
    @Async
    public void sendHtmlMail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("924107131@qq.com");   // 必须与配置的 username 一致
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);      // true 表示发送 HTML
            mailSender.send(message);
            System.out.println("邮件发送成功: " + to);
        } catch (MessagingException e) {
            System.err.println("邮件发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}