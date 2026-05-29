package com.emp_management.feature.policy.service;

import com.emp_management.feature.policy.dto.PolicyResponse;
import com.emp_management.feature.policy.entity.Policy;
import com.emp_management.feature.policy.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PolicyService {

    @Autowired
    private PolicyRepository repository;

    private final String uploadDir = System.getProperty("user.home") + "/emp-uploads/";

    public Policy upload(String name, MultipartFile file) throws Exception {

        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        String filePath = uploadDir + file.getOriginalFilename();

        File dest = new File(filePath);
        file.transferTo(dest);

        Policy policy = new Policy();
        policy.setName(name);
        policy.setFileName(file.getOriginalFilename());
        policy.setFilePath(dest.getAbsolutePath()); // ✅ IMPORTANT

        return repository.save(policy);
    }
    public List<Policy> getAll() {
        return repository.findAll(); // Ensure your repository is injected
    }
    // Inside your PolicyService.java
    public List<PolicyResponse> getAllPolicies() {
        return repository.findAll().stream()
                .map(policy -> new PolicyResponse(
                        policy.getId(),
                        policy.getName(),
                        policy.getFileName()
                ))
                .collect(Collectors.toList());
    }

    public Optional<Policy> getById(Long id) {
        return repository.findById(id);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

}
