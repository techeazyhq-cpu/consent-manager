package com.techeazyhq.cm.ntf.infra;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.techeazyhq.cm.ntf.dom.EmailSenderPort;
import com.techeazyhq.cm.ntf.dom.NtfMsg;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * {@link EmailSenderPort} adapter — real delivery in Phase 1, via a local Mailpit container
 * (plan §9a). {@code JavaMailSender} is blocking; deliberately isolated on
 * {@code Schedulers.boundedElastic()} rather than run on the WebFlux event loop — the same
 * "isolated blocking I/O" exception already made for Flyway (plan §6).
 */
public class SmtpEmailSenderAdapter implements EmailSenderPort {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSenderAdapter(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public Mono<Void> send(NtfMsg msg) {
        return Mono.<Void>fromRunnable(() -> {
                    SimpleMailMessage mail = new SimpleMailMessage();
                    mail.setFrom(fromAddress);
                    mail.setTo(msg.destination());
                    mail.setSubject(msg.subject());
                    mail.setText(msg.body());
                    mailSender.send(mail);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
