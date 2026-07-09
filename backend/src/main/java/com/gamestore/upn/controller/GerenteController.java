package com.gamestore.upn.controller;

import com.gamestore.upn.service.DbService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gerente")
public class GerenteController {
    private final DbService db;

    public GerenteController(DbService db) {
        this.db = db;
    }

    @GetMapping("/resumen")
    public Map<String, Object> resumen(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestParam(required = false) String desde,
                                       @RequestParam(required = false) String hasta) {
        db.requireGerente(authorization);

        StringBuilder filtro = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (desde != null && !desde.isBlank()) {
            filtro.append(" AND DATE(v.fecha) >= ? ");
            params.add(desde);
        }
        if (hasta != null && !hasta.isBlank()) {
            filtro.append(" AND DATE(v.fecha) <= ? ");
            params.add(hasta);
        }

        Map<String, Object> usuarios = db.one("""
                SELECT
                  COUNT(*) AS totalUsuarios,
                  SUM(CASE WHEN rol = 'CLIENTE' THEN 1 ELSE 0 END) AS totalClientes,
                  SUM(CASE WHEN rol = 'ADMIN' THEN 1 ELSE 0 END) AS totalAdministradores,
                  SUM(CASE WHEN rol = 'GERENTE' THEN 1 ELSE 0 END) AS totalGerentes
                FROM usuarios
                """);

        Map<String, Object> videojuegos = db.one("""
                SELECT
                  COUNT(*) AS totalVideojuegos,
                  SUM(CASE WHEN COALESCE(activo, 1) = 1 THEN 1 ELSE 0 END) AS videojuegosVisibles,
                  SUM(CASE WHEN COALESCE(activo, 1) = 0 THEN 1 ELSE 0 END) AS videojuegosOcultos,
                  SUM(CASE WHEN stock <= 5 AND COALESCE(activo, 1) = 1 THEN 1 ELSE 0 END) AS stockBajo
                FROM videojuegos
                """);

        Map<String, Object> ventas = db.one("SELECT COUNT(*) AS totalVentas, COALESCE(SUM(v.total), 0) AS totalVendido FROM ventas v WHERE 1 = 1 " + filtro, params.toArray());

        List<Map<String, Object>> ultimasVentas = db.query("""
                SELECT v.id, u.nombre AS cliente, v.fecha, v.total
                FROM ventas v
                INNER JOIN usuarios u ON u.id = v.usuario_id
                WHERE 1 = 1
                """ + filtro + " ORDER BY v.fecha DESC, v.id DESC LIMIT 10", params.toArray());

        List<Map<String, Object>> videojuegosMasVendidos = db.query("""
                SELECT vg.id, vg.nombre AS videojuego, vg.imagen,
                       SUM(dv.cantidad) AS cantidadVendida,
                       COALESCE(SUM(dv.cantidad * COALESCE(dv.precio_unitario, vg.precio)), 0) AS totalRecaudado
                FROM detalle_venta dv
                INNER JOIN ventas v ON v.id = dv.venta_id
                INNER JOIN videojuegos vg ON vg.id = dv.videojuego_id
                WHERE 1 = 1
                """ + filtro + " GROUP BY vg.id, vg.nombre, vg.imagen ORDER BY cantidadVendida DESC LIMIT 10", params.toArray());

        List<Map<String, Object>> ventasPorCliente = db.query("""
                SELECT u.id, u.nombre AS cliente, u.correo,
                       COUNT(v.id) AS cantidadVentas,
                       COALESCE(SUM(v.total), 0) AS totalComprado
                FROM usuarios u
                LEFT JOIN ventas v ON v.usuario_id = u.id
                """ + joinFechas(desde, hasta) + "\n" +
                "WHERE u.rol = 'CLIENTE'\n" +
                "GROUP BY u.id, u.nombre, u.correo\n" +
                "ORDER BY totalComprado DESC\n" +
                "LIMIT 10", params.toArray());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalUsuarios", val(usuarios, "totalUsuarios"));
        res.put("totalClientes", val(usuarios, "totalClientes"));
        res.put("totalAdministradores", val(usuarios, "totalAdministradores"));
        res.put("totalGerentes", val(usuarios, "totalGerentes"));
        res.put("totalVideojuegos", val(videojuegos, "totalVideojuegos"));
        res.put("videojuegosVisibles", val(videojuegos, "videojuegosVisibles"));
        res.put("videojuegosOcultos", val(videojuegos, "videojuegosOcultos"));
        res.put("stockBajo", val(videojuegos, "stockBajo"));
        res.put("totalVentas", val(ventas, "totalVentas"));
        res.put("totalVendido", val(ventas, "totalVendido"));
        res.put("ultimasVentas", ultimasVentas);
        res.put("videojuegosMasVendidos", videojuegosMasVendidos);
        res.put("ventasPorCliente", ventasPorCliente);
        return res;
    }

    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuarios(@RequestHeader(value = "Authorization", required = false) String authorization) {
        db.requireGerente(authorization);
        return db.query("""
                SELECT id, nombre, correo, usuario, rol
                FROM usuarios
                ORDER BY FIELD(rol, 'GERENTE', 'ADMIN', 'CLIENTE'), id DESC
                """);
    }

    private String joinFechas(String desde, String hasta) {
        StringBuilder sb = new StringBuilder();
        if (desde != null && !desde.isBlank()) sb.append(" AND DATE(v.fecha) >= ? ");
        if (hasta != null && !hasta.isBlank()) sb.append(" AND DATE(v.fecha) <= ? ");
        return sb.toString();
    }

    private Object val(Map<String, Object> map, String key) {
        return map == null || map.get(key) == null ? 0 : map.get(key);
    }
}
