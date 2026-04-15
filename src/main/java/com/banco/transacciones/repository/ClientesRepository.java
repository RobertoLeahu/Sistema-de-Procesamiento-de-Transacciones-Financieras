package com.banco.transacciones.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.banco.transacciones.domain.models.Cliente;

@Repository
public interface ClientesRepository extends JpaRepository<Cliente, Long> {
	Optional<Cliente> findByDni(String dni);
}
