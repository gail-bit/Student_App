package com.activity.studentapp.model;

public class ScheduleRow {
    private String time;
    private String[] subjects; // 5 days

    public ScheduleRow(String time) {
        this.time = time;
        this.subjects = new String[5];
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String[] getSubjects() {
        return subjects;
    }

    public void setSubject(int dayIndex, String subject) {
        if (dayIndex >= 0 && dayIndex < 5) {
            subjects[dayIndex] = subject;
        }
    }
}