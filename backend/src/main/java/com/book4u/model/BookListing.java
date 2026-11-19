package com.book4u.model;

import java.time.LocalDateTime;

/**
 * Domain model representing an academic book listing.
 * Pure POJO designed for Spring JDBC RowMapper integration (no JPA entity annotations).
 */
public class BookListing {

    private Long id;
    private Long ownerId;
    private String title;
    private String author;
    private String isbn;
    private String edition;
    private String courseCode;
    private String department;
    private ConditionGrade conditionGrade;
    private SharingMode sharingMode;
    private ListingStatus status;
    private String swapPreferences;
    private Integer lendingMaxDurationDays;
    private String description;
    private String coverImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BookListing() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public ConditionGrade getConditionGrade() {
        return conditionGrade;
    }

    public void setConditionGrade(ConditionGrade conditionGrade) {
        this.conditionGrade = conditionGrade;
    }

    public SharingMode getSharingMode() {
        return sharingMode;
    }

    public void setSharingMode(SharingMode sharingMode) {
        this.sharingMode = sharingMode;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public String getSwapPreferences() {
        return swapPreferences;
    }

    public void setSwapPreferences(String swapPreferences) {
        this.swapPreferences = swapPreferences;
    }

    public Integer getLendingMaxDurationDays() {
        return lendingMaxDurationDays;
    }

    public void setLendingMaxDurationDays(Integer lendingMaxDurationDays) {
        this.lendingMaxDurationDays = lendingMaxDurationDays;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
