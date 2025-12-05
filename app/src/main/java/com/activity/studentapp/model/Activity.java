package com.activity.studentapp.model;

import java.io.Serializable;

public class Activity implements Serializable {
    private String id;
    private String title;
    private String description;
    private String subjectId;
    private String sectionId;
    private String instructorId;
    private String instructorName;
    private long createdAt;
    private long publishedAt;
    private long dueDate;
    private int totalPoints;
    private boolean published;
    private String status;

    public Activity() {
        // Default constructor required for Firestore
    }

    public Activity(String id, String title, String description, String subjectId, String sectionId,
                    String instructorId, String instructorName, long createdAt, long publishedAt, long dueDate,
                    int totalPoints, boolean published, String status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.subjectId = subjectId;
        this.sectionId = sectionId;
        this.instructorId = instructorId;
        this.instructorName = instructorName;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.dueDate = dueDate;
        this.totalPoints = totalPoints;
        this.published = published;
        this.status = status;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public void setSectionId(String sectionId) {
        this.sectionId = sectionId;
    }

    public String getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(String instructorId) {
        this.instructorId = instructorId;
    }

    public String getInstructorName() {
        return instructorName;
    }

    public void setInstructorName(String instructorName) {
        this.instructorName = instructorName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(long publishedAt) {
        this.publishedAt = publishedAt;
    }

    public long getDueDate() {
        return dueDate;
    }

    public void setDueDate(long dueDate) {
        this.dueDate = dueDate;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}