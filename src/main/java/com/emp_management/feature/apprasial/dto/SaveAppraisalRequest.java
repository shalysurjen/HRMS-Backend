package com.emp_management.feature.apprasial.dto;
import java.util.List;

public class SaveAppraisalRequest {
    private String employeeId;
    private Long cycleId;
    private List<AnswerDTO> answers;
    private boolean submit;

    public SaveAppraisalRequest() {}

    public String getEmployeeId() { return employeeId; } public void setEmployeeId(String v) { employeeId = v; }
    public Long getCycleId() { return cycleId; } public void setCycleId(Long v) { cycleId = v; }
    public List<AnswerDTO> getAnswers() { return answers; } public void setAnswers(List<AnswerDTO> v) { answers = v; }
    public boolean isSubmit() { return submit; } public void setSubmit(boolean v) { submit = v; }
}
