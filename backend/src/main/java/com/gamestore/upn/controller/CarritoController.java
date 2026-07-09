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
@RequestMapping("/api/carrito")
public class CarritoController {
    private final DbService db;

    public CarritoController(DbService db) {
        this.db = db;
    }

    @GetMapping
    public Map<String, Object> ver(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = db.requireCliente(authorization);
        Integer carritoId = obtenerOCrearCarrito(numero(user.get("id")));

        List<Map<String, Object>> items = db.query(
                """
                SELECT
                  dc.id AS detalle_id,
                  dc.id AS id,
                  dc.carrito_id,
                  dc.videojuego_id,
                  dc.cantidad,
                  v.nombre,
                  v.descripcion,
                  v.precio,
                  v.stock,
                  v.imagen,
                  c.nombre AS categoria_nombre
                FROM detalle_carrito dc
                INNER JOIN videojuegos v ON v.id = dc.videojuego_id
                LEFT JOIN categorias c ON c.id = v.categoria_id
                WHERE dc.carrito_id = ?
                ORDER BY dc.id DESC
                """,
                carritoId
        );

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            BigDecimal precio = decimal(item.get("precio"));
            int cantidad = numero(item.get("cantidad"));
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(cantidad));
            item.put("subtotal", subtotal);
            total = total.add(subtotal);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("carritoId", carritoId);
        res.put("items", items);
        res.put("total", total);
        return res;
    }

    @PostMapping("/agregar/{videojuegoId}")
    @Transactional
    public Map<String, Object> agregar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable Integer videojuegoId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> user = db.requireCliente(authorization);
        int cantidad = body == null || body.get("cantidad") == null ? 1 : numero(body.get("cantidad"));
        if (cantidad <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a 0.");
        }

        Map<String, Object> juego = db.one("SELECT id, nombre, stock FROM videojuegos WHERE id = ? LIMIT 1", videojuegoId);
        if (juego == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Videojuego no encontrado.");
        }
        if (numero(juego.get("stock")) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay stock disponible.");
        }

        Integer carritoId = obtenerOCrearCarrito(numero(user.get("id")));
        Map<String, Object> existente = db.one(
                "SELECT id, cantidad FROM detalle_carrito WHERE carrito_id = ? AND videojuego_id = ? LIMIT 1",
                carritoId,
                videojuegoId
        );

        if (existente != null) {
            int nuevaCantidad = numero(existente.get("cantidad")) + cantidad;
            if (nuevaCantidad > numero(juego.get("stock"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay suficiente stock disponible.");
            }
            db.jdbc().update("UPDATE detalle_carrito SET cantidad = ? WHERE id = ?", nuevaCantidad, existente.get("id"));
        } else {
            if (cantidad > numero(juego.get("stock"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay suficiente stock disponible.");
            }
            db.jdbc().update("INSERT INTO detalle_carrito (carrito_id, videojuego_id, cantidad) VALUES (?, ?, ?)", carritoId, videojuegoId, cantidad);
        }

        return Map.of("message", juego.get("nombre") + " agregado al carrito.");
    }

    @PutMapping("/detalle/{detalleId}")
    public Map<String, Object> actualizarDetalle(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @PathVariable Integer detalleId,
                                                 @RequestBody Map<String, Object> body) {
        Map<String, Object> user = db.requireCliente(authorization);
        int cantidad = numero(body.get("cantidad"));

        Map<String, Object> row = db.one(
                """
                SELECT dc.id, dc.videojuego_id, v.stock
                FROM detalle_carrito dc
                INNER JOIN carrito c ON c.id = dc.carrito_id
                INNER JOIN videojuegos v ON v.id = dc.videojuego_id
                WHERE dc.id = ? AND c.usuario_id = ?
                LIMIT 1
                """,
                detalleId,
                user.get("id")
        );
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado en tu carrito.");
        }
        if (cantidad <= 0) {
            db.jdbc().update("DELETE FROM detalle_carrito WHERE id = ?", detalleId);
            return Map.of("message", "Producto eliminado del carrito.");
        }
        if (cantidad > numero(row.get("stock"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay suficiente stock disponible.");
        }
        db.jdbc().update("UPDATE detalle_carrito SET cantidad = ? WHERE id = ?", cantidad, detalleId);
        return Map.of("message", "Cantidad actualizada.");
    }

    @DeleteMapping("/detalle/{detalleId}")
    public Map<String, Object> eliminarDetalle(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @PathVariable Integer detalleId) {
        Map<String, Object> user = db.requireCliente(authorization);
        Map<String, Object> row = db.one(
                """
                SELECT dc.id
                FROM detalle_carrito dc
                INNER JOIN carrito c ON c.id = dc.carrito_id
                WHERE dc.id = ? AND c.usuario_id = ?
                LIMIT 1
                """,
                detalleId,
                user.get("id")
        );
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado en tu carrito.");
        }
        db.jdbc().update("DELETE FROM detalle_carrito WHERE id = ?", detalleId);
        return Map.of("message", "Producto eliminado del carrito.");
    }

    @PostMapping("/comprar")
    @Transactional
    public Map<String, Object> comprarCarrito(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = db.requireCliente(authorization);
        Integer carritoId = obtenerOCrearCarrito(numero(user.get("id")));

        List<Map<String, Object>> items = db.query(
                """
                SELECT dc.id AS detalle_id, dc.videojuego_id, dc.cantidad, v.nombre, v.precio, v.stock
                FROM detalle_carrito dc
                INNER JOIN videojuegos v ON v.id = dc.videojuego_id
                WHERE dc.carrito_id = ?
                """,
                carritoId
        );

        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tu carrito está vacío.");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            int cantidad = numero(item.get("cantidad"));
            int stock = numero(item.get("stock"));
            if (cantidad > stock) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay suficiente stock para " + item.get("nombre") + ".");
            }
            total = total.add(decimal(item.get("precio")).multiply(BigDecimal.valueOf(cantidad)));
        }

        db.jdbc().update("INSERT INTO ventas (usuario_id, fecha, total) VALUES (?, NOW(), ?)", user.get("id"), total);
        Integer ventaId = db.jdbc().queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

        boolean tieneSubtotal = db.hasColumn("detalle_venta", "subtotal");
        for (Map<String, Object> item : items) {
            BigDecimal precio = decimal(item.get("precio"));
            int cantidad = numero(item.get("cantidad"));
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(cantidad));
            if (tieneSubtotal) {
                db.jdbc().update("INSERT INTO detalle_venta (venta_id, videojuego_id, cantidad, precio_unitario, subtotal) VALUES (?, ?, ?, ?, ?)", ventaId, item.get("videojuego_id"), cantidad, precio, subtotal);
            } else {
                db.jdbc().update("INSERT INTO detalle_venta (venta_id, videojuego_id, cantidad, precio_unitario) VALUES (?, ?, ?, ?)", ventaId, item.get("videojuego_id"), cantidad, precio);
            }
            db.jdbc().update("UPDATE videojuegos SET stock = stock - ? WHERE id = ?", cantidad, item.get("videojuego_id"));
        }

        db.jdbc().update("DELETE FROM detalle_carrito WHERE carrito_id = ?", carritoId);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Compra realizada correctamente.");
        res.put("ventaId", ventaId);
        res.put("total", total);
        return res;
    }

    private Integer obtenerOCrearCarrito(Integer usuarioId) {
        Map<String, Object> carrito = db.one("SELECT id FROM carrito WHERE usuario_id = ? ORDER BY id DESC LIMIT 1", usuarioId);
        if (carrito != null) {
            return numero(carrito.get("id"));
        }
        db.jdbc().update("INSERT INTO carrito (usuario_id) VALUES (?)", usuarioId);
        return db.jdbc().queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    private Integer numero(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return 0;
        return Integer.valueOf(String.valueOf(value));
    }

    private BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(value));
    }
}
