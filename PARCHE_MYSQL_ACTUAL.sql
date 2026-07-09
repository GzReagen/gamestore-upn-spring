-- =====================================================
-- PARCHE PARA TU MYSQL ACTUAL: tienda_videojuegos
-- No crea otra base de datos.
-- Solo asegura columnas/tablas necesarias para que Spring Boot no falle.
-- =====================================================

USE tienda_videojuegos;

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_missing $$
CREATE PROCEDURE add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

-- VIDEOJUEGOS
CALL add_column_if_missing('videojuegos', 'activo', 'TINYINT(1) NOT NULL DEFAULT 1');
CALL add_column_if_missing('videojuegos', 'fecha_creacion', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP');

UPDATE videojuegos
SET activo = 1
WHERE activo IS NULL;

-- DETALLE DE VENTA
CALL add_column_if_missing('detalle_venta', 'precio_unitario', 'DECIMAL(10,2) NOT NULL DEFAULT 0');
CALL add_column_if_missing('detalle_venta', 'subtotal', 'DECIMAL(10,2) NULL');

UPDATE detalle_venta dv
INNER JOIN videojuegos v ON v.id = dv.videojuego_id
SET dv.precio_unitario = v.precio
WHERE dv.precio_unitario IS NULL OR dv.precio_unitario = 0;

UPDATE detalle_venta dv
INNER JOIN videojuegos v ON v.id = dv.videojuego_id
SET dv.subtotal = COALESCE(dv.precio_unitario, v.precio) * dv.cantidad
WHERE dv.subtotal IS NULL;

-- CARRITO
CREATE TABLE IF NOT EXISTS carrito (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL
);

CREATE TABLE IF NOT EXISTS detalle_carrito (
    id INT AUTO_INCREMENT PRIMARY KEY,
    carrito_id INT NOT NULL,
    videojuego_id INT NOT NULL,
    cantidad INT NOT NULL DEFAULT 1
);

-- VARIAS CATEGORÍAS POR VIDEOJUEGO
CREATE TABLE IF NOT EXISTS videojuego_categorias (
    videojuego_id INT NOT NULL,
    categoria_id INT NOT NULL,
    PRIMARY KEY (videojuego_id, categoria_id)
);

INSERT IGNORE INTO videojuego_categorias (videojuego_id, categoria_id)
SELECT id, categoria_id
FROM videojuegos
WHERE categoria_id IS NOT NULL;

DROP PROCEDURE IF EXISTS add_column_if_missing;

-- VERIFICACIÓN RÁPIDA
SELECT 'Parche aplicado correctamente' AS mensaje;
SHOW TABLES;
