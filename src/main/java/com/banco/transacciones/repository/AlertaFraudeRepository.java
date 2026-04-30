package com.banco.transacciones.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.banco.transacciones.domain.models.AlertaFraude;

@Repository
public interface AlertaFraudeRepository extends JpaRepository<AlertaFraude, Long> {

	/**
	 * Retorna alertas no revisadas ordenadas por riesgo y fecha.
	 */
	@Query("SELECT a FROM AlertaFraude a WHERE a.revisada = false OR a.revisada IS NULL")
	Page<AlertaFraude> buscarPendientes(Pageable pageable);
}
