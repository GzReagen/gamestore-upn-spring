package com.gamestore.upn.controller;

import com.gamestore.upn.service.DbService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compras")
public class CompraController {
    private final DbService db;

    public CompraController(DbService db) {
        this.db = db;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> compraDirecta(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestBody Map<String, Object> body) {
        Map<String, Object> user = db.requireCliente(authorization);
        Integer videojuegoId = numero(body.get("videojuegoId"));
        if (videojuegoId == null) videojuegoId = numero(body.get("videojuego_id"));
        int cantidad = body.get("cantidad") == null ? 1 : numero(body.get("cantidad"));

        if (videojuegoId == null || cantidad <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datos de compra inválidos.");
        }

        Map<String, Object> juego = db.one("SELECT id, nombre, precio, stock FROM videojuegos WHERE id = ? LIMIT 1", videojuegoId);
        if (juego == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Videojuego no encontrado.");
        }
        if (numero(juego.get("stock")) < cantidad) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock insuficiente.");
        }

        BigDecimal precioUnitario = decimal(juego.get("precio"));
        BigDecimal total = precioUnitario.multiply(BigDecimal.valueOf(cantidad));

        db.jdbc().update("INSERT INTO ventas (usuario_id, fecha, total) VALUES (?, NOW(), ?)", user.get("id"), total);
        Integer ventaId = db.jdbc().queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

        if (db.hasColumn("detalle_venta", "subtotal")) {
            db.jdbc().update("INSERT INTO detalle_venta (venta_id, videojuego_id, cantidad, precio_unitario, subtotal) VALUES (?, ?, ?, ?, ?)", ventaId, videojuegoId, cantidad, precioUnitario, total);
        } else {
            db.jdbc().update("INSERT INTO detalle_venta (venta_id, videojuego_id, cantidad, precio_unitario) VALUES (?, ?, ?, ?)", ventaId, videojuegoId, cantidad, precioUnitario);
        }

        db.jdbc().update("UPDATE videojuegos SET stock = stock - ? WHERE id = ?", cantidad, videojuegoId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Compra realizada correctamente.");
        res.put("ventaId", ventaId);
        return res;
    }

    @GetMapping("/historial")
    public List<Map<String, Object>> historial(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = db.requireCliente(authorization);
        return db.query(
                """
                SELECT
                  dv.id AS detalle_id,
                  v.id AS venta_id,
                  v.fecha,
                  vg.nombre AS videojuego_nombre,
                  vg.imagen AS videojuego_imagen,
                  dv.cantidad,
                  COALESCE(dv.precio_unitario, vg.precio) AS precio_unitario,
                  (COALESCE(dv.precio_unitario, vg.precio) * dv.cantidad) AS total
                FROM ventas v
                INNER JOIN detalle_venta dv ON dv.venta_id = v.id
                INNER JOIN videojuegos vg ON vg.id = dv.videojuego_id
                WHERE v.usuario_id = ?
                ORDER BY v.fecha DESC, dv.id DESC
                """,
                user.get("id")
        );
    }

    private Integer numero(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return Integer.valueOf(String.valueOf(value));
    }

    private BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(value));
    }
}
