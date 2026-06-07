package com.emp_management.feature.apprasial.dto;

public class AnswerDTO {
    private Long questionId;
    private String answerText;
    private Integer selfRating;

    public AnswerDTO() {}

    public Long getQuestionId() { return questionId; } public void setQuestionId(Long v) { questionId = v; }
    public String getAnswerText() { return answerText; } public void setAnswerText(String v) { answerText = v; }
    public Integer getSelfRating() { return selfRating; } public void setSelfRating(Integer v) { selfRating = v; }
}
