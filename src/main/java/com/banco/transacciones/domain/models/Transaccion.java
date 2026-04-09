package com.banco.transacciones.domain.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.banco.transacciones.domain.enums.EstadoTransaccion;
import com.banco.transacciones.domain.enums.TipoTransaccion;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * Registro histórico e inmutable de un movimiento financiero entre cuentas.
 */

@Entity
@Table(name = "transacciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaccion {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String cuentaOrigen;
	private String cuentaDestino;
	private BigDecimal monto;

	@Enumerated(EnumType.STRING)
	private TipoTransaccion tipo;

	@Enumerated(EnumType.STRING)
	private EstadoTransaccion estado;

	private Instant fechaHora;
	private String descripcion;
	private Double riesgoFraude;

	@OneToMany(mappedBy = "transaccion", cascade = CascadeType.ALL)
	private List<AlertaFraude> alertas;
}
