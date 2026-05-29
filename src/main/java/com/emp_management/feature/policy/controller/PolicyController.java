package com.emp_management.feature.policy.controller;

import com.emp_management.feature.policy.dto.PolicyResponse;
import com.emp_management.feature.policy.entity.Policy;
import com.emp_management.feature.policy.service.PolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/policies")
@CrossOrigin(
        origins = "http://localhost:5173",
        allowCredentials = "true"
)
public class PolicyController {

    @Autowired
    private PolicyService service;


    // ✅ Upload
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("name") String name, // Added explicit "name"
            @RequestParam("file") MultipartFile file) { // Added explicit "file"
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }
            service.upload(name, file);
            return ResponseEntity.ok("Uploaded successfully");
        } catch (Exception e) {
            e.printStackTrace(); // Log the actual crash in the IntelliJ console
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }


    // ✅ Get All
    @GetMapping
    public ResponseEntity<List<PolicyResponse>> getAll() {
        try {
            // Change 'service.getAll()' to 'service.findAll()' or whatever
            // matches your Repository/Service method name
            List<Policy> policies = service.getAll();

            if (policies == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<PolicyResponse> response = policies.stream()
                    .map(p -> new PolicyResponse(
                            p.getId(),
                            p.getName(),
                            p.getFileName() // This is where getFileName() IS used!
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Check your IntelliJ console for this printout to see the REAL error
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }



    // ✅ Download
    @GetMapping("/download/{id}")
    public ResponseEntity<?> download(@PathVariable Long id) {
        try {
            Policy policy = service.getById(id).orElseThrow();

            FileInputStream file = new FileInputStream(policy.getFilePath());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=" + policy.getFileName())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(file));

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    @GetMapping("/preview/{id}")
    public ResponseEntity<Resource> previewPolicy(@PathVariable Long id) {
        try {
            Policy policy = service.getById(id)
                    .orElseThrow(() -> new RuntimeException("File not found in DB"));

            File file = new File(policy.getFilePath());

            if (!file.exists()) {
                System.out.println("❌ FILE NOT FOUND: " + policy.getFilePath());
                return ResponseEntity.notFound().build();
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + policy.getFileName() + "\"")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    // ✅ View (open in browser)
    @GetMapping("/view/{id}")
    public ResponseEntity<?> view(
            @PathVariable Long id,
            @SuppressWarnings("unused")
            @RequestParam(required = false) String token
    ) {
        try {
            Policy policy = service.getById(id).orElseThrow();

            System.out.println("FILE PATH: " + policy.getFilePath()); // ✅ DEBUG

            File file = new File(policy.getFilePath());

            if (!file.exists()) {
                return ResponseEntity.status(404).body("File not found in server");
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace(); // (you can replace later)
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // ✅ Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok("Deleted");
    }
}