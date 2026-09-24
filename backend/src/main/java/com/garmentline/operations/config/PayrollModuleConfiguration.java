package com.garmentline.operations.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Loads the payroll business module only in the shared cloud backend profile. */
@Configuration
@Profile("payroll")
@ComponentScan("com.tradex.unionnorth")
@EntityScan("com.tradex.unionnorth")
@EnableJpaRepositories("com.tradex.unionnorth")
public class PayrollModuleConfiguration {}
