package com.bookexchange.model;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Timestamp;

/**
 * ============================================================================
 * Exchange Model (Java Bean / POJO)
 * ============================================================================
 * Represents an agreed physical exchange transaction or active textbook loan.
 *
 * Physical Exchange Lifecycle:
 * - AWAITING_HANDOVER : Exchange request accepted; students must meet on campus
 * - ACTIVE            : Handover confirmed for LENDING; textbook currently on loan
 * - COMPLETED         : Handover confirmed for SWAP or GIVEAWAY; ownership transferred
 * - RETURNED          : Both parties confirmed return of borrowed textbook (LENDING)
 * - OVERDUE           : Loan return deadline exceeded without return confirmation
 * - CANCELLED         : Handover cancelled by mutual agreement before meeting
 * - DISPUTED          : Issue reported with book condition or handover
 * ============================================================================
 */
public class Exchange implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long requestId;
    private Long bookId;
    private Long offeredBookId; // For SWAP mode
    private Long ownerId;       // Original book owner / lender
    private Long requesterId;   // Book recipient / borrower
    private String exchangeMode;// SWAP, GIVEAWAY, LENDING
    private String status;      // AWAITING_HANDOVER, ACTIVE, COMPLETED, RETURNED, OVERDUE, CANCELLED, DISPUTED

    private boolean ownerHandoverConfirmed;
    private boolean requesterHandoverConfirmed;
    private boolean ownerReturnConfirmed;
    private boolean requesterReturnConfirmed;

    private String handoverLocation;
    private String handoverNotes;
    private Date startDate;     // Date handover completed
    private Date dueDate;       // Return due date for LENDING
    private Date returnDate;    // Actual return date for LENDING

    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Joined metadata fields for frontend display
    private String bookTitle;
    private String bookAuthor;
    private String bookImageUrl;
    private String bookSubject;

    private String offeredBookTitle;
    private String offeredBookAuthor;
    private String offeredBookImageUrl;

    private String ownerName;
    private String ownerEmail;
    private String ownerDepartment;
    private String ownerCampus;

    private String requesterName;
    private String requesterEmail;
    private String requesterDepartment;
    private String requesterCampus;

    private Integer proposedDurationDays;

    public Exchange() {
    }

    public Exchange(Long requestId, Long bookId, Long offeredBookId, Long ownerId, Long requesterId,
                    String exchangeMode, String handoverLocation, String handoverNotes) {
        this.requestId = requestId;
        this.bookId = bookId;
        this.offeredBookId = offeredBookId;
        this.ownerId = ownerId;
        this.requesterId = requesterId;
        this.exchangeMode = exchangeMode;
        this.handoverLocation = handoverLocation;
        this.handoverNotes = handoverNotes;
        this.status = "AWAITING_HANDOVER";
        this.ownerHandoverConfirmed = false;
        this.requesterHandoverConfirmed = false;
        this.ownerReturnConfirmed = false;
        this.requesterReturnConfirmed = false;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Long getOfferedBookId() {
        return offeredBookId;
    }

    public void setOfferedBookId(Long offeredBookId) {
        this.offeredBookId = offeredBookId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public String getExchangeMode() {
        return exchangeMode;
    }

    public void setExchangeMode(String exchangeMode) {
        this.exchangeMode = exchangeMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isOwnerHandoverConfirmed() {
        return ownerHandoverConfirmed;
    }

    public void setOwnerHandoverConfirmed(boolean ownerHandoverConfirmed) {
        this.ownerHandoverConfirmed = ownerHandoverConfirmed;
    }

    public boolean isRequesterHandoverConfirmed() {
        return requesterHandoverConfirmed;
    }

    public void setRequesterHandoverConfirmed(boolean requesterHandoverConfirmed) {
        this.requesterHandoverConfirmed = requesterHandoverConfirmed;
    }

    public boolean isOwnerReturnConfirmed() {
        return ownerReturnConfirmed;
    }

    public void setOwnerReturnConfirmed(boolean ownerReturnConfirmed) {
        this.ownerReturnConfirmed = ownerReturnConfirmed;
    }

    public boolean isRequesterReturnConfirmed() {
        return requesterReturnConfirmed;
    }

    public void setRequesterReturnConfirmed(boolean requesterReturnConfirmed) {
        this.requesterReturnConfirmed = requesterReturnConfirmed;
    }

    public String getHandoverLocation() {
        return handoverLocation;
    }

    public void setHandoverLocation(String handoverLocation) {
        this.handoverLocation = handoverLocation;
    }

    public String getHandoverNotes() {
        return handoverNotes;
    }

    public void setHandoverNotes(String handoverNotes) {
        this.handoverNotes = handoverNotes;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public void setDueDate(Date dueDate) {
        this.dueDate = dueDate;
    }

    public Date getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(Date returnDate) {
        this.returnDate = returnDate;
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

    public Integer getProposedDurationDays() {
        return proposedDurationDays;
    }

    public void setProposedDurationDays(Integer proposedDurationDays) {
        this.proposedDurationDays = proposedDurationDays;
    }
}
