package com.activity.studentapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.activity.studentapp.adapter.ActivityAdapter;
import com.activity.studentapp.model.Subject;
import com.activity.studentapp.model.Activity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class SubjectDetailsActivity extends AppCompatActivity {
    private Subject subject;
    private FirebaseFirestore db;
    private ListenerRegistration subjectDetailsListener;
    private ListenerRegistration assignmentsListener;

    // UI components
    private TextView subjectName;
    private TextView subjectDescription;
    private TextView subjectSchedule;
    private TextView subjectRoom;
    private TextView instructorName;
    private TextView scheduleInfo;
    private ImageButton backButton;

    // Activities components
    private RecyclerView activitiesRecyclerView;
    private TextView noActivitiesText;
    private View activitiesProgressBar;
    private ActivityAdapter activityAdapter;
    private List<Activity> activitiesList;
    private ListenerRegistration activitiesListener;

    // Quiz components
    private RecyclerView quizzesRecyclerView;
    private TextView noQuizzesText;
    private View quizzesProgressBar;
    private ActivityAdapter quizAdapter;
    private List<Activity> quizzesList;
    private ListenerRegistration quizzesListener;

    // Exam components
    private RecyclerView examsRecyclerView;
    private TextView noExamsText;
    private View examsProgressBar;
    private ActivityAdapter examAdapter;
    private List<Activity> examsList;
    private ListenerRegistration examsListener;

    // Attendance listener
    private ListenerRegistration attendanceListener;
    private String studentId;
    private String studentDocId;
    private String section;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_details);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize lists
        activitiesList = new ArrayList<>();
        quizzesList = new ArrayList<>();
        examsList = new ArrayList<>();

        // Get subject data from intent
        subject = (Subject) getIntent().getSerializableExtra("subject");
        if (subject == null) {
            Toast.makeText(this, "Subject data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get student ID
        SharedPreferences sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        studentId = sharedPreferences.getString("studentId", null);
        studentDocId = sharedPreferences.getString("studentDocId", null);

        getStudentSection();
        initializeViews();
        setupActivitiesRecyclerView();
        setupQuizzesRecyclerView();
        setupExamsRecyclerView();
        setupUI();
        loadActivities();
        loadQuizzes();
        loadExams();
        setupAttendanceListener();
        setupClickListeners();
    }

    private void getStudentSection() {
        if (studentDocId != null) {
            db.collection("students").document(studentDocId).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    section = documentSnapshot.getString("section");
                    if (section != null) {
                        subject.setRoom(section);
                        // Update the UI with the room
                        updateSubjectDetails(subject.getName(), subject.getSchedule(), subject.getRoom(),
                                subject.getInstructor());
                    }
                }
            }).addOnFailureListener(
                    e -> Log.e("SubjectDetailsActivity", "Error getting student section: " + e.getMessage()));
        }
    }

    private void initializeViews() {
        subjectName = findViewById(R.id.subjectName);
        subjectDescription = findViewById(R.id.subjectDescription);
        subjectSchedule = findViewById(R.id.subjectSchedule);
        subjectRoom = findViewById(R.id.subjectRoom);
        instructorName = findViewById(R.id.instructorName);
        scheduleInfo = findViewById(R.id.scheduleInfo);
        backButton = findViewById(R.id.backButton);

        // Activities views
        activitiesRecyclerView = findViewById(R.id.activitiesRecyclerView);
        noActivitiesText = findViewById(R.id.noActivitiesText);
        activitiesProgressBar = findViewById(R.id.activitiesProgressBar);

        // Quiz views
        quizzesRecyclerView = findViewById(R.id.quizzesRecyclerView);
        noQuizzesText = findViewById(R.id.noQuizzesText);
        quizzesProgressBar = findViewById(R.id.quizzesProgressBar);

        // Exam views
        examsRecyclerView = findViewById(R.id.examsRecyclerView);
        noExamsText = findViewById(R.id.noExamsText);
        examsProgressBar = findViewById(R.id.examsProgressBar);

    }

    private void setupUI() {
        // Load subject details from database with real-time updates
        loadSubjectDetails(subject.getId());
    }

    private void loadSubjectDetails(String subjectId) {
        if (subjectId == null || subjectId.trim().isEmpty()) {
            // Fallback to intent data if no subjectId
            updateSubjectDetails(subject.getName(), subject.getSchedule(), subject.getRoom(), subject.getInstructor());
            return;
        }

        // Remove existing listener if any
        if (subjectDetailsListener != null) {
            subjectDetailsListener.remove();
        }

        subjectDetailsListener = db.collection("subjects")
                .document(subjectId)
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot documentSnapshot, FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.e("SubjectDetailsActivity", "Error listening to subject details: " + e.getMessage(), e);
                            // Fallback to intent data on error
                            updateSubjectDetails(subject.getName(), subject.getSchedule(), subject.getRoom(),
                                    subject.getInstructor());
                            return;
                        }

                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            // Update subject details from database
                            String name = documentSnapshot.getString("subjectName");
                            String schedule = documentSnapshot.getString("schedule");
                            String room = documentSnapshot.getString("room");
                            String description = documentSnapshot.getString("description");
                            String instructor = documentSnapshot.getString("instructor");

                            // Update the subject object with latest data
                            subject.setName(name);
                            subject.setSchedule(schedule);

                            // FIX: Only update room if it's not null in Firestore
                            if (room != null) {
                                subject.setRoom(room);
                            }

                            // Only update instructor if it's not null (don't overwrite existing instructor)
                            if (instructor != null) {
                                subject.setInstructor(instructor);
                            }

                            // FIX: Use subject.getRoom() instead of the local room variable
                            updateSubjectDetails(name, schedule, subject.getRoom(), subject.getInstructor());
                            updateDescriptionView(description, name);

                            // Load instructor and schedule from class_schedule collection
                            loadInstructorAndSchedule(subjectId);
                        } else {
                            // Fallback to intent data if document doesn't exist
                            updateSubjectDetails(subject.getName(), subject.getSchedule(), subject.getRoom(),
                                    subject.getInstructor());
                        }
                    }
                });
    }

    private void loadInstructorAndSchedule(String subjectId) {
        // Load instructor and schedule from class_schedule collection
        db.collection("class_schedule")
                .whereEqualTo("subjectId", subjectId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Get the first schedule document
                        DocumentSnapshot scheduleDoc = queryDocumentSnapshots.getDocuments().get(0);
                        String instructor = scheduleDoc.getString("instructorName");
                        String timeFrame = scheduleDoc.getString("timeFrame");
                        if (instructor != null) {
                            subject.setInstructor(instructor);
                        }
                        if (timeFrame != null) {
                            subject.setSchedule(timeFrame);
                        }
                        // Update the UI with instructor and schedule
                        updateSubjectDetails(subject.getName(), subject.getSchedule(), subject.getRoom(),
                                subject.getInstructor());
                        // Reload quizzes and exams to update instructor names
                        loadQuizzes();
                        loadExams();
                        Log.d("SubjectDetailsActivity",
                                "Loaded instructor: " + instructor + ", schedule: " + timeFrame + " for subject: "
                                        + subjectId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("SubjectDetailsActivity", "Error loading instructor and schedule: " + e.getMessage(), e);
                });
    }

    private void updateSubjectDetails(String name, String schedule, String room, String instructor) {
        // Update subject name
        if (subjectName != null) {
            subjectName.setText(name != null ? name : "No Name");
        }

        // Update subject schedule
        if (subjectSchedule != null) {
            subjectSchedule.setText(schedule != null ? schedule : "TBA");
        }

        // Update subject room
        if (subjectRoom != null) {
            subjectRoom.setText(room != null ? room : "TBA");
        }

        // Update instructor information
        if (instructorName != null) {
            instructorName.setText(instructor != null ? instructor : "TBA");
        }
        if (scheduleInfo != null) {
            scheduleInfo.setText("Schedule: " + (schedule != null ? schedule : "TBA"));
        }
    }

    private void updateDescriptionView(String description, String subjectName) {
        if (subjectDescription != null) {
            String desc = description;
            if (desc == null || desc.trim().isEmpty()) {
                desc = getSubjectDescription(subjectName);
            }
            subjectDescription.setText(desc);
        }
    }

    private String getSubjectDescription(String subjectName) {
        if (subjectName == null) {
            return "This subject covers fundamental concepts and practical applications. " +
                    "Students will learn through theoretical lectures and hands-on activities.";
        }

        // Provide specific descriptions based on subject names from the database
        switch (subjectName.toLowerCase()) {
            case "filipino":
                return "Filipino subject focuses on the development of communication skills in the Filipino language, "
                        +
                        "including reading, writing, speaking, and listening. Students will explore Filipino literature, "
                        +
                        "grammar, and cultural contexts to enhance their linguistic proficiency.";

            case "mapehhh":
            case "mapeh":
                return "MAPEH (Music, Arts, Physical Education, and Health) integrates various disciplines to promote "
                        +
                        "holistic development. Students will engage in creative expression, physical activities, and " +
                        "health education to develop well-rounded skills and healthy lifestyles.";

            case "englessh":
            case "english":
                return "English subject develops proficiency in the English language through comprehensive instruction "
                        +
                        "in grammar, vocabulary, literature, and communication skills. Students will improve their " +
                        "reading, writing, speaking, and listening abilities for academic and professional success.";

            default:
                return "This subject covers fundamental concepts and practical applications in " + subjectName + ". " +
                        "Students will learn through theoretical lectures, hands-on activities, and interactive sessions "
                        +
                        "to develop essential knowledge and skills in this area.";
        }
    }

    private void setupActivitiesRecyclerView() {
        activityAdapter = new ActivityAdapter(activitiesList, this::onActivityClick);
        activitiesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        activitiesRecyclerView.setAdapter(activityAdapter);
    }

    private void setupQuizzesRecyclerView() {
        quizAdapter = new ActivityAdapter(quizzesList, this::onQuizClick);
        quizzesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        quizzesRecyclerView.setAdapter(quizAdapter);
    }

    private void setupExamsRecyclerView() {
        examAdapter = new ActivityAdapter(examsList, this::onExamClick);
        examsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        examsRecyclerView.setAdapter(examAdapter);
    }

    private void loadActivities() {
        if (subject.getId() == null || subject.getId().trim().isEmpty()) {
            updateActivitiesEmptyView();
            return;
        }

        // Remove existing listener if any
        if (activitiesListener != null) {
            activitiesListener.remove();
        }

        // Show loading state
        if (activitiesProgressBar != null) {
            activitiesProgressBar.setVisibility(View.VISIBLE);
        }
        if (noActivitiesText != null) {
            noActivitiesText.setVisibility(View.GONE);
        }
        if (activitiesRecyclerView != null) {
            activitiesRecyclerView.setVisibility(View.GONE);
        }

        // Load activities from Firestore with real-time updates
        activitiesListener = db.collection("activities")
                .whereEqualTo("subjectId", subject.getId())
                .addSnapshotListener((querySnapshot, e) -> {
                    if (activitiesProgressBar != null) {
                        activitiesProgressBar.setVisibility(View.GONE);
                    }

                    if (e != null) {
                        Log.e("SubjectDetailsActivity", "Error listening to activities: " + e.getMessage(), e);
                        Toast.makeText(this, "Error loading activities", Toast.LENGTH_SHORT).show();
                        updateActivitiesEmptyView();
                        return;
                    }

                    if (querySnapshot != null) {
                        // Remove existing assignments (assignments don't have prefixes anymore)
                        activitiesList.removeIf(
                                activity -> activity.getId() != null && !activity.getId().startsWith("activity_"));
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            try {
                                Activity activity = document.toObject(Activity.class);
                                activity.setId(document.getId());
                                activitiesList.add(activity);
                                Log.d("SubjectDetailsActivity", "Loaded activity: " + activity.getTitle());
                            } catch (Exception ex) {
                                Log.e("SubjectDetailsActivity", "Error parsing activity: " + ex.getMessage());
                            }
                        }

                        // Sort activities by creation date (newest first)
                        activitiesList.sort((a1, a2) -> Long.compare(a2.getCreatedAt(), a1.getCreatedAt()));

                        if (activityAdapter != null) {
                            activityAdapter.updateActivities(activitiesList);
                            Log.d("SubjectDetailsActivity",
                                    "Updated activities list with " + activitiesList.size() + " items");
                        }
                    }

                    updateActivitiesEmptyView();
                });

        // Load assignments
        assignmentsListener = db.collection("assignments")
                .whereEqualTo("subjectId", subject.getId())
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        Log.e("SubjectDetailsActivity", "Error listening to assignments: " + e.getMessage(), e);
                        Toast.makeText(this, "Error loading assignments", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (querySnapshot != null) {
                        // Clear existing assignments and add new ones (assignments come from
                        // assignments collection)
                        activitiesList.removeIf(activity -> {
                            // Remove activities that don't start with "activity_" (these are assignments)
                            return activity.getId() != null && !activity.getId().startsWith("activity_");
                        });
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            try {
                                Activity assignment = new Activity();
                                assignment.setId(document.getId()); // Use actual document ID without prefix
                                assignment.setTitle(document.getString("title"));
                                assignment.setDescription(document.getString("description"));
                                assignment.setCreatedAt(
                                        document.getLong("createdAt") != null ? document.getLong("createdAt")
                                                : System.currentTimeMillis());

                                Long publishedAt = document.getLong("publishedAt");
                                if (publishedAt != null) {
                                    assignment.setPublishedAt(publishedAt);
                                } else {
                                    assignment.setPublishedAt(assignment.getCreatedAt());
                                }

                                assignment.setSubjectId(subject.getId());
                                assignment.setInstructorName(subject.getInstructor());
                                assignment.setStatus("Active");

                                // Map totalPoints from instructor app
                                Long totalPoints = document.getLong("totalPoints");
                                if (totalPoints != null) {
                                    assignment.setTotalPoints(totalPoints.intValue());
                                } else {
                                    assignment.setTotalPoints(0);
                                }

                                // Map published status from instructor app
                                Boolean published = document.getBoolean("published");
                                if (published != null) {
                                    assignment.setPublished(published);
                                } else {
                                    assignment.setPublished(true); // Default to published if not set
                                }

                                Object dueDateObj = document.get("dueDate");
                                if (dueDateObj instanceof com.google.firebase.Timestamp) {
                                    assignment.setDueDate(
                                            ((com.google.firebase.Timestamp) dueDateObj).toDate().getTime());
                                } else if (dueDateObj instanceof Long) {
                                    assignment.setDueDate((Long) dueDateObj);
                                } else {
                                    assignment.setDueDate(0L);
                                }

                                activitiesList.add(assignment);
                                Log.d("SubjectDetailsActivity",
                                        "Loaded assignment into activities: " + assignment.getTitle());
                            } catch (Exception ex) {
                                Log.e("SubjectDetailsActivity", "Error parsing assignment: " + ex.getMessage());
                            }
                        }

                        activitiesList.sort((a1, a2) -> Long.compare(a2.getPublishedAt(), a1.getPublishedAt()));
                        if (activityAdapter != null) {
                            activityAdapter.updateActivities(activitiesList);
                        }

                        updateActivitiesEmptyView();
                    }
                });
    }

    private void updateActivitiesEmptyView() {
        boolean empty = activitiesList.isEmpty();
        if (noActivitiesText != null) {
            noActivitiesText.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (activitiesRecyclerView != null) {
            activitiesRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private void onActivityClick(Activity activity) {
        // Check if it's an assignment by looking at the activity type or other
        // properties
        // For now, assume all activities from assignments collection are assignments
        Intent intent = new Intent(this, AssignmentDetailsActivity.class);
        intent.putExtra("assignment", activity);
        intent.putExtra("subject", subject);
        startActivity(intent);
        Log.d("SubjectDetailsActivity", "Opening assignment details: " + activity.getTitle());
    }

    private void checkAttendanceForSubject(String studentId, String subjectId, AttendanceCheckCallback callback) {
        // Get current date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = sdf.format(new Date());

        // Query attendance collection for the student in this subject on current date
        db.collection("attendance")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("subjectId", subjectId)
                .whereEqualTo("date", currentDate)
                .whereEqualTo("isPresent", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    boolean hasAttendance = !querySnapshot.isEmpty();
                    Log.d("SubjectDetailsActivity", "Attendance check for subject " + subjectId +
                            " on date " + currentDate + " - Present: " + hasAttendance);
                    callback.onAttendanceChecked(hasAttendance);
                })
                .addOnFailureListener(e -> {
                    Log.e("SubjectDetailsActivity", "Error checking attendance: " + e.getMessage());
                    // On error, assume no attendance for security
                    callback.onAttendanceChecked(false);
                });
    }

    private interface AttendanceCheckCallback {
        void onAttendanceChecked(boolean hasAttendance);
    }

    private void loadQuizzes() {
        if (studentId == null || subject.getId() == null) {
            updateQuizzesEmptyView();
            return;
        }

        // Remove existing listener if any
        if (quizzesListener != null) {
            quizzesListener.remove();
        }

        // Show loading state
        if (quizzesProgressBar != null) {
            quizzesProgressBar.setVisibility(View.VISIBLE);
        }
        if (noQuizzesText != null) {
            noQuizzesText.setVisibility(View.GONE);
        }
        if (quizzesRecyclerView != null) {
            quizzesRecyclerView.setVisibility(View.GONE);
        }

        // Load quizzes from Firestore with real-time updates
        // Only show quizzes that are published and student was present for the subject
        quizzesListener = db.collection("quizzes")
                .whereEqualTo("subjectId", subject.getId())
                .whereEqualTo("isPublished", true)
                .addSnapshotListener((querySnapshot, e) -> {
                    if (quizzesProgressBar != null) {
                        quizzesProgressBar.setVisibility(View.GONE);
                    }

                    if (e != null) {
                        Log.e("SubjectDetailsActivity", "Error listening to quizzes: " + e.getMessage(), e);
                        Toast.makeText(this, "Error loading quizzes", Toast.LENGTH_SHORT).show();
                        updateQuizzesEmptyView();
                        return;
                    }

                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        List<Activity> tempQuizzesList = new ArrayList<>();
                        AtomicInteger pendingChecks = new AtomicInteger(querySnapshot.size());

                        for (QueryDocumentSnapshot document : querySnapshot) {
                            try {
                                // Convert quiz to Activity-like object for display
                                Activity quiz = new Activity();
                                quiz.setId(document.getId());
                                quiz.setTitle(document.getString("title"));
                                quiz.setDescription(document.getString("description"));
                                quiz.setCreatedAt(document.getLong("createdAt") != null ? document.getLong("createdAt")
                                        : System.currentTimeMillis());

                                // Set publishedAt from document if available
                                Long publishedAt = document.getLong("publishedAt");
                                if (publishedAt != null) {
                                    quiz.setPublishedAt(publishedAt);
                                } else {
                                    quiz.setPublishedAt(quiz.getCreatedAt()); // fallback
                                }

                                // Set additional fields for proper display
                                quiz.setSubjectId(subject.getId());
                                quiz.setInstructorName(subject.getInstructor());
                                quiz.setStatus("Active");

                                // Try to set due date from scheduledAt or dueDate field
                                Object dueDateObj = document.get("dueDate");
                                if (dueDateObj == null) {
                                    dueDateObj = document.get("scheduledAt");
                                }
                                if (dueDateObj instanceof com.google.firebase.Timestamp) {
                                    quiz.setDueDate(((com.google.firebase.Timestamp) dueDateObj).toDate().getTime());
                                } else if (dueDateObj instanceof Long) {
                                    quiz.setDueDate((Long) dueDateObj);
                                } else if (dueDateObj instanceof String) {
                                    try {
                                        quiz.setDueDate(Long.parseLong((String) dueDateObj));
                                    } catch (NumberFormatException nfe) {
                                        quiz.setDueDate(0L);
                                    }
                                } else {
                                    quiz.setDueDate(0L);
                                }

                                // Check attendance for the subject
                                checkAttendanceForSubject(studentId, subject.getId(), hasAttendance -> {
                                    if (hasAttendance) {
                                        tempQuizzesList.add(quiz);
                                        Log.d("SubjectDetailsActivity",
                                                "Added quiz with attendance: " + quiz.getTitle());
                                    } else {
                                        Log.d("SubjectDetailsActivity",
                                                "Skipped quiz (no attendance): " + quiz.getTitle());
                                    }

                                    // Check if all attendance checks are complete
                                    if (pendingChecks.decrementAndGet() == 0) {
                                        // All checks complete, update the UI
                                        runOnUiThread(() -> {
                                            quizzesList.clear();
                                            quizzesList.addAll(tempQuizzesList);

                                            // Sort quizzes by published date (newest first)
                                            quizzesList.sort(
                                                    (a1, a2) -> Long.compare(a2.getPublishedAt(), a1.getPublishedAt()));

                                            if (quizAdapter != null) {
                                                quizAdapter.updateActivities(quizzesList);
                                                Log.d("SubjectDetailsActivity",
                                                        "Updated quizzes list with " + quizzesList.size()
                                                                + " items (after attendance filter)");
                                            }

                                            updateQuizzesEmptyView();
                                        });
                                    }
                                });

                            } catch (Exception ex) {
                                Log.e("SubjectDetailsActivity", "Error parsing quiz: " + ex.getMessage());
                                // Still decrement counter even on error
                                if (pendingChecks.decrementAndGet() == 0) {
                                    runOnUiThread(() -> {
                                        quizzesList.clear();
                                        quizzesList.addAll(tempQuizzesList);
                                        quizzesList.sort(
                                                (a1, a2) -> Long.compare(a2.getPublishedAt(), a1.getPublishedAt()));

                                        if (quizAdapter != null) {
                                            quizAdapter.updateActivities(quizzesList);
                                            Log.d("SubjectDetailsActivity",
                                                    "Updated quizzes list with " + quizzesList.size()
                                                            + " items (after attendance filter)");
                                        }

                                        updateQuizzesEmptyView();
                                    });
                                }
                            }
                        }
                    } else {
                        // No quizzes found
                        quizzesList.clear();
                        if (quizAdapter != null) {
                            quizAdapter.updateActivities(quizzesList);
                        }
                        updateQuizzesEmptyView();
                    }
                });
    }

    private void loadExams() {
        if (studentId == null || subject.getId() == null) {
            updateExamsEmptyView();
            return;
        }

        // Remove existing listener if any
        if (examsListener != null) {
            examsListener.remove();
        }

        // Show loading state
        if (examsProgressBar != null) {
            examsProgressBar.setVisibility(View.VISIBLE);
        }
        if (noExamsText != null) {
            noExamsText.setVisibility(View.GONE);
        }
        if (examsRecyclerView != null) {
            examsRecyclerView.setVisibility(View.GONE);
        }

        // Load exams from Firestore with real-time updates
        // Only show exams that are published and student was present for the subject
        examsListener = db.collection("exams")
                .whereEqualTo("subjectId", subject.getId())
                .whereEqualTo("isPublished", true)
                .addSnapshotListener((querySnapshot, e) -> {
                    if (examsProgressBar != null) {
                        examsProgressBar.setVisibility(View.GONE);
                    }

                    if (e != null) {
                        Log.e("SubjectDetailsActivity", "Error listening to exams: " + e.getMessage(), e);
                        Toast.makeText(this, "Error loading exams", Toast.LENGTH_SHORT).show();
                        updateExamsEmptyView();
                        return;
                    }

                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        List<Activity> tempExamsList = new ArrayList<>();
                        AtomicInteger pendingChecks = new AtomicInteger(querySnapshot.size());

                        for (QueryDocumentSnapshot document : querySnapshot) {
                            try {
                                // Convert exam to Activity-like object for display
                                Activity exam = new Activity();
                                exam.setId(document.getId());
                                exam.setTitle(document.getString("title"));
                                exam.setDescription(document.getString("description"));
                                exam.setCreatedAt(document.getLong("createdAt") != null ? document.getLong("createdAt")
                                        : System.currentTimeMillis());

                                // Set publishedAt from document if available
                                Long publishedAt = document.getLong("publishedAt");
                                if (publishedAt != null) {
                                    exam.setPublishedAt(publishedAt);
                                } else {
                                    exam.setPublishedAt(exam.getCreatedAt()); // fallback
                                }

                                // Set additional fields for proper display
                                exam.setSubjectId(subject.getId());
                                exam.setInstructorName(subject.getInstructor());
                                exam.setStatus("Active");

                                // Try to set due date from scheduledAt or dueDate field
                                Object dueDateObj = document.get("dueDate");
                                if (dueDateObj == null) {
                                    dueDateObj = document.get("scheduledAt");
                                }
                                if (dueDateObj instanceof com.google.firebase.Timestamp) {
                                    exam.setDueDate(((com.google.firebase.Timestamp) dueDateObj).toDate().getTime());
                                } else if (dueDateObj instanceof Long) {
                                    exam.setDueDate((Long) dueDateObj);
                                } else if (dueDateObj instanceof String) {
                                    try {
                                        exam.setDueDate(Long.parseLong((String) dueDateObj));
                                    } catch (NumberFormatException nfe) {
                                        exam.setDueDate(0L);
                                    }
                                } else {
                                    exam.setDueDate(0L);
                                }

                                // Check attendance for the subject
                                checkAttendanceForSubject(studentId, subject.getId(), hasAttendance -> {
                                    if (hasAttendance) {
                                        tempExamsList.add(exam);
                                        Log.d("SubjectDetailsActivity",
                                                "Added exam with attendance: " + exam.getTitle());
                                    } else {
                                        Log.d("SubjectDetailsActivity",
                                                "Skipped exam (no attendance): " + exam.getTitle());
                                    }

                                    // Check if all attendance checks are complete
                                    if (pendingChecks.decrementAndGet() == 0) {
                                        // All checks complete, update the UI
                                        runOnUiThread(() -> {
                                            examsList.clear();
                                            examsList.addAll(tempExamsList);

                                            // Sort exams by published date (newest first)
                                            examsList.sort(
                                                    (a1, a2) -> Long.compare(a2.getPublishedAt(), a1.getPublishedAt()));

                                            if (examAdapter != null) {
                                                examAdapter.updateActivities(examsList);
                                                Log.d("SubjectDetailsActivity",
                                                        "Updated exams list with " + examsList.size()
                                                                + " items (after attendance filter)");
                                            }

                                            updateExamsEmptyView();
                                        });
                                    }
                                });

                            } catch (Exception ex) {
                                Log.e("SubjectDetailsActivity", "Error parsing exam: " + ex.getMessage());
                                // Still decrement counter even on error
                                if (pendingChecks.decrementAndGet() == 0) {
                                    runOnUiThread(() -> {
                                        examsList.clear();
                                        examsList.addAll(tempExamsList);
                                        examsList.sort(
                                                (a1, a2) -> Long.compare(a2.getPublishedAt(), a1.getPublishedAt()));

                                        if (examAdapter != null) {
                                            examAdapter.updateActivities(examsList);
                                            Log.d("SubjectDetailsActivity",
                                                    "Updated exams list with " + examsList.size()
                                                            + " items (after attendance filter)");
                                        }

                                        updateExamsEmptyView();
                                    });
                                }
                            }
                        }
                    } else {
                        // No exams found
                        examsList.clear();
                        if (examAdapter != null) {
                            examAdapter.updateActivities(examsList);
                        }
                        updateExamsEmptyView();
                    }
                });
    }

    private void updateQuizzesEmptyView() {
        boolean empty = quizzesList.isEmpty();
        if (noQuizzesText != null) {
            noQuizzesText.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (quizzesRecyclerView != null) {
            quizzesRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private void updateExamsEmptyView() {
        boolean empty = examsList.isEmpty();
        if (noExamsText != null) {
            noExamsText.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (examsRecyclerView != null) {
            examsRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private void onQuizClick(Activity quiz) {
        // Navigate to quiz details activity
        Intent intent = new Intent(this, QuizDetailsActivity.class);
        intent.putExtra("quiz", quiz);
        intent.putExtra("subject", subject);
        startActivity(intent);

        Log.d("SubjectDetailsActivity", "Opening quiz details: " + quiz.getTitle());
    }

    private void onExamClick(Activity exam) {
        // Navigate to exam details activity
        Intent intent = new Intent(this, ExamDetailsActivity.class);
        intent.putExtra("exam", exam);
        intent.putExtra("subject", subject);
        startActivity(intent);

        Log.d("SubjectDetailsActivity", "Opening exam details: " + exam.getTitle());
    }

    private void setupAttendanceListener() {
        if (studentId == null || subject.getId() == null) {
            return;
        }

        // Remove existing listener if any
        if (attendanceListener != null) {
            attendanceListener.remove();
        }

        // Listen for attendance changes for this student and subject
        attendanceListener = db.collection("attendance")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("subjectId", subject.getId())
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        Log.e("SubjectDetailsActivity", "Error listening to attendance: " + e.getMessage());
                        return;
                    }

                    Log.d("SubjectDetailsActivity", "Attendance changed, reloading quizzes and exams");
                    // Reload quizzes and exams when attendance changes
                    loadQuizzes();
                    loadExams();
                });
    }

    private void setupClickListeners() {
        // Back button
        backButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove real-time listeners to prevent memory leaks
        if (subjectDetailsListener != null) {
            subjectDetailsListener.remove();
        }
        if (activitiesListener != null) {
            activitiesListener.remove();
        }
        if (quizzesListener != null) {
            quizzesListener.remove();
        }
        if (examsListener != null) {
            examsListener.remove();
        }
        if (assignmentsListener != null) {
            assignmentsListener.remove();
        }
        if (attendanceListener != null) {
            attendanceListener.remove();
        }
    }
}