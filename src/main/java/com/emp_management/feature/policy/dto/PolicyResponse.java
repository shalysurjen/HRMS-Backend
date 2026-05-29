package com.emp_management.feature.policy.dto;


public class PolicyResponse {

    private Long id;
    private String name;
    private String fileName; // Ensure this matches p.getFileName() in the controller

    public PolicyResponse(Long id, String name, String fileName) {
        this.id = id;
        this.name = name;
        this.fileName = fileName;
    }



    public Long getId() { return id; }
    public String getName() { return name; }
    public String getFileName() { return fileName; }
}