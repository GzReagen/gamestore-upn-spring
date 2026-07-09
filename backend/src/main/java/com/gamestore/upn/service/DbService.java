package com.gamestore.upn.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DbService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms:28800000}")
    private long expirationMs;

    public DbService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public List<Map<String, Object>> query(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.get(0));
    }

    public boolean hasColumn(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                table,
                column
        );
        return count != null && count > 0;
    }

    public String generarToken(Map<String, Object> usuario) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", usuario.get("id"));
            payload.put("nombre", usuario.get("nombre"));
            payload.put("correo", usuario.get("correo"));
            payload.put("usuario", usuario.get("usuario"));
            payload.put("rol", usuario.get("rol"));
            payload.put("exp", Instant.now().toEpochMilli() + expirationMs);

            String json = mapper.writeValueAsString(payload);
            String data = base64Url(json.getBytes(StandardCharsets.UTF_8));
            String firma = firmar(data);
            return data + "." + firma;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar token.");
        }
    }

    public Map<String, Object> validarToken(String header) {
        try {
            if (header == null || !header.startsWith("Bearer ")) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token no enviado.");
            }

            String token = header.substring(7).trim();
            String[] partes = token.split("\\.");
            if (partes.length != 2) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido.");
            }

            if (!firmar(partes[0]).equals(partes[1])) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido.");
            }

            String json = new String(Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8);
            Map<String, Object> payload = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            Number exp = (Number) payload.get("exp");
            if (exp == null || exp.longValue() < Instant.now().toEpochMilli()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión expirada.");
            }

            Number id = (Number) payload.get("id");
            if (id == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido.");
            }

            Map<String, Object> usuario = one("SELECT id, nombre, correo, usuario, rol FROM usuarios WHERE id = ? LIMIT 1", id.longValue());
            if (usuario == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado.");
            }

            return usuario;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido.");
        }
    }

    public Map<String, Object> requireAuth(String authorization) {
        return validarToken(authorization);
    }

    public Map<String, Object> requireCliente(String authorization) {
        Map<String, Object> user = validarToken(authorization);
        if (!"CLIENTE".equalsIgnoreCase(String.valueOf(user.get("rol")))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso permitido solo para cliente.");
        }
        return user;
    }

    public Map<String, Object> requireAdminOrGerente(String authorization) {
        Map<String, Object> user = validarToken(authorization);
        String rol = String.valueOf(user.get("rol")).toUpperCase();
        if (!rol.equals("ADMIN") && !rol.equals("GERENTE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso permitido solo para administrador o gerente.");
        }
        return user;
    }

    public Map<String, Object> requireGerente(String authorization) {
        Map<String, Object> user = validarToken(authorization);
        if (!"GERENTE".equalsIgnoreCase(String.valueOf(user.get("rol")))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso permitido solo para gerente.");
        }
        return user;
    }

    private String firmar(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return base64Url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
