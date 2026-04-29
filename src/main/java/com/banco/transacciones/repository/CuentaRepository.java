package com.banco.transacciones.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.banco.transacciones.domain.models.Cuenta;

import jakarta.persistence.LockModeType;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

	/**
	 * Busca una cuenta por su número aplicando un BLOQUEO PESIMISTA de escritura.
	 * Esto bloquea la fila en la base de datos hasta que la transacción actual
	 * finalice, evitando condiciones de carrera. * @EntityGraph se utiliza para
	 * hacer un JOIN FETCH del cliente y evitar el error LazyInitializationException
	 * en procesos asíncronos.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = { "cliente" })
	@Query("SELECT c FROM Cuenta c WHERE c.numeroCuenta = :numeroCuenta")
	Optional<Cuenta> findByNumeroCuentaWithLock(@Param("numeroCuenta") String numeroCuenta);

	/**
	 * Búsqueda estándar sin bloqueo de base de datos. También recupera el cliente
	 * asociado para validaciones inmediatas.
	 */
	@EntityGraph(attributePaths = { "cliente" })
	Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);
}