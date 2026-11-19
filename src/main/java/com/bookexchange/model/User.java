package com.bookexchange.model;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * ============================================================================
 * User Model (Java Bean / POJO)
 * ============================================================================
 * Represents a registered university student in the Book Exchange System.
 * Demonstrates: Encapsulation, Serializable interface, standard getters/setters.
 * ============================================================================
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String universityId;
    private String fullName;
    private String email;
    private String phone;
    private transient String passwordHash; // transient prevents accidental session serialization
    private String department;
    private int semester;
    private String campusName;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Default no-argument constructor (required for Java Beans)
    public User() {
    }

    // Parameterized constructor for convenient initialization
    public User(String universityId, String fullName, String email, String phone,
                String passwordHash, String department, int semester, String campusName) {
        this.universityId = universityId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.department = department;
        this.semester = semester;
        this.campusName = campusName;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUniversityId() {
        return universityId;
    }

    public void setUniversityId(String universityId) {
        this.universityId = universityId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) {
        this.semester = semester;
    }

    public String getCampusName() {
        return campusName;
    }

    public void setCampusName(String campusName) {
        this.campusName = campusName;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", universityId='" + universityId + '\'' +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", department='" + department + '\'' +
                ", semester=" + semester +
                ", campusName='" + campusName + '\'' +
                '}';
    }
}
