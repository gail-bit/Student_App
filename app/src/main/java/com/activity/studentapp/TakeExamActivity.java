package com.activity.studentapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.activity.studentapp.model.Activity;
import com.activity.studentapp.model.Question;
import com.activity.studentapp.model.Subject;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TakeExamActivity extends AppCompatActivity {

    private Activity exam;
    private Subject subject;
    private List<Question> questions;
    private int currentQuestionIndex = 0;
    private Map<String, String> userAnswers;
    private boolean isFinishingNormally = false;

    // UI components
    private TextView tvQuestionNumber;
    private TextView tvQuestion;
    private RadioGroup radioGroupOptions;
    private com.google.android.material.textfield.TextInputLayout dropdownInputAnswer;
    private android.widget.AutoCompleteTextView dropdownAnswer;
    private Button btnPrevious;
    private Button btnNext;
    private Button btnSubmit;
    private ProgressBar progressBar;
    private ImageButton backButton;
    private LinearLayout examContent;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_exam);

        // Set fullscreen and prevent screenshots to avoid cheating
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Get exam and subject data from intent
        exam = (Activity) getIntent().getSerializableExtra("exam");
        subject = (Subject) getIntent().getSerializableExtra("subject");

        if (exam == null) {
            Toast.makeText(this, "Exam data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize data structures
        questions = new ArrayList<>();
        userAnswers = new HashMap<>();

        initializeViews();
        setupUI();
        loadExamQuestions();
    }

    private void initializeViews() {
        tvQuestionNumber = findViewById(R.id.tvQuestionNumber);
        tvQuestion = findViewById(R.id.tvQuestion);
        radioGroupOptions = findViewById(R.id.radioGroupOptions);
        dropdownInputAnswer = findViewById(R.id.dropdownInputAnswer);
        dropdownAnswer = findViewById(R.id.dropdownAnswer);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        btnSubmit = findViewById(R.id.btnSubmit);
        progressBar = findViewById(R.id.progressBar);
        backButton = findViewById(R.id.backButton);
        examContent = findViewById(R.id.examContent);
    }

    private void setupUI() {
        // Set exam title in action bar or header if available
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(exam.getTitle() != null ? exam.getTitle() : "Take Exam");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initially hide exam content until questions are loaded
        examContent.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        setupClickListeners();
    }

    private void setupClickListeners() {
        btnPrevious.setOnClickListener(v -> showPreviousQuestion());
        btnNext.setOnClickListener(v -> showNextQuestion());
        btnSubmit.setOnClickListener(v -> submitExam());

        backButton.setOnClickListener(v -> {
            Toast.makeText(this, "You cannot go back during the exam. Please finish the exam to exit.",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void loadExamQuestions() {
        if (exam.getId() == null) {
            Toast.makeText(this, "Exam ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Load questions from exam's subcollection
        db.collection("exams")
                .document(exam.getId())
                .collection("questions")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    questions.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            // Debug: Log all fields in the document
                            Log.d("TakeExamActivity", "Document data: " + document.getData());

                            Question question = new Question();
                            question.setId(document.getId());

                            // Load question type
                            String questionType = document.getString("type");
                            if (questionType == null) {
                                questionType = document.getString("questionType");
                            }
                            question.setQuestionType(questionType);

                            // Try different possible field names for question text
                            String questionText = document.getString("questionText");
                            if (questionText == null) {
                                questionText = document.getString("question");
                            }
                            if (questionText == null) {
                                questionText = document.getString("text");
                            }
                            question.setQuestionText(questionText);

                            // Handle options - could be individual fields or a list
                            List<String> optionsList = new ArrayList<>();
                            String optionA = document.getString("optionA");
                            String optionB = document.getString("optionB");
                            String optionC = document.getString("optionC");
                            String optionD = document.getString("optionD");

                            // Also try alternative field names
                            if (optionA == null)
                                optionA = document.getString("choiceA");
                            if (optionB == null)
                                optionB = document.getString("choiceB");
                            if (optionC == null)
                                optionC = document.getString("choiceC");
                            if (optionD == null)
                                optionD = document.getString("choiceD");

                            if (optionA != null)
                                optionsList.add(optionA);
                            if (optionB != null)
                                optionsList.add(optionB);
                            if (optionC != null)
                                optionsList.add(optionC);
                            if (optionD != null)
                                optionsList.add(optionD);

                            // Try to get options as a list/array if individual fields don't exist
                            if (optionsList.isEmpty()) {
                                List<String> optionsFromList = (List<String>) document.get("options");
                                if (optionsFromList != null) {
                                    optionsList.addAll(optionsFromList);
                                }
                            }

                            question.setOptions(optionsList);

                            // For identification questions, we'll collect all correct answers later
                            // and use them as options for all ID questions

                            // Handle correctAnswer field - could be Boolean or String
                            Object correctAnswerObj = document.get("correctAnswer");
                            if (correctAnswerObj instanceof Boolean) {
                                question.setCorrectAnswer(((Boolean) correctAnswerObj) ? "true" : "false");
                            } else if (correctAnswerObj instanceof String) {
                                question.setCorrectAnswer((String) correctAnswerObj);
                            } else {
                                question.setCorrectAnswer(""); // Default empty
                            }

                            questions.add(question);
                            Log.d("TakeExamActivity", "Loaded question: " + question.getQuestionText());
                            Log.d("TakeExamActivity", "Options count: "
                                    + (question.getOptions() != null ? question.getOptions().size() : 0));
                        } catch (Exception e) {
                            Log.e("TakeExamActivity", "Error parsing question: " + e.getMessage());
                        }
                    }

                    // Collect all correct answers from identification questions
                    // and use them as options for all ID questions
                    List<String> allIdAnswers = new ArrayList<>();
                    for (Question q : questions) {
                        if (q.isShortAnswer()) {
                            String correctAnswer = q.getCorrectAnswer();
                            if (correctAnswer != null && !correctAnswer.trim().isEmpty()
                                    && !allIdAnswers.contains(correctAnswer)) {
                                allIdAnswers.add(correctAnswer);
                            }
                        }
                    }

                    // Set the collected answers as options for all identification questions
                    for (Question q : questions) {
                        if (q.isShortAnswer() && !allIdAnswers.isEmpty()) {
                            q.setOptions(new ArrayList<>(allIdAnswers)); // Create a copy
                        }
                    }

                    Log.d("TakeExamActivity", "Collected " + allIdAnswers.size()
                            + " answers for identification questions: " + allIdAnswers);

                    // Randomize question order to prevent cheating
                    Collections.shuffle(questions);

                    if (!questions.isEmpty()) {
                        // Record exam attempt
                        recordExamAttempt();

                        // Show first question
                        displayCurrentQuestion();
                        updateNavigationButtons();

                        // Show exam content
                        examContent.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);

                        Log.d("TakeExamActivity", "Total questions loaded: " + questions.size());
                    } else {
                        Toast.makeText(this, "No questions found for this exam", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("TakeExamActivity", "Error loading exam questions: " + e.getMessage());
                    Toast.makeText(this, "Error loading exam questions", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void recordExamAttempt() {
        android.content.SharedPreferences sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        String studentId = sharedPreferences.getString("studentId", null);

        if (studentId != null && exam != null) {
            Map<String, Object> attemptData = new HashMap<>();
            attemptData.put("studentId", studentId);
            attemptData.put("examId", exam.getId());
            attemptData.put("subjectId", subject != null ? subject.getId() : null);
            attemptData.put("startedAt", com.google.firebase.Timestamp.now());
            attemptData.put("status", "in_progress");

            db.collection("examAttempts")
                    .document(studentId + "_" + exam.getId())
                    .set(attemptData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("TakeExamActivity", "Exam attempt recorded for student: " + studentId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("TakeExamActivity", "Failed to record exam attempt: " + e.getMessage());
                    });
        }
    }

    private void displayCurrentQuestion() {
        if (questions.isEmpty() || currentQuestionIndex >= questions.size()) {
            return;
        }

        Question question = questions.get(currentQuestionIndex);

        // Update question number
        tvQuestionNumber.setText("Question " + (currentQuestionIndex + 1) + " of " + questions.size());

        // Set question text
        tvQuestion.setText(question.getQuestionText() != null ? question.getQuestionText() : "Question not available");

        // Clear previous options
        radioGroupOptions.removeAllViews();
        radioGroupOptions.setVisibility(View.GONE);
        dropdownInputAnswer.setVisibility(View.GONE);

        // Handle different question types
        if (question.isMultipleChoice()) {
            // Multiple choice: show radio buttons with options
            radioGroupOptions.setVisibility(View.VISIBLE);
            List<String> options = question.getOptions();
            if (options != null && !options.isEmpty()) {
                char optionLetter = 'A';
                for (String option : options) {
                    if (option != null && !option.trim().isEmpty()) {
                        RadioButton radioButton = new RadioButton(this);
                        radioButton.setText(optionLetter + ". " + option);
                        radioButton.setTag(String.valueOf(optionLetter));
                        radioButton.setTextSize(16);
                        radioButton.setPadding(16, 16, 16, 16);

                        // Check if this option was previously selected
                        String savedAnswer = userAnswers.get(question.getId());
                        if (savedAnswer != null && savedAnswer.equals(String.valueOf(optionLetter))) {
                            radioButton.setChecked(true);
                        }

                        radioGroupOptions.addView(radioButton);
                        optionLetter++;
                    }
                }
            }

            // Set listener for radio group
            radioGroupOptions.setOnCheckedChangeListener((group, checkedId) -> {
                RadioButton selectedRadioButton = findViewById(checkedId);
                if (selectedRadioButton != null) {
                    String selectedOption = (String) selectedRadioButton.getTag();
                    userAnswers.put(question.getId(), selectedOption);
                    Log.d("TakeExamActivity", "Answer saved for question " + question.getId() + ": " + selectedOption);
                }
            });

        } else if (question.isTrueFalse()) {
            // True/False: show True and False radio buttons
            radioGroupOptions.setVisibility(View.VISIBLE);

            // Add True option
            RadioButton trueButton = new RadioButton(this);
            trueButton.setText("A. True");
            trueButton.setTag("true");
            trueButton.setTextSize(16);
            trueButton.setPadding(16, 16, 16, 16);

            // Add False option
            RadioButton falseButton = new RadioButton(this);
            falseButton.setText("B. False");
            falseButton.setTag("false");
            falseButton.setTextSize(16);
            falseButton.setPadding(16, 16, 16, 16);

            // Check if previously selected
            String savedAnswer = userAnswers.get(question.getId());
            if ("true".equals(savedAnswer)) {
                trueButton.setChecked(true);
            } else if ("false".equals(savedAnswer)) {
                falseButton.setChecked(true);
            }

            radioGroupOptions.addView(trueButton);
            radioGroupOptions.addView(falseButton);

            // Set listener for radio group
            radioGroupOptions.setOnCheckedChangeListener((group, checkedId) -> {
                RadioButton selectedRadioButton = findViewById(checkedId);
                if (selectedRadioButton != null) {
                    String selectedOption = (String) selectedRadioButton.getTag();
                    userAnswers.put(question.getId(), selectedOption);
                    Log.d("TakeExamActivity", "Answer saved for question " + question.getId() + ": " + selectedOption);
                }
            });

        } else if (question.isShortAnswer()) {
            // Identification/Short Answer: show dropdown with possible answers
            dropdownInputAnswer.setVisibility(View.VISIBLE);

            List<String> possibleAnswers = question.getOptions();
            if (possibleAnswers != null && !possibleAnswers.isEmpty()) {
                // Create ArrayAdapter for dropdown
                android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        possibleAnswers);
                dropdownAnswer.setAdapter(adapter);

                // Set previously selected answer if any
                String savedAnswer = userAnswers.get(question.getId());
                if (savedAnswer != null) {
                    dropdownAnswer.setText(savedAnswer, false);
                } else {
                    dropdownAnswer.setText("", false);
                }

                // Set listener for selection changes
                dropdownAnswer.setOnItemClickListener((parent, view, position, id) -> {
                    String selectedAnswer = possibleAnswers.get(position);
                    userAnswers.put(question.getId(), selectedAnswer);
                    Log.d("TakeExamActivity", "Answer saved for question " + question.getId() + ": " + selectedAnswer);
                });
            } else {
                // No possible answers available, hide dropdown
                dropdownInputAnswer.setVisibility(View.GONE);
                Log.w("TakeExamActivity",
                        "No possible answers available for identification question: " + question.getId());
            }
        } else {
            // Hide dropdown for other question types
            dropdownInputAnswer.setVisibility(View.GONE);
        }
    }

    private void updateNavigationButtons() {
        // Previous button
        btnPrevious.setEnabled(currentQuestionIndex > 0);
        btnPrevious.setAlpha(currentQuestionIndex > 0 ? 1.0f : 0.5f);

        // Next button
        btnNext.setEnabled(currentQuestionIndex < questions.size() - 1);
        btnNext.setAlpha(currentQuestionIndex < questions.size() - 1 ? 1.0f : 0.5f);

        // Submit button - only show on last question
        btnSubmit.setVisibility(currentQuestionIndex == questions.size() - 1 ? View.VISIBLE : View.GONE);
    }

    private void showPreviousQuestion() {
        if (currentQuestionIndex > 0) {
            currentQuestionIndex--;
            displayCurrentQuestion();
            updateNavigationButtons();
        }
    }

    private void showNextQuestion() {
        if (currentQuestionIndex < questions.size() - 1) {
            currentQuestionIndex++;
            displayCurrentQuestion();
            updateNavigationButtons();
        }
    }

    private void submitExam() {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Submit Exam");
        builder.setMessage(
                "Are you sure you want to submit this exam? You cannot change your answers after submission.");
        builder.setPositiveButton("Submit", (dialog, which) -> performExamSubmission());
        builder.setNegativeButton("Review", null);
        builder.show();
    }

    private void performExamSubmission() {
        isFinishingNormally = true;

        // Calculate score
        int correctAnswers = 0;
        int totalQuestions = questions.size();

        for (Question question : questions) {
            String userAnswer = userAnswers.get(question.getId());
            String correctAnswer = question.getCorrectAnswer();

            if (userAnswer != null && userAnswer.equalsIgnoreCase(correctAnswer)) {
                correctAnswers++;
            }
        }

        double percentage = totalQuestions > 0 ? (double) correctAnswers / totalQuestions * 100 : 0;

        // Save result to Firestore
        saveExamResult(correctAnswers, totalQuestions, percentage);
    }

    private void saveExamResult(int score, int maxScore, double percentage) {
        android.content.SharedPreferences sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        String studentId = sharedPreferences.getString("studentId", null);

        if (studentId == null) {
            Toast.makeText(this, "Student information not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("studentId", studentId);
        resultData.put("examId", exam.getId());
        resultData.put("subjectId", subject != null ? subject.getId() : null);
        resultData.put("score", score);
        resultData.put("maxScore", maxScore);
        resultData.put("percentage", percentage);
        resultData.put("submittedAt", com.google.firebase.Timestamp.now());

        // Update exam attempt status
        db.collection("examAttempts")
                .document(studentId + "_" + exam.getId())
                .update("status", "completed", "completedAt", com.google.firebase.Timestamp.now())
                .addOnSuccessListener(aVoid -> Log.d("TakeExamActivity", "Exam attempt updated to completed"))
                .addOnFailureListener(
                        e -> Log.e("TakeExamActivity", "Failed to update exam attempt: " + e.getMessage()));

        db.collection("exam_results")
                .add(resultData)
                .addOnSuccessListener(documentReference -> {
                    Log.d("TakeExamActivity", "Exam result saved with ID: " + documentReference.getId());

                    // Show result
                    String resultMessage = String.format("Exam Submitted!\nScore: %d/%d (%.1f%%)",
                            score, maxScore, percentage);
                    Toast.makeText(this, resultMessage, Toast.LENGTH_LONG).show();

                    // Return to exam details with completion flag
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("exam_completed", true);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e("TakeExamActivity", "Error saving exam result: " + e.getMessage());
                    Toast.makeText(this, "Error saving result. Please try again.", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    @SuppressLint({ "MissingSuperCall", "GestureBackNavigation" })
    public void onBackPressed() {
        // Prevent back navigation during exam to avoid cheating
        Toast.makeText(this, "You cannot go back during the exam. Please finish the exam to exit.", Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // If the activity is paused without finishing normally (e.g., user pressed home
        // or switched apps),
        // submit the exam to prevent cheating
        if (!isFinishingNormally && !isFinishing()) {
            Toast.makeText(this, "Exam submitted due to app exit.", Toast.LENGTH_SHORT).show();
            performExamSubmission();
        }
    }
}