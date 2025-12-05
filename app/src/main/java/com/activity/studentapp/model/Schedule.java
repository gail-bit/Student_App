package com.activity.studentapp.model;

public class Schedule {
    private String instructorId;
    private String subjectId;
    private String sectionId;
    private String day;
    private String timeFrame;
    private String instructorName;
    private String subjectName;
    private String sectionName;
    private long createdAt;

    // Default constructor required for Firestore
    public Schedule() {
    }

    public Schedule(String instructorId, String subjectId, String sectionId, String day, String timeFrame,
            String instructorName, String subjectName, String sectionName, long createdAt) {
        this.instructorId = instructorId;
        this.subjectId = subjectId;
        this.sectionId = sectionId;
        this.day = day != null ? day : "M-F"; // Default to M-F for weekly schedules
        this.timeFrame = timeFrame;
        this.instructorName = instructorName;
        this.subjectName = subjectName;
        this.sectionName = sectionName;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public String getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(String instructorId) {
        this.instructorId = instructorId;
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

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getTimeFrame() {
        return timeFrame;
    }

    public void setTimeFrame(String timeFrame) {
        this.timeFrame = timeFrame;
    }

    public String getInstructorName() {
        return instructorName;
    }

    public void setInstructorName(String instructorName) {
        this.instructorName = instructorName;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}