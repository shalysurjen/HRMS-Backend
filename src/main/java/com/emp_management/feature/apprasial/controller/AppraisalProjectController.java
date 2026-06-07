package com.emp_management.feature.apprasial.controller;

import com.emp_management.feature.apprasial.dto.AppraisalProjectDTO;
import com.emp_management.feature.apprasial.service.AppraisalProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * URL pattern:  /v1/appraisals/{appraisalId}/projects
 *
 * NOTE: Backend context-path = /api  (set in application.properties)
 *       So full URL from browser = /api/v1/appraisals/{appraisalId}/projects
 *       Frontend axios baseURL   = /api
 *       Therefore axios calls    = /v1/appraisals/{appraisalId}/projects  ✅
 *
 * DO NOT prefix /api here — context-path handles that automatically.
 */
@RestController
@RequestMapping("/v1/appraisals/{appraisalId}/projects")
public class AppraisalProjectController {

    @Autowired
    private AppraisalProjectService projectService;

    // GET /v1/appraisals/{appraisalId}/projects?questionId=6
    @GetMapping
    public ResponseEntity<List<AppraisalProjectDTO.Response>> getProjects(
            @PathVariable Long appraisalId,
            @RequestParam(required = false) Long questionId) {
        if (questionId != null) {
            return ResponseEntity.ok(projectService.getProjects(appraisalId, questionId));
        }
        return ResponseEntity.ok(projectService.getAllProjects(appraisalId));
    }

    // POST /v1/appraisals/{appraisalId}/projects
    @PostMapping
    public ResponseEntity<AppraisalProjectDTO.Response> addProject(
            @PathVariable Long appraisalId,
            @RequestBody AppraisalProjectDTO.Request req) {
        return ResponseEntity.ok(projectService.addProject(appraisalId, req));
    }

    // PUT /v1/appraisals/{appraisalId}/projects/{projectId}
    @PutMapping("/{projectId}")
    public ResponseEntity<AppraisalProjectDTO.Response> updateProject(
            @PathVariable Long appraisalId,
            @PathVariable Long projectId,
            @RequestBody AppraisalProjectDTO.Request req) {
        return ResponseEntity.ok(projectService.updateProject(projectId, req));
    }

    // DELETE /v1/appraisals/{appraisalId}/projects/{projectId}
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long appraisalId,
            @PathVariable Long projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }
}