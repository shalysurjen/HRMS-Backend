package com.emp_management.feature.apprasial.service;

import com.emp_management.feature.apprasial.dto.AppraisalProjectDTO;
import com.emp_management.feature.apprasial.entity.AppraisalProject;
import com.emp_management.feature.apprasial.entity.AppraisalQuestion;
import com.emp_management.feature.apprasial.entity.SelfAppraisal;
import com.emp_management.feature.apprasial.repository.AppraisalProjectRepository;
import com.emp_management.feature.apprasial.repository.AppraisalQuestionRepository;
import com.emp_management.feature.apprasial.repository.SelfAppraisalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppraisalProjectService {

    @Autowired private AppraisalProjectRepository projectRepository;
    @Autowired private SelfAppraisalRepository appraisalRepository;
    @Autowired private AppraisalQuestionRepository questionRepository;

    // ── Add project ──────────────────────────────────────────────────────────
    public AppraisalProjectDTO.Response addProject(Long appraisalId, AppraisalProjectDTO.Request req) {
        SelfAppraisal appraisal = appraisalRepository.findById(appraisalId)
                .orElseThrow(() -> new RuntimeException("Appraisal not found: " + appraisalId));
        AppraisalQuestion question = questionRepository.findById(req.getQuestionId())
                .orElseThrow(() -> new RuntimeException("Question not found: " + req.getQuestionId()));

        AppraisalProject project = new AppraisalProject();
        project.setAppraisal(appraisal);
        project.setQuestion(question);
        project.setProjectName(req.getProjectName());
        project.setDescription(req.getDescription());

        return toResponse(projectRepository.save(project));
    }

    // ── Update project ───────────────────────────────────────────────────────
    public AppraisalProjectDTO.Response updateProject(Long projectId, AppraisalProjectDTO.Request req) {
        AppraisalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        project.setProjectName(req.getProjectName());
        project.setDescription(req.getDescription());

        return toResponse(projectRepository.save(project));
    }

    // ── Delete project ───────────────────────────────────────────────────────
    @Transactional
    public void deleteProject(Long projectId) {
        projectRepository.deleteById(projectId);
    }

    // ── Get projects by appraisal + question ─────────────────────────────────
    public List<AppraisalProjectDTO.Response> getProjects(Long appraisalId, Long questionId) {
        return projectRepository
                .findByAppraisal_IdAndQuestion_Id(appraisalId, questionId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get all projects for appraisal ───────────────────────────────────────
    public List<AppraisalProjectDTO.Response> getAllProjects(Long appraisalId) {
        return projectRepository
                .findByAppraisal_Id(appraisalId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private AppraisalProjectDTO.Response toResponse(AppraisalProject p) {
        AppraisalProjectDTO.Response r = new AppraisalProjectDTO.Response();
        r.setId(p.getId());
        r.setAppraisalId(p.getAppraisal().getId());
        r.setQuestionId(p.getQuestion().getId());
        r.setProjectName(p.getProjectName());
        r.setDescription(p.getDescription());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}