package com.muggle.analyzer.model;

public class AnalysisRequest {
    private String code;

    public AnalysisRequest() {}
    public AnalysisRequest(String code) { this.code = code; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
