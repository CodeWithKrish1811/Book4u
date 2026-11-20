package com.bookexchange.model;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * ============================================================================
 * ExchangeRequest Model (Java Bean / POJO)
 * ============================================================================
 * Represents a formal request submitted by a student to exchange, borrow,
 * or receive a textbook from another student.
 *
 * Supported Request Statuses:
 * - PENDING   : Awaiting owner review and decision
 * - ACCEPTED  : Owner agreed; exchange transaction initialized
 * - REJECTED  : Owner declined the request
 * - CANCELLED : Requester retracted the pending request
 * - EXPIRED   : Timed out before response
 * ============================================================================
 */
public class ExchangeRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long bookId;
    private Long requesterId;
    private Long ownerId;
    private String exchangeMode;          // SWAP, GIVEAWAY, LENDING
    private Long offeredBookId;           // Required for SWAP
    private Integer proposedDurationDays; // Required for LENDING
    private String requestMessage;
    private String status;                // PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED
    private String handoverLocation;
    private Timestamp handoverTime;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Joined metadata fields for frontend display
    private String bookTitle;
    private String bookAuthor;
    private String bookImageUrl;
    private String bookSubject;
    private String bookConditionGrade;

    private String requesterName;
    private String requesterEmail;
    private String requesterDepartment;
    private String requesterCampus;

    private String ownerName;
    private String ownerEmail;
    private String ownerDepartment;
    private String ownerCampus;

    private String offeredBookTitle;
    private String offeredBookAuthor;
    private String offeredBookImageUrl;

    private Long exchangeId; // Linked active/completed exchange ID if ACCEPTED

    public ExchangeRequest() {
    }

    public ExchangeRequest(Long bookId, Long requesterId, Long ownerId, String exchangeMode,
                           Long offeredBookId, Integer proposedDurationDays, String requestMessage,
                           String handoverLocation) {
        this.bookId = bookId;
        this.requesterId = requesterId;
        this.ownerId = ownerId;
        this.exchangeMode = exchangeMode;
        this.offeredBookId = offeredBookId;
        this.proposedDurationDays = proposedDurationDays;
        this.requestMessage = requestMessage;
        this.handoverLocation = handoverLocation;
        this.status = "PENDING";
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getExchangeMode() {
        return exchangeMode;
    }

    public void setExchangeMode(String exchangeMode) {
        this.exchangeMode = exchangeMode;
    }

    public Long getOfferedBookId() {
        return offeredBookId;
    }

    public void setOfferedBookId(Long offeredBookId) {
        this.offeredBookId = offeredBookId;
    }

    public Integer getProposedDurationDays() {
        return proposedDurationDays;
    }

    public void setProposedDurationDays(Integer proposedDurationDays) {
        this.proposedDurationDays = proposedDurationDays;
    }

    public String getRequestMessage() {
        return requestMessage;
    }

    public void setRequestMessage(String requestMessage) {
        this.requestMessage = requestMessage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHandoverLocation() {
        return handoverLocation;
    }

    public void setHandoverLocation(String handoverLocation) {
        this.handoverLocation = handoverLocation;
    }

    public Timestamp getHandoverTime() {
        return handoverTime;
    }

    public void setHandoverTime(Timestamp handoverTime) {
        this.handoverTime = handoverTime;
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

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getBookAuthor() {
        return bookAuthor;
    }

    public void setBookAuthor(String bookAuthor) {
        this.bookAuthor = bookAuthor;
    }

    public String getBookImageUrl() {
        return bookImageUrl;
    }

    public void setBookImageUrl(String bookImageUrl) {
        this.bookImageUrl = bookImageUrl;
    }

    public String getBookSubject() {
        return bookSubject;
    }

    public void setBookSubject(String bookSubject) {
        this.bookSubject = bookSubject;
    }

    public String getBookConditionGrade() {
        return bookConditionGrade;
    }

    public void setBookConditionGrade(String bookConditionGrade) {
        this.bookConditionGrade = bookConditionGrade;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }

    public String getRequesterDepartment() {
        return requesterDepartment;
    }

    public void setRequesterDepartment(String requesterDepartment) {
        this.requesterDepartment = requesterDepartment;
    }

    public String getRequesterCampus() {
        return requesterCampus;
    }

    public void setRequesterCampus(String requesterCampus) {
        this.requesterCampus = requesterCampus;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getOwnerDepartment() {
        return ownerDepartment;
    }

    public void setOwnerDepartment(String ownerDepartment) {
        this.ownerDepartment = ownerDepartment;
    }

    public String getOwnerCampus() {
        return ownerCampus;
    }

    public void setOwnerCampus(String ownerCampus) {
        this.ownerCampus = ownerCampus;
    }

    public String getOfferedBookTitle() {
        return offeredBookTitle;
    }

    public void setOfferedBookTitle(String offeredBookTitle) {
        this.offeredBookTitle = offeredBookTitle;
    }

    public String getOfferedBookAuthor() {
        return offeredBookAuthor;
    }

    public void setOfferedBookAuthor(String offeredBookAuthor) {
        this.offeredBookAuthor = offeredBookAuthor;
    }

    public String getOfferedBookImageUrl() {
        return offeredBookImageUrl;
    }

    public void setOfferedBookImageUrl(String offeredBookImageUrl) {
        this.offeredBookImageUrl = offeredBookImageUrl;
    }

    public Long getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(Long exchangeId) {
        this.exchangeId = exchangeId;
    }
}
