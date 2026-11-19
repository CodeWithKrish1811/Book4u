package com.bookexchange.model;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * ============================================================================
 * Book Model (Java Bean / POJO)
 * ============================================================================
 * Represents an academic book listed for peer-to-peer sharing.
 * Supported sharing modes:
 * - SWAP: Direct book-for-book trade
 * - GIVEAWAY: Altruistic free gift
 * - LENDING: Time-bound temporary loan
 * ============================================================================
 */
public class Book implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long ownerId;
    private String ownerName; // Joined from students table for UI convenience
    private String title;
    private String author;
    private String isbn;
    private String edition;
    private String courseCode;
    private String subject;
    private String conditionGrade; // NEW, LIKE_NEW, GOOD, FAIR
    private String sharingMode;    // SWAP, GIVEAWAY, LENDING
    private String status;         // AVAILABLE, RESERVED, ON_LOAN, UNAVAILABLE
    private String description;
    private Integer lendingMaxDurationDays;
    private String swapPreferences;
    private Timestamp createdAt;

    public Book() {
    }

    public Book(Long ownerId, String title, String author, String isbn, String edition,
                String courseCode, String subject, String conditionGrade, String sharingMode,
                String status, String description, Integer lendingMaxDurationDays, String swapPreferences) {
        this.ownerId = ownerId;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.edition = edition;
        this.courseCode = courseCode;
        this.subject = subject;
        this.conditionGrade = conditionGrade;
        this.sharingMode = sharingMode;
        this.status = status;
        this.description = description;
        this.lendingMaxDurationDays = lendingMaxDurationDays;
        this.swapPreferences = swapPreferences;
    }

    // Getters and Setters
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

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
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

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getConditionGrade() {
        return conditionGrade;
    }

    public void setConditionGrade(String conditionGrade) {
        this.conditionGrade = conditionGrade;
    }

    public String getSharingMode() {
        return sharingMode;
    }

    public void setSharingMode(String sharingMode) {
        this.sharingMode = sharingMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getLendingMaxDurationDays() {
        return lendingMaxDurationDays;
    }

    public void setLendingMaxDurationDays(Integer lendingMaxDurationDays) {
        this.lendingMaxDurationDays = lendingMaxDurationDays;
    }

    public String getSwapPreferences() {
        return swapPreferences;
    }

    public void setSwapPreferences(String swapPreferences) {
        this.swapPreferences = swapPreferences;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Book{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", subject='" + subject + '\'' +
                ", sharingMode='" + sharingMode + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
