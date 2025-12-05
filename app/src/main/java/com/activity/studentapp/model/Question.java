package com.activity.studentapp.model;

import java.io.Serializable;
import java.util.List;

public class Question implements Serializable {
    private String id;
    private String quizId;
    private String questionText;
    @com.google.firebase.firestore.PropertyName("type")
    private String questionType; // "multiple_choice", "true_false", "short_answer"
    private List<String> options; // For multiple choice questions
    private String correctAnswer; // The correct answer
    private int points; // Points for this question
    private long createdAt;
    private String explanation; // Optional explanation for the answer

    public Question() {
        // Default constructor required for Firestore
    }

    public Question(String id, String quizId, String questionText, String questionType,
            List<String> options, String correctAnswer, int points, long createdAt, String explanation) {
        this.id = id;
        this.quizId = quizId;
        this.questionText = questionText;
        this.questionType = questionType;
        this.options = options;
        this.correctAnswer = correctAnswer;
        this.points = points;
        this.createdAt = createdAt;
        this.explanation = explanation;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuizId() {
        return quizId;
    }

    public void setQuizId(String quizId) {
        this.quizId = quizId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    // Helper methods
    public boolean isMultipleChoice() {
        return "multiple_choice".equals(questionType) || "MC".equals(questionType);
    }

    public boolean isTrueFalse() {
        return "true_false".equals(questionType) || "TF".equals(questionType);
    }

    public boolean isShortAnswer() {
        return "short_answer".equals(questionType) || "SA".equals(questionType) || "ID".equals(questionType);
    }
}