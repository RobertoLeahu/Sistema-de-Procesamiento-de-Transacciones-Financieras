-- ===================================================================
-- SCRIPT DE POBLADO DE DATOS (Sincronizado con Modelos Java)
-- ===================================================================

-- 1. CLIENTES (Tabla: clientes)
INSERT INTO clientes (id, nombre, dni, email, fecha_alta, pais_residencia) 
VALUES (1, 'Juan Perez', '12345678A', 'juan.perez@banco.com', CURRENT_DATE, 'ES');

INSERT INTO clientes (id, nombre, dni, email, fecha_alta, pais_residencia) 
VALUES (2, 'Maria Gomez', '87654321B', 'maria.gomez@banco.com', CURRENT_DATE, 'ES');

INSERT INTO clientes (id, nombre, dni, email, fecha_alta, pais_residencia) 
VALUES (3, 'Empresa Logística S.L.', 'B12345678', 'admin@logistica.com', CURRENT_DATE, 'ES');


-- 2. CUENTAS (Tabla: cuentas)
-- Corrección vital: 'ACTIVA' -> 'ACTIVADA' y 'AHORRO' -> 'ACTIVO' según los Enums de Java.
INSERT INTO cuentas (id, numero_cuenta, saldo, tipo, estado, cliente_id) 
VALUES (1, 'ES9121000418401234567890', 15000.0, 'CORRIENTE', 'ACTIVADA', 1);

INSERT INTO cuentas (id, numero_cuenta, saldo, tipo, estado, cliente_id) 
VALUES (2, 'ES9121000418401111111111', 10000.0, 'ACTIVO', 'BLOQUEADA', 2);

INSERT INTO cuentas (id, numero_cuenta, saldo, tipo, estado, cliente_id) 
VALUES (3, 'ES9121000418400987654321', 1000.0, 'CORRIENTE', 'ACTIVADA', 2);

INSERT INTO cuentas (id, numero_cuenta, saldo, tipo, estado, cliente_id) 
VALUES (4, 'ES9121000418402222222222', 2000.0, 'CORRIENTE', 'ACTIVADA', 3);

INSERT INTO cuentas (id, numero_cuenta, saldo, tipo, estado, cliente_id) 
VALUES (5, 'ES9121000418403333333333', 50000.0, 'EMPRESARIAL', 'ACTIVADA', 3);

INSERT INTO cuentas (id, numero_cuenta, saldo, tipo, estado, cliente_id) 
VALUES (6, 'ES9121000418404444444444', 5000.00, 'EMPRESARIAL', 'ACTIVADA', 3);


-- 3. TRANSACCIONES (Tabla: transacciones)
INSERT INTO transacciones (id, cuenta_origen, cuenta_destino, monto, tipo, estado, fecha_hora, codigo_pais, descripcion, riesgo_fraude)
VALUES (1, 'ES9121000418401234567890', 'ES9121000418400987654321', 100.00, 'TRANSFERENCIA', 'COMPLETADA', CURRENT_TIMESTAMP, 'ES', 'Pago de servicios', 0.10);

INSERT INTO transacciones (id, cuenta_origen, cuenta_destino, monto, tipo, estado, fecha_hora, codigo_pais, descripcion, riesgo_fraude)
VALUES (312, 'ES9121000418401234567890', 'ES9121000418404444444444', 15000.00, 'TRANSFERENCIA', 'COMPLETADA', CURRENT_TIMESTAMP, 'ES', 'Inversión inicial', 0.65);

INSERT INTO transacciones (id, cuenta_origen, cuenta_destino, monto, tipo, estado, fecha_hora, codigo_pais, descripcion, riesgo_fraude)
VALUES (408, 'ES9121000418403333333333', 'ES9121000418400987654321', 45000.00, 'TRANSFERENCIA', 'RECHAZADA', CURRENT_TIMESTAMP, 'KY', 'Transferencia de alto valor', 0.85);


-- 4. ALERTAS DE FRAUDE (Tabla: alertas_fraude)
INSERT INTO alertas_fraude (id, transaccion_id, nivel, motivo, revisada)
VALUES (102, 312, 'ALTO', 'Múltiples transferencias en 5 min (>3)', false);

INSERT INTO alertas_fraude (id, transaccion_id, nivel, motivo, revisada)
VALUES (105, 408, 'CRITICO', 'Score > 0.75 y país destino inusual', false);


-- ===================================================================
-- REINICIO DE SECUENCIAS (Sincronización para H2 con IDENTITY)
-- ===================================================================
ALTER TABLE clientes ALTER COLUMN id RESTART WITH 100;
ALTER TABLE cuentas ALTER COLUMN id RESTART WITH 100;
ALTER TABLE transacciones ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE alertas_fraude ALTER COLUMN id RESTART WITH 1000;