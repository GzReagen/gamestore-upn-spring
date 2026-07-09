package com.gamestore.upn.controller;

import com.gamestore.upn.service.DbService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {
    private final DbService db;

    public CategoriaController(DbService db) {
        this.db = db;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return db.query("SELECT id, nombre FROM categorias ORDER BY nombre ASC");
    }

    @PostMapping
    public Map<String, Object> crear(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @RequestBody Map<String, Object> body) {
        db.requireAdminOrGerente(authorization);
        String nombre = body.get("nombre") == null ? "" : String.valueOf(body.get("nombre")).trim();

        if (nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de la categoría es obligatorio.");
        }

        Map<String, Object> existe = db.one(
                "SELECT id, nombre FROM categorias WHERE LOWER(nombre) = LOWER(?) LIMIT 1",
                nombre
        );

        if (existe != null) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("message", "La categoría ya existía.");
            res.put("categoria", existe);
            res.put("yaExistia", true);
            return res;
        }

        db.jdbc().update("INSERT INTO categorias (nombre) VALUES (?)", nombre);
        Map<String, Object> categoria = db.one("SELECT id, nombre FROM categorias WHERE LOWER(nombre) = LOWER(?) LIMIT 1", nombre);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Categoría creada correctamente.");
        res.put("categoria", categoria);
        res.put("yaExistia", false);
        return res;
    }
}
