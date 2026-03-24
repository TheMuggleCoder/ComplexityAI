package com.muggle.analyzer.model;

import java.util.List;

public class AnalysisResult {

    private String timeComplexity;
    private String timeLabel;
    private String spaceComplexity;
    private String spaceLabel;
    private List<String> steps;
    private String verdict;
    private String notes;
    private double confidence;
    private String source;     // "static_analysis" or "error"
    private boolean hasRecursion;
    private String error;

    // ── Constructor (full) ──
    public AnalysisResult(
            String timeComplexity, String timeLabel,
            String spaceComplexity, String spaceLabel,
            List<String> steps, String verdict,
            String notes, double confidence,
            String source, boolean hasRecursion) {
        this.timeComplexity  = timeComplexity;
        this.timeLabel       = timeLabel;
        this.spaceComplexity = spaceComplexity;
        this.spaceLabel      = spaceLabel;
        this.steps           = steps;
        this.verdict         = verdict;
        this.notes           = notes;
        this.confidence      = confidence;
        this.source          = source;
        this.hasRecursion    = hasRecursion;
    }

    // ── Error constructor ──
    public static AnalysisResult error(String message) {
        AnalysisResult r = new AnalysisResult(
            null, null, null, null, null, null, null, 0, "error", false
        );
        r.error = message;
        return r;
    }

    // ── Getters & Setters ──
    public String getTimeComplexity()  { return timeComplexity; }
    public String getTimeLabel()       { return timeLabel; }
    public String getSpaceComplexity() { return spaceComplexity; }
    public String getSpaceLabel()      { return spaceLabel; }
    public List<String> getSteps()     { return steps; }
    public String getVerdict()         { return verdict; }
    public String getNotes()           { return notes; }
    public double getConfidence()      { return confidence; }
    public String getSource()          { return source; }
    public boolean isHasRecursion()    { return hasRecursion; }
    public String getError()           { return error; }

    public void setTimeComplexity(String v)  { this.timeComplexity = v; }
    public void setTimeLabel(String v)       { this.timeLabel = v; }
    public void setSpaceComplexity(String v) { this.spaceComplexity = v; }
    public void setSpaceLabel(String v)      { this.spaceLabel = v; }
    public void setSteps(List<String> v)     { this.steps = v; }
    public void setVerdict(String v)         { this.verdict = v; }
    public void setNotes(String v)           { this.notes = v; }
    public void setConfidence(double v)      { this.confidence = v; }
    public void setSource(String v)          { this.source = v; }
    public void setHasRecursion(boolean v)   { this.hasRecursion = v; }
    public void setError(String v)           { this.error = v; }
}
