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

public class QuizDetailsActivity extends AppCompatActivity {

    private static final int TAKE_QUIZ_REQUEST = 1001;

    private Activity quiz;
    private Subject subject;

    // UI components
    private TextView quizTitle;
    private TextView quizSubject;
    private TextView quizInstructor;
    private TextView quizDescription;
    private TextView quizDuration;
    private TextView quizQuestions;
    private TextView quizDueDate;
    private ImageButton backButton;
    private com.google.android.material.button.MaterialButton btnTakeQuiz;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_details);

        // Get quiz and subject data from intent
        quiz = (Activity) getIntent().getSerializableExtra("quiz");
        subject = (Subject) getIntent().getSerializableExtra("subject");

        if (quiz == null) {
            Toast.makeText(this, "Quiz data not found", Toast.LENGTH_SHORT).show();
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

        // Check if returning from TakeQuizActivity
        if (requestCode == TAKE_QUIZ_REQUEST && resultCode == RESULT_OK && data != null) {
            boolean quizCompleted = data.getBooleanExtra("quiz_completed", false);
            if (quizCompleted) {
                // Quiz was just completed, change button to "View Score"
                btnTakeQuiz.setText("View Score");
                btnTakeQuiz.setOnClickListener(v -> viewQuizScore());
                Log.d("QuizDetailsActivity", "Quiz completed, button changed to View Score");
            }
        }
    }

    private void initializeViews() {
        quizTitle = findViewById(R.id.quizTitle);
        quizSubject = findViewById(R.id.quizSubject);
        quizInstructor = findViewById(R.id.quizInstructor);
        quizDescription = findViewById(R.id.quizDescription);
        quizDuration = findViewById(R.id.quizDuration);
        quizQuestions = findViewById(R.id.quizQuestions);
        quizDueDate = findViewById(R.id.quizDueDate);
        backButton = findViewById(R.id.backButton);
        btnTakeQuiz = findViewById(R.id.btnTakeQuiz);
    }

    private void setupUI() {
        // Set quiz title
        quizTitle.setText(quiz.getTitle() != null ? quiz.getTitle() : "Untitled Quiz");

        // Set subject name
        if (subject != null) {
            quizSubject.setText(subject.getName() != null ? subject.getName() : "Unknown Subject");
            quizInstructor.setText(subject.getInstructor() != null ? subject.getInstructor() : "Unknown Instructor");
        } else {
            quizSubject.setText("Unknown Subject");
            quizInstructor.setText("Unknown Instructor");
        }

        // Set quiz description
        quizDescription.setText(quiz.getDescription() != null ? quiz.getDescription() : "No description available");

        // Load quiz duration from database
        loadQuizDuration(quiz.getId());

        // Load total questions from database
        loadQuestionCount(quiz.getId());

        // Set published date
        if (quiz.getPublishedAt() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            String publishedDateStr = sdf.format(new Date(quiz.getPublishedAt()));
            quizDueDate.setText("Published: " + publishedDateStr);
        } else if (quiz.getCreatedAt() > 0) {
            // Fallback to created date if published date not available
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            String publishedDateStr = sdf.format(new Date(quiz.getCreatedAt()));
            quizDueDate.setText("Published: " + publishedDateStr);
        } else {
            quizDueDate.setText("Date not available");
        }

        // Check if student has already taken this quiz
        checkQuizStatus();
    }

    private void setupClickListeners() {
        // Back button
        backButton.setOnClickListener(v -> finish());

        // Take Quiz button click listener is set dynamically in checkQuizStatus()
    }

    private void loadQuestionCount(String quizId) {
        if (quizId == null || quizId.trim().isEmpty()) {
            quizQuestions.setText("0 questions");
            return;
        }

        // First, get the quiz document to find the correct quiz identifier for
        // questions
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("quizzes")
                .document(quizId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Query questions from the quiz's subcollection
                        queryQuestions(quizId);
                    } else {
                        Log.e("QuizDetailsActivity", "Quiz document not found: " + quizId);
                        quizQuestions.setText("0 questions");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("QuizDetailsActivity", "Failed to load quiz document: " + e.getMessage());
                    quizQuestions.setText("0 questions");
                });
    }

    private void queryQuestions(String quizDocumentId) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("quizzes")
                .document(quizDocumentId)
                .collection("questions")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        int questionCount = task.getResult().size();
                        String questionText = questionCount == 1 ? "1 question"
                                : questionCount + " questions";
                        quizQuestions.setText(questionText);
                        Log.d("QuizDetailsActivity",
                                "Found " + questionCount + " questions for quiz: " + quizDocumentId);
                    } else {
                        Log.e("QuizDetailsActivity", "Error loading question count: " +
                                (task.getException() != null ? task.getException().getMessage()
                                        : "Unknown error"));
                        quizQuestions.setText("0 questions");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("QuizDetailsActivity", "Failed to load question count: " + e.getMessage());
                    quizQuestions.setText("0 questions");
                });
    }

    private void loadQuizDuration(String quizId) {
        if (quizId == null || quizId.trim().isEmpty()) {
            quizDuration.setText("Not specified");
            return;
        }

        // Get the quiz document to find the duration
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("quizzes")
                .document(quizId)
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
                                quizDuration.setText("1 minute");
                            } else {
                                quizDuration.setText(durationMinutes + " minutes");
                            }
                            Log.d("QuizDetailsActivity", "Loaded quiz duration: " + durationMinutes + " minutes");
                        } else {
                            quizDuration.setText("Not specified");
                        }
                    } else {
                        Log.e("QuizDetailsActivity", "Quiz document not found for duration: " + quizId);
                        quizDuration.setText("Not specified");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("QuizDetailsActivity", "Failed to load quiz duration: " + e.getMessage());
                    quizDuration.setText("Not specified");
                });
    }

    private void checkQuizStatus() {
        // Get student ID from shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        String studentId = sharedPreferences.getString("studentId", null);

        if (studentId == null || quiz.getId() == null) {
            // No student logged in or invalid quiz, show take quiz button
            btnTakeQuiz.setText("Take Quiz");
            btnTakeQuiz.setOnClickListener(v -> takeQuiz());
            return;
        }

        // Check if student has already taken this quiz
        FirebaseFirestore.getInstance()
                .collection("quiz_results")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("quizId", quiz.getId())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Student has taken the quiz, show view score button
                        btnTakeQuiz.setText("View Score");
                        btnTakeQuiz.setOnClickListener(v -> viewQuizScore());
                        Log.d("QuizDetailsActivity", "Student has already taken this quiz");
                    } else {
                        // Student hasn't taken the quiz, show take quiz button
                        btnTakeQuiz.setText("Take Quiz");
                        btnTakeQuiz.setOnClickListener(v -> takeQuiz());
                        Log.d("QuizDetailsActivity", "Student hasn't taken this quiz yet");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("QuizDetailsActivity", "Error checking quiz status: " + e.getMessage());
                    // On permission error, check if we just came back from taking a quiz
                    // by checking if there's a flag in intent
                    boolean justCompletedQuiz = getIntent().getBooleanExtra("quiz_completed", false);
                    if (justCompletedQuiz) {
                        // Student just completed the quiz, show view score button
                        btnTakeQuiz.setText("View Score");
                        btnTakeQuiz.setOnClickListener(v -> viewQuizScore());
                        Log.d("QuizDetailsActivity", "Quiz was just completed, showing View Score");
                        // Clear the flag
                        getIntent().removeExtra("quiz_completed");
                    } else {
                        // Default to take quiz on other errors
                        btnTakeQuiz.setText("Take Quiz");
                        btnTakeQuiz.setOnClickListener(v -> takeQuiz());
                    }
                });
    }

    private void takeQuiz() {
        // Navigate to quiz taking activity
        Intent intent = new Intent(this, TakeQuizActivity.class);
        intent.putExtra("quiz", quiz);
        intent.putExtra("subject", subject);
        startActivityForResult(intent, TAKE_QUIZ_REQUEST);

        Log.d("QuizDetailsActivity", "Navigating to take quiz: " + quiz.getTitle());
    }

    private void viewQuizScore() {
        // Get student ID from shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        String studentId = sharedPreferences.getString("studentId", null);

        if (studentId == null) {
            Toast.makeText(this, "Student information not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Query the quiz result
        FirebaseFirestore.getInstance()
                .collection("quiz_results")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("quizId", quiz.getId())
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
                        String scoreMessage = String.format("Quiz Score: %d/%d (%.1f%%)", score, maxScore, percentage);
                        Toast.makeText(this, scoreMessage, Toast.LENGTH_LONG).show();

                        Log.d("QuizDetailsActivity", "Showing quiz score: " + scoreMessage);
                    } else {
                        Toast.makeText(this, "Score not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("QuizDetailsActivity", "Error loading quiz score: " + e.getMessage());
                    Toast.makeText(this, "Error loading score", Toast.LENGTH_SHORT).show();
                });
    }
}