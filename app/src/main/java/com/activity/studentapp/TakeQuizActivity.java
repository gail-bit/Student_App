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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TakeQuizActivity extends AppCompatActivity {

    private Activity quiz;
    private Subject subject;
    private FirebaseFirestore db;
    private boolean isFinishingNormally = false;

    // Quiz data
    private List<Question> questions;
    private Map<String, String> userAnswers; // questionId -> selectedAnswer
    private int currentQuestionIndex = 0;

    // UI components
    private ImageButton backButton;
    private TextView quizTitleHeader;
    private MaterialButton btnPrevious;
    private MaterialButton btnNext;
    private TextView questionCounter;
    private LinearLayout quizContent;

    // Question display components
    private TextView questionNumber;
    private TextView questionText;
    private RadioGroup answerOptions;
    private TextInputLayout dropdownInputAnswer;
    private androidx.appcompat.widget.AppCompatAutoCompleteTextView dropdownAnswer;
    private ProgressBar quizProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_quiz);

        // Set fullscreen and prevent screenshots to avoid cheating
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize data structures
        questions = new ArrayList<>();
        userAnswers = new HashMap<>();

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

        // Load questions from database
        loadQuizQuestions();

        Toast.makeText(this, "Starting quiz: " + quiz.getTitle(), Toast.LENGTH_SHORT).show();
        Log.d("TakeQuizActivity", "Quiz started: " + quiz.getTitle());
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        quizTitleHeader = findViewById(R.id.quizTitleHeader);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        questionCounter = findViewById(R.id.questionCounter);
        quizContent = findViewById(R.id.quizContent);

        // Question display views
        questionNumber = findViewById(R.id.questionNumber);
        questionText = findViewById(R.id.questionText);
        answerOptions = findViewById(R.id.answerOptions);
        dropdownInputAnswer = findViewById(R.id.dropdownInputAnswer);
        dropdownAnswer = findViewById(R.id.dropdownAnswer);
        quizProgress = findViewById(R.id.quizProgress);
    }

    private void setupUI() {
        // Set quiz title in header
        quizTitleHeader.setText(quiz.getTitle() != null ? quiz.getTitle() : "Quiz");

        // Initially hide quiz content until questions are loaded
        if (quizContent != null) {
            quizContent.setVisibility(View.GONE);
        }
        if (quizProgress != null) {
            quizProgress.setVisibility(View.VISIBLE);
        }

        // Initially disable previous button
        btnPrevious.setEnabled(false);
        updateNavigationButtons();
    }

    private void loadQuizQuestions() {
        if (quiz.getId() == null) {
            Toast.makeText(this, "Quiz ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d("TakeQuizActivity", "Loading questions for quiz: " + quiz.getId());

        // Load questions from quiz's subcollection
        db.collection("quizzes")
                .document(quiz.getId())
                .collection("questions")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    questions.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            // Debug: Log all fields in the document
                            Log.d("TakeQuizActivity", "Document data: " + document.getData());

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

                            // Set points
                            question.setPoints(
                                    document.getLong("points") != null ? document.getLong("points").intValue() : 1);

                            questions.add(question);
                            Log.d("TakeQuizActivity", "Loaded question: " + question.getQuestionText());
                            Log.d("TakeQuizActivity", "Options count: "
                                    + (question.getOptions() != null ? question.getOptions().size() : 0));
                        } catch (Exception e) {
                            Log.e("TakeQuizActivity", "Error parsing question: " + e.getMessage());
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

                    Log.d("TakeQuizActivity", "Collected " + allIdAnswers.size()
                            + " answers for identification questions: " + allIdAnswers);

                    // Randomize question order to prevent cheating
                    Collections.shuffle(questions);

                    if (!questions.isEmpty()) {
                        // Record quiz attempt
                        recordQuizAttempt();

                        // Show first question
                        displayCurrentQuestion();
                        updateNavigationButtons();

                        // Show quiz content
                        if (quizContent != null) {
                            quizContent.setVisibility(View.VISIBLE);
                        }
                        if (quizProgress != null) {
                            quizProgress.setVisibility(View.GONE);
                        }

                        Log.d("TakeQuizActivity", "Total questions loaded: " + questions.size());
                    } else {
                        Toast.makeText(this, "No questions found for this quiz", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("TakeQuizActivity", "Error loading quiz questions: " + e.getMessage());
                    Toast.makeText(this, "Error loading quiz questions", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void recordQuizAttempt() {
        android.content.SharedPreferences sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        String studentId = sharedPreferences.getString("studentId", null);

        if (studentId != null && quiz != null) {
            Map<String, Object> attemptData = new HashMap<>();
            attemptData.put("studentId", studentId);
            attemptData.put("quizId", quiz.getId());
            attemptData.put("subjectId", subject != null ? subject.getId() : null);
            attemptData.put("startedAt", com.google.firebase.Timestamp.now());
            attemptData.put("status", "in_progress");

            db.collection("quizAttempts")
                    .document(studentId + "_" + quiz.getId())
                    .set(attemptData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("TakeQuizActivity", "Quiz attempt recorded for student: " + studentId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("TakeQuizActivity", "Failed to record quiz attempt: " + e.getMessage());
                    });
        }
    }

    private void displayCurrentQuestion() {
        if (questions.isEmpty() || currentQuestionIndex >= questions.size()) {
            return;
        }

        Question question = questions.get(currentQuestionIndex);

        // Update question counter and progress
        questionCounter.setText((currentQuestionIndex + 1) + " / " + questions.size());
        int progress = (int) (((currentQuestionIndex + 1) / (float) questions.size()) * 100);
        quizProgress.setProgress(progress);

        // Set question number (Question X of Y format)
        questionNumber.setText("Question " + (currentQuestionIndex + 1) + " of " + questions.size());

        // Set question text
        questionText
                .setText(question.getQuestionText() != null ? question.getQuestionText() : "Question not available");

        // Clear previous options
        if (answerOptions != null) {
            answerOptions.removeAllViews();
            answerOptions.setVisibility(View.GONE);
        }
        if (dropdownInputAnswer != null) {
            dropdownInputAnswer.setVisibility(View.GONE);
        }

        // Handle different question types
        if (question.isMultipleChoice()) {
            // Multiple choice: show radio buttons with options
            if (answerOptions != null) {
                answerOptions.setVisibility(View.VISIBLE);
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

                            answerOptions.addView(radioButton);
                            optionLetter++;
                        }
                    }
                }

                // Set listener for radio group
                answerOptions.setOnCheckedChangeListener((group, checkedId) -> {
                    RadioButton selectedRadioButton = findViewById(checkedId);
                    if (selectedRadioButton != null) {
                        String selectedOption = (String) selectedRadioButton.getTag();
                        userAnswers.put(question.getId(), selectedOption);
                        Log.d("TakeQuizActivity",
                                "Answer saved for question " + question.getId() + ": " + selectedOption);
                    }
                });
            }

        } else if (question.isTrueFalse()) {
            // True/False: show True and False radio buttons
            if (answerOptions != null) {
                answerOptions.setVisibility(View.VISIBLE);

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

                answerOptions.addView(trueButton);
                answerOptions.addView(falseButton);

                // Set listener for radio group
                answerOptions.setOnCheckedChangeListener((group, checkedId) -> {
                    RadioButton selectedRadioButton = findViewById(checkedId);
                    if (selectedRadioButton != null) {
                        String selectedOption = (String) selectedRadioButton.getTag();
                        userAnswers.put(question.getId(), selectedOption);
                        Log.d("TakeQuizActivity",
                                "Answer saved for question " + question.getId() + ": " + selectedOption);
                    }
                });
            }

        } else if (question.isShortAnswer()) {
            // Identification/Short Answer: show dropdown with possible answers
            if (dropdownInputAnswer != null && dropdownAnswer != null) {
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
                        Log.d("TakeQuizActivity",
                                "Answer saved for question " + question.getId() + ": " + selectedAnswer);
                    });

                    // Show dropdown when focused
                    dropdownAnswer.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) {
                            dropdownAnswer.showDropDown();
                        }
                    });

                    // Show dropdown when clicked
                    dropdownAnswer.setOnClickListener(v -> dropdownAnswer.showDropDown());
                } else {
                    // No possible answers available, hide dropdown
                    dropdownInputAnswer.setVisibility(View.GONE);
                    Log.w("TakeQuizActivity",
                            "No possible answers available for identification question: " + question.getId());
                }
            }
        } else {
            // Hide dropdown for other question types
            if (dropdownInputAnswer != null) {
                dropdownInputAnswer.setVisibility(View.GONE);
            }
        }
    }

    private void setupClickListeners() {
        // Back button - disabled during quiz to prevent cheating
        backButton.setOnClickListener(v -> {
            Toast.makeText(this, "You cannot go back during the quiz. Please finish the quiz to exit.",
                    Toast.LENGTH_SHORT).show();
        });

        // Previous button
        btnPrevious.setOnClickListener(v -> {
            saveCurrentAnswer();
            currentQuestionIndex--;
            displayCurrentQuestion();
            updateNavigationButtons();
        });

        // Next button
        btnNext.setOnClickListener(v -> {
            // Check if an answer is selected
            boolean answerSelected = (answerOptions != null && answerOptions.getCheckedRadioButtonId() != -1) ||
                    (dropdownAnswer != null && dropdownAnswer.getText() != null
                            && !dropdownAnswer.getText().toString().trim().isEmpty());

            if (!answerSelected) {
                Toast.makeText(this, "Please select an answer before proceeding", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save current answer
            saveCurrentAnswer();

            // Check if this is the last question
            if (currentQuestionIndex >= questions.size() - 1) {
                // Submit quiz
                submitQuiz();
            } else {
                // Go to next question
                currentQuestionIndex++;
                displayCurrentQuestion();
                updateNavigationButtons();
            }
        });
    }

    private void saveCurrentAnswer() {
        if (questions.isEmpty() || currentQuestionIndex >= questions.size()) {
            return;
        }

        Question currentQuestion = questions.get(currentQuestionIndex);
        String selectedAnswer = null;

        // Get selected answer based on question type
        if (currentQuestion.isMultipleChoice() || currentQuestion.isTrueFalse()) {
            if (answerOptions != null) {
                int checkedId = answerOptions.getCheckedRadioButtonId();
                if (checkedId != -1) {
                    RadioButton selectedRadioButton = findViewById(checkedId);
                    if (selectedRadioButton != null) {
                        selectedAnswer = (String) selectedRadioButton.getTag();
                    }
                }
            }
        } else if (currentQuestion.isShortAnswer()) {
            if (dropdownAnswer != null && dropdownAnswer.getText() != null) {
                selectedAnswer = dropdownAnswer.getText().toString().trim();
            }
        }

        if (selectedAnswer != null && !selectedAnswer.isEmpty()) {
            userAnswers.put(currentQuestion.getId(), selectedAnswer);
            Log.d("TakeQuizActivity", "Saved answer for question " + currentQuestion.getId() + ": " + selectedAnswer);
        }
    }

    private void updateNavigationButtons() {
        // Update previous button
        btnPrevious.setEnabled(currentQuestionIndex > 0);

        // Update next button text
        if (currentQuestionIndex >= questions.size() - 1) {
            btnNext.setText("Submit Quiz");
        } else {
            btnNext.setText("Next");
        }
    }

    private void submitQuiz() {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Submit Quiz");
        builder.setMessage(
                "Are you sure you want to submit this quiz? You cannot change your answers after submission.");
        builder.setPositiveButton("Submit", (dialog, which) -> performQuizSubmission());
        builder.setNegativeButton("Review", null);
        builder.show();
    }

    private void performQuizSubmission() {
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
        saveQuizResult(correctAnswers, totalQuestions, percentage);
    }

    private void saveQuizResult(int score, int maxScore, double percentage) {
        SharedPreferences sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        String studentId = sharedPreferences.getString("studentId", null);

        if (studentId == null) {
            Toast.makeText(this, "Student information not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("studentId", studentId);
        resultData.put("quizId", quiz.getId());
        resultData.put("subjectId", subject != null ? subject.getId() : null);
        resultData.put("score", score);
        resultData.put("maxScore", maxScore);
        resultData.put("percentage", percentage);
        resultData.put("submittedAt", com.google.firebase.Timestamp.now());

        // Update quiz attempt status
        db.collection("quizAttempts")
                .document(studentId + "_" + quiz.getId())
                .update("status", "completed", "completedAt", com.google.firebase.Timestamp.now())
                .addOnSuccessListener(aVoid -> Log.d("TakeQuizActivity", "Quiz attempt updated to completed"))
                .addOnFailureListener(
                        e -> Log.e("TakeQuizActivity", "Failed to update quiz attempt: " + e.getMessage()));

        db.collection("quiz_results")
                .add(resultData)
                .addOnSuccessListener(documentReference -> {
                    Log.d("TakeQuizActivity", "Quiz result saved with ID: " + documentReference.getId());

                    // Show result
                    String resultMessage = String.format("Quiz Submitted!\nScore: %d/%d (%.1f%%)",
                            score, maxScore, percentage);
                    Toast.makeText(this, resultMessage, Toast.LENGTH_LONG).show();

                    // Return to quiz details with completion flag
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("quiz_completed", true);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e("TakeQuizActivity", "Error saving quiz result: " + e.getMessage());
                    Toast.makeText(this, "Error saving result. Please try again.", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    @SuppressLint({ "MissingSuperCall", "GestureBackNavigation" })
    public void onBackPressed() {
        // Prevent back navigation during quiz to avoid cheating
        Toast.makeText(this, "You cannot go back during the quiz. Please finish the quiz to exit.", Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // If the activity is paused without finishing normally (e.g., user pressed home
        // or switched apps),
        // submit the quiz to prevent cheating
        if (!isFinishingNormally && !isFinishing()) {
            Toast.makeText(this, "Quiz submitted due to app exit.", Toast.LENGTH_SHORT).show();
            performQuizSubmission();
        }
    }
}