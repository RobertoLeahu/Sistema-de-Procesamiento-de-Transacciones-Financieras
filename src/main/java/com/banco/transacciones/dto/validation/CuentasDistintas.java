package com.banco.transacciones.dto.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Anotación personalizada @CuentasDistintas que valide que origen ≠ destino a nivel de clase del DTO.
 */
@Documented
@Constraint(validatedBy = CuentasDistintasValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface CuentasDistintas {
	String message() default "La cuenta de origen y destino no pueden ser la misma";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
