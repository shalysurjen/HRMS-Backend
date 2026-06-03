package com.emp_management.feature.apprasial.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AppraisalCycleDTO {
    private Long id;
    private String cycleLabel;
    private int startYear;
    private int endYear;
    private boolean isActive;
    private boolean isOpen;

    public AppraisalCycleDTO() {}
    public AppraisalCycleDTO(Long id, String cycleLabel, int startYear, int endYear, boolean isActive, boolean isOpen) {
        this.id = id; this.cycleLabel = cycleLabel; this.startYear = startYear;
        this.endYear = endYear; this.isActive = isActive; this.isOpen = isOpen;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCycleLabel() { return cycleLabel; }
    public void setCycleLabel(String v) { cycleLabel = v; }
    public int getStartYear() { return startYear; }
    public void setStartYear(int v) { startYear = v; }
    public int getEndYear() { return endYear; }
    public void setEndYear(int v) { endYear = v; }

    // Force JSON key "isActive" instead of "active"
    @JsonProperty("isActive")
    public boolean isActive() { return isActive; }
    public void setActive(boolean v) { isActive = v; }

    // Force JSON key "isOpen" instead of "open"
    @JsonProperty("isOpen")
    public boolean isOpen() { return isOpen; }
    public void setOpen(boolean v) { isOpen = v; }
}