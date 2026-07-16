package com.verirad.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * VeriRad - Multi-agent CT scan report generation system.
 *
 * Pipeline: Vision (dual, independent) -> Arbiter (consensus/disagreement)
 *           -> Explainability (saliency heatmap) -> Writing -> Compliance/Format
 *
 * See /docs/system_design.md for the full architecture.
 */
@SpringBootApplication
public class VeriRadApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeriRadApplication.class, args);
    }
}
