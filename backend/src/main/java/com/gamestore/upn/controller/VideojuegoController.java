package com.gamestore.upn.controller;

import com.gamestore.upn.service.DbService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/videojuegos")
public class VideojuegoController {
    private final DbService db;

    public VideojuegoController(DbService db) {
        this.db = db;
    }

    @GetMapping
    public List<Map<String, Object>> listarCatalogo() {
        return db.query(sqlVideojuegos("WHERE COALESCE(v.activo, 1) = 1", "ORDER BY v.id DESC"));
    }

    @GetMapping("/admin/lista")
    public List<Map<String, Object>> listarAdmin(@RequestHeader(value = "Authorization", required = false) String authorization) {
        db.requireAdminOrGerente(authorization);
        return db.query(sqlVideojuegos("", "ORDER BY COALESCE(v.activo, 1) DESC, v.id DESC"));
    }

    @GetMapping("/{id}")
    public Map<String, Object> obtener(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable Integer id) {
        db.requireAdminOrGerente(authorization);
        List<Map<String, Object>> rows = db.query(sqlVideojuegos("WHERE v.id = ?", "LIMIT 1"), id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Videojuego no encontrado.");
        }
        return rows.get(0);
    }

    @PostMapping
    @Transactional
    public Map<String, Object> crear(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @RequestBody Map<String, Object> body) {
        db.requireAdminOrGerente(authorization);
        validar(body);

        List<Integer> categorias = categorias(body);
        Integer categoriaPrincipal = categorias.isEmpty() ? numero(body.get("categoria_id")) : categorias.get(0);
        if (categoriaPrincipal == null) categoriaPrincipal = numero(body.get("categoriaId"));
        if (categoriaPrincipal == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre, precio, stock y categoría son obligatorios.");
        }

        db.jdbc().update(
                "INSERT INTO videojuegos (nombre, descripcion, precio, stock, imagen, categoria_id, activo) VALUES (?, ?, ?, ?, ?, ?, 1)",
                texto(body.get("nombre")),
                texto(body.get("descripcion")),
                decimal(body.get("precio")),
                numero(body.get("stock")),
                texto(body.get("imagen")),
                categoriaPrincipal
        );

        Integer id = db.jdbc().queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        guardarCategorias(id, categorias.isEmpty() ? List.of(categoriaPrincipal) : categorias);

        return Map.of("message", "Videojuego creado correctamente.", "id", id);
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> actualizar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @PathVariable Integer id,
                                          @RequestBody Map<String, Object> body) {
        db.requireAdminOrGerente(authorization);
        validar(body);

        Map<String, Object> existe = db.one("SELECT id FROM videojuegos WHERE id = ? LIMIT 1", id);
        if (existe == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Videojuego no encontrado.");
        }

        List<Integer> categorias = categorias(body);
        Integer categoriaPrincipal = categorias.isEmpty() ? numero(body.get("categoria_id")) : categorias.get(0);
        if (categoriaPrincipal == null) categoriaPrincipal = numero(body.get("categoriaId"));
        if (categoriaPrincipal == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre, precio, stock y categoría son obligatorios.");
        }

        db.jdbc().update(
                "UPDATE videojuegos SET nombre = ?, descripcion = ?, precio = ?, stock = ?, imagen = ?, categoria_id = ? WHERE id = ?",
                texto(body.get("nombre")),
                texto(body.get("descripcion")),
                decimal(body.get("precio")),
                numero(body.get("stock")),
                texto(body.get("imagen")),
                categoriaPrincipal,
                id
        );

        guardarCategorias(id, categorias.isEmpty() ? List.of(categoriaPrincipal) : categorias);
        return Map.of("message", "Videojuego actualizado correctamente.");
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> quitar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable Integer id) {
        db.requireAdminOrGerente(authorization);
        Map<String, Object> existe = db.one("SELECT id FROM videojuegos WHERE id = ? LIMIT 1", id);
        if (existe == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Videojuego no encontrado.");
        }
        db.jdbc().update("UPDATE videojuegos SET activo = 0 WHERE id = ?", id);
        return Map.of("message", "Videojuego quitado del catálogo. El historial de ventas se mantiene.");
    }

    @PutMapping("/restaurar/{id}")
    public Map<String, Object> restaurar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable Integer id) {
        db.requireAdminOrGerente(authorization);
        Map<String, Object> existe = db.one("SELECT id FROM videojuegos WHERE id = ? LIMIT 1", id);
        if (existe == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Videojuego no encontrado.");
        }
        db.jdbc().update("UPDATE videojuegos SET activo = 1 WHERE id = ?", id);
        return Map.of("message", "Videojuego reincorporado al catálogo correctamente.");
    }

    private String sqlVideojuegos(String where, String order) {
        return """
                SELECT
                  v.id,
                  v.nombre,
                  v.descripcion,
                  v.precio,
                  v.stock,
                  v.imagen,
                  v.categoria_id,
                  COALESCE(v.activo, 1) AS activo,
                  COALESCE(
                    NULLIF(GROUP_CONCAT(DISTINCT vc.categoria_id ORDER BY vc.categoria_id SEPARATOR ','), ''),
                    CAST(v.categoria_id AS CHAR)
                  ) AS categoria_ids,
                  COALESCE(
                    NULLIF(GROUP_CONCAT(DISTINCT c2.nombre ORDER BY c2.nombre SEPARATOR ', '), ''),
                    c.nombre,
                    'Sin categoría'
                  ) AS categorias_nombre,
                  COALESCE(
                    NULLIF(GROUP_CONCAT(DISTINCT c2.nombre ORDER BY c2.nombre SEPARATOR ', '), ''),
                    c.nombre,
                    'Sin categoría'
                  ) AS categoria_nombre
                FROM videojuegos v
                LEFT JOIN categorias c ON c.id = v.categoria_id
                LEFT JOIN videojuego_categorias vc ON vc.videojuego_id = v.id
                LEFT JOIN categorias c2 ON c2.id = vc.categoria_id
                """ + where + "\n" +
                "GROUP BY v.id, v.nombre, v.descripcion, v.precio, v.stock, v.imagen, v.categoria_id, v.activo, c.nombre\n" +
                order;
    }

    private void guardarCategorias(Integer videojuegoId, List<Integer> categorias) {
        db.jdbc().update("DELETE FROM videojuego_categorias WHERE videojuego_id = ?", videojuegoId);
        for (Integer categoriaId : new LinkedHashSet<>(categorias)) {
            if (categoriaId != null) {
                db.jdbc().update("INSERT IGNORE INTO videojuego_categorias (videojuego_id, categoria_id) VALUES (?, ?)", videojuegoId, categoriaId);
            }
        }
    }

    private void validar(Map<String, Object> body) {
        if (texto(body.get("nombre")).isBlank() || body.get("precio") == null || body.get("stock") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre, precio, stock y categoría son obligatorios.");
        }
    }

    private List<Integer> categorias(Map<String, Object> body) {
        Object raw = body.get("categoria_ids");
        if (raw == null) raw = body.get("categoriaIds");
        List<Integer> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Integer n = numero(item);
                if (n != null) result.add(n);
            }
        } else if (raw != null) {
            String[] parts = String.valueOf(raw).split(",");
            for (String p : parts) {
                Integer n = numero(p.trim());
                if (n != null) result.add(n);
            }
        }
        return result;
    }

    private String texto(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer numero(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return Integer.valueOf(String.valueOf(value).trim());
    }

    private BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(value));
    }
}
