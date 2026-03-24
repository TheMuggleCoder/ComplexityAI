package com.muggle.analyzer.controller;

import com.muggle.analyzer.engine.StaticAnalysisEngine;
import com.muggle.analyzer.model.AnalysisRequest;
import com.muggle.analyzer.model.AnalysisResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for the Java complexity analyzer.
 *
 * POST /analyze
 *   Body: { "code": "<java source>" }
 *   Returns: AnalysisResult JSON
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")   // Allow frontend (file:// or any port) to call this
public class AnalyzerController {

    private final StaticAnalysisEngine engine;

    public AnalyzerController(StaticAnalysisEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyze(@RequestBody AnalysisRequest request) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            return ResponseEntity.badRequest().body(AnalysisResult.error("Request body must contain 'code'."));
        }

        try {
            AnalysisResult result = engine.analyze(request.getCode());
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                .body(AnalysisResult.error("Internal analysis error: " + ex.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\",\"engine\":\"ComplexityAI Static Analyzer\"}");
    }
}
