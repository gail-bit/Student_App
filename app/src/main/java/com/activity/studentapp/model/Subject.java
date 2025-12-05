package com.activity.studentapp.model;

import java.io.Serializable;

public class Subject implements Serializable {
    private String id;
    private String code;
    private String name;
    private String description;
    private String instructor;
    private String schedule;
    private String room;

    // Required empty constructor for Firestore
    public Subject() {
    }

    public Subject(String id, String code, String name, String description, String instructor, String schedule,
            String room) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.instructor = instructor;
        this.schedule = schedule;
        this.room = room;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }
}
