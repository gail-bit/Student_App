package com.activity.studentapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.activity.studentapp.model.Activity;
import com.activity.studentapp.model.Subject;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExamDetailsActivity extends AppCompatActivity {

    private static final int TAKE_EXAM_REQUEST = 1002;

    private Activity exam;
    private Subject subject;

    // UI components
    private TextView examTitle;
    private TextView examSubject;
    private TextView examInstructor;
    private TextView examDescription;
    private TextView examDuration;
    private TextView examQuestions;
    private TextView examDueDate;
    private ImageButton backButton;
    private com.google.android.material.button.MaterialButton btnTakeExam;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_details);

        // Get exam and subject data from intent
        exam = (Activity) getIntent().getSerializableExtra("exam");
        subject = (Subject) getIntent().getSerializableExtra("subject");

        if (exam == null) {
            Toast.makeText(this, "Exam data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupUI();
        setupClickListeners();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check if returning from TakeExamActivity
        if (requestCode == TAKE_EXAM_REQUEST && resultCode == RESULT_OK && data != null) {
            boolean examCompleted = data.getBooleanExtra("exam_completed", false);
            if (examCompleted) {
                // Exam was just completed, change button to "View Score"
                btnTakeExam.setText("View Score");
                btnTakeExam.setOnClickListener(v -> viewExamScore());
                Log.d("ExamDetailsActivity", "Exam completed, button changed to View Score");
            }
        }
    }

    private void initializeViews() {
        examTitle = findViewById(R.id.examTitle);
        examSubject = findViewById(R.id.examSubject);
        examInstructor = findViewById(R.id.examInstructor);
        examDescription = findViewById(R.id.examDescription);
        examDuration = findViewById(R.id.examDuration);
        examQuestions = findViewById(R.id.examQuestions);
        examDueDate = findViewById(R.id.examDueDate);
        backButton = findViewById(R.id.backButton);
        btnTakeExam = findViewById(R.id.btnTakeExam);
    }

    private void setupUI() {
        // Set exam title
        examTitle.setText(exam.getTitle() != null ? exam.getTitle() : "Untitled Exam");

        // Set subject name
        if (subject != null) {
            examSubject.setText(subject.getName() != null ? subject.getName() : "Unknown Subject");
            examInstructor.setText(subject.getInstructor() != null ? subject.getInstructor() : "Unknown Instructor");
        } else {
            examSubject.setText("Unknown Subject");
            examInstructor.setText("Unknown Instructor");
        }

        // Set exam description
        examDescription.setText(exam.getDescription() != null ? exam.getDescription() : "No description available");

        // Load exam duration from database
        loadExamDuration(exam.getId());

        // Load total questions from database
        loadQuestionCount(exam.getId());

        // Set published date
        if (exam.getPublishedAt() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            String publishedDateStr = sdf.format(new Date(exam.getPublishedAt()));
            examDueDate.setText("Published: " + publishedDateStr);
        } else if (exam.getCreatedAt() > 0) {
            // Fallback to created date if published date not available
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            String publishedDateStr = sdf.format(new Date(exam.getCreatedAt()));
            examDueDate.setText("Published: " + publishedDateStr);
        } else {
            examDueDate.setText("Date not available");
        }

        // Check if student has already taken this exam
        checkExamStatus();
    }

    private void setupClickListeners() {
        // Back button
        backButton.setOnClickListener(v -> finish());

        // Take Exam button click listener is set dynamically in checkExamStatus()
    }

    private void loadQuestionCount(String examId) {
        if (examId == null || examId.trim().isEmpty()) {
            examQuestions.setText("0 questions");
            return;
        }

        // First, get the exam document to find the correct exam identifier for
        // questions
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("exams")
                .document(examId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Query questions from the exam's subcollection
                        queryQuestions(examId);
                    } else {
                        Log.e("ExamDetailsActivity", "Exam document not found: " + examId);
                        examQuestions.setText("0 questions");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ExamDetailsActivity", "Failed to load exam document: " + e.getMessage());
                    examQuestions.setText("0 questions");
                });
    }

    private void queryQuestions(String examDocumentId) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("exams")
                .document(examDocumentId)
                .collection("questions")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        int questionCount = task.getResult().size();
                        String questionText = questionCount == 1 ? "1 question"
                                : questionCount + " questions";
                        examQuestions.setText(questionText);
                        Log.d("ExamDetailsActivity",
                                "Found " + questionCount + " questions for exam: " + examDocumentId);
                    } else {
                        Log.e("ExamDetailsActivity", "Error loading question count: " +
                                (task.getException() != null ? task.getException().getMessage()
                                        : "Unknown error"));
                        examQuestions.setText("0 questions");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ExamDetailsActivity", "Failed to load question count: " + e.getMessage());
                    examQuestions.setText("0 questions");
                });
    }

    private void loadExamDuration(String examId) {
        if (examId == null || examId.trim().isEmpty()) {
            examDuration.setText("Not specified");
            return;
        }

        // Get the exam document to find the duration
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("exams")
                .document(examId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Try to get duration from different possible field names
                        Long durationMinutes = documentSnapshot.getLong("duration");
                        if (durationMinutes == null) {
                            durationMinutes = documentSnapshot.getLong("timeLimit");
                        }
                        if (durationMinutes == null) {
                            durationMinutes = documentSnapshot.getLong("durationMinutes");
                        }

                        if (durationMinutes != null && durationMinutes > 0) {
                            if (durationMinutes == 1) {
                                examDuration.setText("1 minute");
                            } else {
                                examDuration.setText(durationMinutes + " minutes");
                            }
                            Log.d("ExamDetailsActivity", "Loaded exam duration: " + durationMinutes + " minutes");
                        } else {
                            examDuration.setText("Not specified");
                        }
                    } else {
                        Log.e("ExamDetailsActivity", "Exam document not found for duration: " + examId);
                        examDuration.setText("Not specified");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ExamDetailsActivity", "Failed to load exam duration: " + e.getMessage());
                    examDuration.setText("Not specified");
                });
    }

    private void checkExamStatus() {
        // Get student ID from shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        String studentId = sharedPreferences.getString("studentId", null);

        if (studentId == null || exam.getId() == null) {
            // No student logged in or invalid exam, show take exam button
            btnTakeExam.setText("Take Exam");
            btnTakeExam.setOnClickListener(v -> takeExam());
            return;
        }

        // Check if student has already taken this exam
        FirebaseFirestore.getInstance()
                .collection("exam_results")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("examId", exam.getId())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Student has taken the exam, show view score button
                        btnTakeExam.setText("View Score");
                        btnTakeExam.setOnClickListener(v -> viewExamScore());
                        Log.d("ExamDetailsActivity", "Student has already taken this exam");
                    } else {
                        // Student hasn't taken the exam, show take exam button
                        btnTakeExam.setText("Take Exam");
                        btnTakeExam.setOnClickListener(v -> takeExam());
                        Log.d("ExamDetailsActivity", "Student hasn't taken this exam yet");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ExamDetailsActivity", "Error checking exam status: " + e.getMessage());
                    // On permission error, check if we just came back from taking an exam
                    // by checking if there's a flag in intent
                    boolean justCompletedExam = getIntent().getBooleanExtra("exam_completed", false);
                    if (justCompletedExam) {
                        // Student just completed the exam, show view score button
                        btnTakeExam.setText("View Score");
                        btnTakeExam.setOnClickListener(v -> viewExamScore());
                        Log.d("ExamDetailsActivity", "Exam was just completed, showing View Score");
                        // Clear the flag
                        getIntent().removeExtra("exam_completed");
                    } else {
                        // Default to take exam on other errors
                        btnTakeExam.setText("Take Exam");
                        btnTakeExam.setOnClickListener(v -> takeExam());
                    }
                });
    }

    private void takeExam() {
        // Navigate to exam taking activity
        Intent intent = new Intent(this, TakeExamActivity.class);
        intent.putExtra("exam", exam);
        intent.putExtra("subject", subject);
        startActivityForResult(intent, TAKE_EXAM_REQUEST);

        Log.d("ExamDetailsActivity", "Navigating to take exam: " + exam.getTitle());
    }

    private void viewExamScore() {
        // Get student ID from shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        String studentId = sharedPreferences.getString("studentId", null);

        if (studentId == null) {
            Toast.makeText(this, "Student information not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Query the exam result
        FirebaseFirestore.getInstance()
                .collection("exam_results")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("examId", exam.getId())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Get the first (and should be only) result
                        QueryDocumentSnapshot document = (QueryDocumentSnapshot) querySnapshot.getDocuments().get(0);
                        int score = document.getLong("score") != null ? document.getLong("score").intValue() : 0;
                        int maxScore = document.getLong("maxScore") != null ? document.getLong("maxScore").intValue()
                                : 0;
                        double percentage = document.getDouble("percentage") != null ? document.getDouble("percentage")
                                : 0.0;

                        // Show score dialog or activity
                        String scoreMessage = String.format("Exam Score: %d/%d (%.1f%%)", score, maxScore, percentage);
                        Toast.makeText(this, scoreMessage, Toast.LENGTH_LONG).show();

                        Log.d("ExamDetailsActivity", "Showing exam score: " + scoreMessage);
                    } else {
                        Toast.makeText(this, "Score not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ExamDetailsActivity", "Error loading exam score: " + e.getMessage());
                    Toast.makeText(this, "Error loading score", Toast.LENGTH_SHORT).show();
                });
    }
}