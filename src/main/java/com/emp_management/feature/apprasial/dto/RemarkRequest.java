package com.emp_management.feature.apprasial.dto;
import com.emp_management.feature.apprasial.entity.AppraisalRemark;
import java.util.List;

public class RemarkRequest {
    private String approverId;
    private AppraisalRemark.ApproverLevel approverLevel;
    private String overallRemark;
    private List<QuestionRemarkDTO> questionRemarks;
    private boolean approve;
    private boolean publish;
    /** When true: persist remarks only, do NOT change appraisal status or send notifications. */
    private boolean draftOnly;

    public RemarkRequest() {}

    public String getApproverId() { return approverId; } public void setApproverId(String v) { approverId = v; }
    public AppraisalRemark.ApproverLevel getApproverLevel() { return approverLevel; } public void setApproverLevel(AppraisalRemark.ApproverLevel v) { approverLevel = v; }
    public String getOverallRemark() { return overallRemark; } public void setOverallRemark(String v) { overallRemark = v; }
    public List<QuestionRemarkDTO> getQuestionRemarks() { return questionRemarks; } public void setQuestionRemarks(List<QuestionRemarkDTO> v) { questionRemarks = v; }
    public boolean isApprove() { return approve; } public void setApprove(boolean v) { approve = v; }
    public boolean isPublish() { return publish; } public void setPublish(boolean v) { publish = v; }
    public boolean isDraftOnly() { return draftOnly; } public void setDraftOnly(boolean v) { draftOnly = v; }

    public static class QuestionRemarkDTO {
        private Long questionId;
        private String remarkText;
        private Integer revisedRating;

        public QuestionRemarkDTO() {}

        public Long getQuestionId() { return questionId; } public void setQuestionId(Long v) { questionId = v; }
        public String getRemarkText() { return remarkText; } public void setRemarkText(String v) { remarkText = v; }
        public Integer getRevisedRating() { return revisedRating; } public void setRevisedRating(Integer v) { revisedRating = v; }
    }
}
