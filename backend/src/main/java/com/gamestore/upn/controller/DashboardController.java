package com.gamestore.upn.controller;

import com.gamestore.upn.service.DbService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DbService db;

    public DashboardController(DbService db) {
        this.db = db;
    }

    @GetMapping("/admin")
    public Map<String, Object> dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
        db.requireAdminOrGerente(authorization);

        Map<String, Object> videojuegos = db.one("""
                SELECT
                  COUNT(*) AS totalVideojuegos,
                  SUM(CASE WHEN COALESCE(activo, 1) = 1 THEN 1 ELSE 0 END) AS videojuegosVisibles,
                  SUM(CASE WHEN COALESCE(activo, 1) = 0 THEN 1 ELSE 0 END) AS videojuegosOcultos,
                  SUM(CASE WHEN stock <= 5 AND COALESCE(activo, 1) = 1 THEN 1 ELSE 0 END) AS totalStockBajo
                FROM videojuegos
                """);

        Map<String, Object> ventas = db.one("SELECT COUNT(*) AS totalVentas, COALESCE(SUM(total), 0) AS totalVendido FROM ventas");

        List<Map<String, Object>> ultimasVentas = db.query("""
                SELECT v.id, v.fecha, v.total, u.nombre AS cliente
                FROM ventas v
                INNER JOIN usuarios u ON u.id = v.usuario_id
                ORDER BY v.fecha DESC, v.id DESC
                LIMIT 8
                """);

        List<Map<String, Object>> juegosStockBajo = db.query("""
                SELECT v.id, v.nombre, v.stock, v.precio, v.imagen, c.nombre AS categoria_nombre
                FROM videojuegos v
                LEFT JOIN categorias c ON c.id = v.categoria_id
                WHERE v.stock <= 5 AND COALESCE(v.activo, 1) = 1
                ORDER BY v.stock ASC, v.nombre ASC
                LIMIT 10
                """);

        List<Map<String, Object>> videojuegosMasVendidos = db.query("""
                SELECT vg.id, vg.nombre AS videojuego, vg.imagen, c.nombre AS categoria_nombre,
                       SUM(dv.cantidad) AS cantidadVendida,
                       COALESCE(SUM(dv.cantidad * COALESCE(dv.precio_unitario, vg.precio)), 0) AS totalRecaudado
                FROM detalle_venta dv
                INNER JOIN ventas v ON v.id = dv.venta_id
                INNER JOIN videojuegos vg ON vg.id = dv.videojuego_id
                LEFT JOIN categorias c ON c.id = vg.categoria_id
                GROUP BY vg.id, vg.nombre, vg.imagen, c.nombre
                ORDER BY cantidadVendida DESC, totalRecaudado DESC
                LIMIT 8
                """);

        List<Map<String, Object>> ventasPorCategoria = db.query("""
                SELECT COALESCE(c.nombre, 'Sin categoría') AS categoria,
                       SUM(dv.cantidad) AS cantidadVendida,
                       COALESCE(SUM(dv.cantidad * COALESCE(dv.precio_unitario, vg.precio)), 0) AS totalRecaudado
                FROM detalle_venta dv
                INNER JOIN ventas v ON v.id = dv.venta_id
                INNER JOIN videojuegos vg ON vg.id = dv.videojuego_id
                LEFT JOIN categorias c ON c.id = vg.categoria_id
                GROUP BY c.nombre
                ORDER BY totalRecaudado DESC
                LIMIT 8
                """);

        List<Map<String, Object>> ventasPorDia = db.query("""
                SELECT DATE(v.fecha) AS fecha, COUNT(*) AS cantidadVentas, COALESCE(SUM(v.total), 0) AS totalVendido
                FROM ventas v
                GROUP BY DATE(v.fecha)
                ORDER BY fecha DESC
                LIMIT 7
                """);

        Map<String, Object> promedioVenta = db.one("SELECT COALESCE(AVG(total), 0) AS ticketPromedio FROM ventas");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalVideojuegos", val(videojuegos, "totalVideojuegos"));
        res.put("videojuegosVisibles", val(videojuegos, "videojuegosVisibles"));
        res.put("videojuegosOcultos", val(videojuegos, "videojuegosOcultos"));
        res.put("totalStockBajo", val(videojuegos, "totalStockBajo"));
        res.put("totalVentas", val(ventas, "totalVentas"));
        res.put("totalVendido", val(ventas, "totalVendido"));
        res.put("ticketPromedio", val(promedioVenta, "ticketPromedio"));
        res.put("ultimasVentas", ultimasVentas);
        res.put("juegosStockBajo", juegosStockBajo);
        res.put("videojuegosMasVendidos", videojuegosMasVendidos);
        res.put("ventasPorCategoria", ventasPorCategoria);
        res.put("ventasPorDia", ventasPorDia);
        return res;
    }

    private Object val(Map<String, Object> map, String key) {
        return map == null || map.get(key) == null ? 0 : map.get(key);
    }
}
