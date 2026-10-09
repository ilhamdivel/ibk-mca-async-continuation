package com.ibkglobal.integrator.engine.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HealthCheckResult extends HealthCheckDefault {
	private Integer result; // ack : 0, nak : 1
}
