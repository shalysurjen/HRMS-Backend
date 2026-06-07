package com.emp_management.feature.apprasial.dto;
import com.emp_management.feature.apprasial.entity.AppraisalQuestion;

public class AppraisalQuestionDTO {
    private Long id;
    private String section;
    private int sortOrder;
    private String questionText;
    private AppraisalQuestion.InputType inputType;
    private boolean isRequired;

    public AppraisalQuestionDTO() {}

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getSection() { return section; } public void setSection(String v) { section = v; }
    public int getSortOrder() { return sortOrder; } public void setSortOrder(int v) { sortOrder = v; }
    public String getQuestionText() { return questionText; } public void setQuestionText(String v) { questionText = v; }
    public AppraisalQuestion.InputType getInputType() { return inputType; } public void setInputType(AppraisalQuestion.InputType v) { inputType = v; }
    public boolean isRequired() { return isRequired; } public void setRequired(boolean v) { isRequired = v; }
}
