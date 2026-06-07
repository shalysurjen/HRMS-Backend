package com.emp_management.feature.apprasial;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit component-scan config for the appraisal module.
 *
 * WHY THIS EXISTS:
 *   The module package is spelled "apprasial" (typo) while the rest of the
 *   project uses "appraisal".  If your @SpringBootApplication class lives in
 *   com.emp_management and does NOT do an explicit @ComponentScan, Spring's
 *   default scan covers all sub-packages — including this one — and this file
 *   is not needed.
 *
 *   BUT if any @Configuration / @SpringBootApplication elsewhere has an
 *   explicit basePackages list that omits "apprasial", the controller is
 *   silently skipped and every endpoint returns 404.  This class guarantees
 *   the package is always registered regardless of what the root scan says.
 */
@Configuration
@ComponentScan(basePackages = "com.emp_management.feature.apprasial")
public class AppraisalModuleConfig {
    // intentionally empty — annotation does the work
}
