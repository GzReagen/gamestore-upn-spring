package com.gamestore.upn.controller;

import com.gamestore.upn.service.DbService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final DbService db;

    public AuthController(DbService db) {
        this.db = db;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String loginUsuario = texto(body.get("usuario"));
        if (loginUsuario.isBlank()) loginUsuario = texto(body.get("username"));
        if (loginUsuario.isBlank()) loginUsuario = texto(body.get("correo"));
        String password = texto(body.get("password"));

        if (loginUsuario.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe ingresar usuario y contraseña.");
        }

        Map<String, Object> usuario = db.one(
                "SELECT id, nombre, correo, usuario, password, rol FROM usuarios WHERE usuario = ? OR correo = ? LIMIT 1",
                loginUsuario,
                loginUsuario
        );

        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado.");
        }

        if (!password.equals(String.valueOf(usuario.get("password")))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contraseña incorrecta.");
        }

        Map<String, Object> payload = usuarioPayload(usuario);
        String token = db.generarToken(payload);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Login correcto.");
        res.put("token", token);
        res.put("usuario", payload);
        res.put("user", payload);
        return res;
    }

    @PostMapping("/registro")
    public Map<String, Object> registro(@RequestBody Map<String, Object> body) {
        String nombre = texto(body.get("nombre"));
        String correo = texto(body.get("correo"));
        String usuario = texto(body.get("usuario"));
        String password = texto(body.get("password"));

        if (nombre.isBlank() || correo.isBlank() || usuario.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todos los campos son obligatorios.");
        }

        Map<String, Object> existe = db.one("SELECT id FROM usuarios WHERE usuario = ? OR correo = ? LIMIT 1", usuario, correo);
        if (existe != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario o correo ya existe.");
        }

        db.jdbc().update(
                "INSERT INTO usuarios (nombre, correo, usuario, password, rol) VALUES (?, ?, ?, ?, 'CLIENTE')",
                nombre,
                correo,
                usuario,
                password
        );

        return Map.of("message", "Cliente registrado correctamente.");
    }

    private Map<String, Object> usuarioPayload(Map<String, Object> row) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", row.get("id"));
        user.put("nombre", row.get("nombre"));
        user.put("correo", row.get("correo"));
        user.put("usuario", row.get("usuario"));
        user.put("username", row.get("usuario"));
        user.put("rol", row.get("rol"));
        return user;
    }

    private String texto(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
