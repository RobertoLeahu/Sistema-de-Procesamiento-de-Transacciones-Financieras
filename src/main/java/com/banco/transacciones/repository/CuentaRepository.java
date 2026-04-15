package com.banco.transacciones.repository;

import java.util.Optional;

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
	 * finalice, evitando condiciones de carrera.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Cuenta c WHERE c.numeroCuenta = :numeroCuenta")
	Optional<Cuenta> findByNumeroCuentaWithLock(@Param("numeroCuenta") String numeroCuenta);

	Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);
}
