package com.backendbank.backendbank.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenService {

    static final String MENSAJE_INVALIDO = "el token de sesión no es válido";

    private final byte[] secreto;
    private final long expiracionSegundos;

    public TokenService(
            @Value("${seguridad.jwt.secreto:backendbank-secreto-local-de-desarrollo-32b}") String secreto,
            @Value("${seguridad.jwt.expiracion-minutos:60}") long expiracionMinutos) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("seguridad.jwt.secreto debe tener al menos 32 bytes");
        }
        this.secreto = bytes;
        this.expiracionSegundos = expiracionMinutos * 60;
    }

    public String generar(UUID idCliente, String usuario, String rol) {
        long ahora = Instant.now().getEpochSecond();
        String header = codificar("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = codificar("{"
                + "\"sub\":\"" + escapar(idCliente.toString()) + "\","
                + "\"usuario\":\"" + escapar(usuario) + "\","
                + "\"rol\":\"" + escapar(rol) + "\","
                + "\"iat\":" + ahora + ","
                + "\"exp\":" + (ahora + expiracionSegundos)
                + "}");
        String contenido = header + "." + payload;
        return contenido + "." + firmar(contenido);
    }

    public SesionToken validar(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException(MENSAJE_INVALIDO);
        }
        String[] partes = token.split("\\.");
        if (partes.length != 3) {
            throw new TokenInvalidoException(MENSAJE_INVALIDO);
        }
        String contenido = partes[0] + "." + partes[1];
        byte[] firmaRecibida = decodificar(partes[2]);
        if (!MessageDigest.isEqual(firmaRecibida, firmaBytes(contenido))) {
            throw new TokenInvalidoException(MENSAJE_INVALIDO);
        }
        String json = new String(decodificar(partes[1]), StandardCharsets.UTF_8);
        Map<String, Object> datos = JsonParserFactory.getJsonParser().parseMap(json);
        long expira = ((Number) datos.get("exp")).longValue();
        if (Instant.now().getEpochSecond() >= expira) {
            throw new TokenInvalidoException(MENSAJE_INVALIDO);
        }
        return new SesionToken(
                UUID.fromString(String.valueOf(datos.get("sub"))),
                String.valueOf(datos.get("usuario")),
                String.valueOf(datos.get("rol")),
                Instant.ofEpochSecond(expira));
    }

    public record SesionToken(UUID idCliente, String usuario, String rol, Instant expira) {
    }

    public static class TokenInvalidoException extends RuntimeException {

        public TokenInvalidoException(String mensaje) {
            super(mensaje);
        }
    }

    private String firmar(String contenido) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(firmaBytes(contenido));
    }

    private byte[] firmaBytes(String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("no fue posible firmar el token de sesión", ex);
        }
    }

    private String codificar(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] decodificar(String valor) {
        try {
            return Base64.getUrlDecoder().decode(valor);
        } catch (IllegalArgumentException ex) {
            throw new TokenInvalidoException(MENSAJE_INVALIDO);
        }
    }

    private String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
