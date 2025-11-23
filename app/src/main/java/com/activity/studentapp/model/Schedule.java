package com.activity.studentapp.model;

public class Schedule {
    private String subjectName;
    private String instructorName;
    private String timeFrame;

    public Schedule() {}

    public Schedule(String subjectName, String instructorName, String timeFrame) {
        this.subjectName = subjectName;
        this.instructorName = instructorName;
        this.timeFrame = timeFrame;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getInstructorName() {
        return instructorName;
    }

    public void setInstructorName(String instructorName) {
        this.instructorName = instructorName;
    }

    public String getTimeFrame() {
        return timeFrame;
    }

    public void setTimeFrame(String timeFrame) {
        this.timeFrame = timeFrame;
    }
}