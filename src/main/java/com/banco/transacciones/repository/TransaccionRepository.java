package com.banco.transacciones.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.banco.transacciones.domain.models.Transaccion;

@Repository
public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

	/**
	 * Conteo de transacciones de una cuenta en un periodo de tiempo. Requerido para
	 * el indicador de fraude: > 3 transacciones en 5 min.
	 */
	long countByCuentaOrigenAndFechaHoraAfter(String cuentaOrigen, Instant fechaHora);

	/**
	 * Recupera transacciones del último día para la detección de ciclos.
	 */
	List<Transaccion> findByFechaHoraAfter(Instant fechaHora);

	/**
	 * Generar el resumen de movimientos de la cuenta origen
	 */
	List<Transaccion> findByCuentaOrigenOrCuentaDestino(String cuentaOrigen, String cuentaDestino);

	
	/**
	 * Consulta que determine cuál es el país habitual.
	 */
	@Query(value = "SELECT t.codigo_pais FROM transacciones t " + "WHERE t.cuenta_origen = :cuenta "
			+ "GROUP BY t.codigo_pais " + "ORDER BY COUNT(*) DESC LIMIT 1", nativeQuery = true)
	Optional<String> findPaisHabitual(@Param("cuenta") String cuenta);
}
