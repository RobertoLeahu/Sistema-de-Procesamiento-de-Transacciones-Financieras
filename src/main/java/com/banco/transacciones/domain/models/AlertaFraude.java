package com.banco.transacciones.domain.models;

import com.banco.transacciones.domain.enums.NivelRiesgo;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad que registra las alertas de posibles fraudes detectadas por el sistema.
 */

@Entity
@Table(name = "alertas_fraude")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaFraude {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaccion_id", nullable = false)
    private Transaccion transaccion;

    @Enumerated(EnumType.STRING)
    private NivelRiesgo nivel;

    private String motivo;

    private Boolean revisada;
}
