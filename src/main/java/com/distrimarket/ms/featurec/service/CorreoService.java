package com.distrimarket.ms.featurec.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de correos (soporte y promociones). Todo se configura en application.yml:
 * con {@code distrimarket.correo.habilitado=false} no se envía nada y cada correo queda en el log,
 * para poder probar sin credenciales SMTP.
 */
@Slf4j
@Service
public class CorreoService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean habilitado;
    private final String remitente;
    private final String correoSoporte;

    public CorreoService(ObjectProvider<JavaMailSender> mailSender,
                         @Value("${distrimarket.correo.habilitado:false}") boolean habilitado,
                         @Value("${distrimarket.correo.remitente}") String remitente,
                         @Value("${distrimarket.correo.soporte}") String correoSoporte) {
        this.mailSender = mailSender;
        this.habilitado = habilitado;
        this.remitente = remitente;
        this.correoSoporte = correoSoporte;
    }

    public String correoSoporte() {
        return correoSoporte;
    }

    /**
     * Envía un correo de texto. Lanza una excepción si el servidor SMTP lo rechaza, para que el job
     * registre el error y reintente.
     *
     * @param responderA dirección para "Responder" (por ejemplo, quien escribió a soporte); puede ser null
     */
    public void enviar(String para, String asunto, String texto, String responderA) {
        if (!habilitado) {
            log.info("[correo simulado] Para: {} | Asunto: {} | Responder a: {}\n{}", para, asunto, responderA, texto);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("No hay servidor de correo configurado (spring.mail.*).");
        }
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(para);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        if (responderA != null && !responderA.isBlank()) {
            mensaje.setReplyTo(responderA);
        }
        sender.send(mensaje);
        log.info("Correo enviado a {}: {}", para, asunto);
    }
}
