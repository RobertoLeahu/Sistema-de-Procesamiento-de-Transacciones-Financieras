package com.banco.transacciones.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.banco.transacciones.domain.models.Cliente;

public interface ClientesRepository extends JpaRepository<Cliente, Long> {
	Optional<Cliente> findByDni(String dni);
}
