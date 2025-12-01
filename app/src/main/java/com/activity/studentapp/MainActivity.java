package com.activity.studentapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.activity.studentapp.adapter.SubjectAdapter;
import com.activity.studentapp.model.Subject;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private SharedPreferences sharedPreferences;
    private String studentId;
    private String studentDocId;

    private RecyclerView subjectsRecyclerView;
    private ProgressBar subjectsProgressBar;
    private TextView noSubjectsText;
    private TextView headerTitle;
    private ImageButton menuButton;
    private SubjectAdapter subjectAdapter;
    private Map<String, String> subjectIdToInstructor;
    private Map<String, String> subjectIdToSchedule;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MainActivity onCreate called");

        // Initialize Firebase instances
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        studentId = sharedPreferences.getString("studentId", null);
        studentDocId = sharedPreferences.getString("studentDocId", null);
        subjectIdToInstructor = new HashMap<>();
        subjectIdToSchedule = new HashMap<>();

        // Check if coming from login
        boolean fromLogin = getIntent().getBooleanExtra("fromLogin", false);

        if (fromLogin) {
            // User just logged in, show dashboard
            Log.d(TAG, "User logged in, showing dashboard");
            setContentView(R.layout.student_dashboard);

            // Initialize UI components
            initializeViews();
            setupRecyclerView();
            loadEnrolledSubjects();
            setupMenuButton();
        } else {
            // Launch the StudentLoginActivity
            Log.d(TAG, "Redirecting to login");
            startActivity(new Intent(this, StudentLoginActivity.class));
            finish();
        }
    }

    private void initializeViews() {
        subjectsRecyclerView = findViewById(R.id.subjectsRecyclerView);
        subjectsProgressBar = findViewById(R.id.subjectsProgressBar);
        noSubjectsText = findViewById(R.id.noSubjectsText);
        headerTitle = findViewById(R.id.headerTitle);
        menuButton = findViewById(R.id.menuButton);
    }

    private void setupRecyclerView() {
        subjectAdapter = new SubjectAdapter(new ArrayList<>());
        subjectsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        subjectsRecyclerView.setAdapter(subjectAdapter);
    }

    private void loadEnrolledSubjects() {
        Log.d(TAG, "loadEnrolledSubjects called");
        Log.d(TAG, "studentId: " + studentId);
        Log.d(TAG, "studentDocId: " + studentDocId);
        Log.d(TAG, "Firebase Auth current user: "
                + (mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "null"));

        if (studentId == null || studentId.isEmpty()) {
            Log.e(TAG, "Student ID is null or empty");
            showNoSubjectsView("Student information not available");
            return;
        }

        // Show loading state
        subjectsProgressBar.setVisibility(View.VISIBLE);
        noSubjectsText.setVisibility(View.GONE);
        subjectsRecyclerView.setVisibility(View.GONE);

        // Log offline persistence status
        Log.d(TAG, "Offline persistence enabled: " + db.getFirestoreSettings().isPersistenceEnabled());

        // Check network connectivity
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        Log.d(TAG, "Network connected: " + isConnected);

        // First, get the student's section using snapshot listener for offline support
        Log.d(TAG, "Attempting to fetch student document for ID: " + studentDocId);
        db.collection("students")
                .document(studentDocId != null ? studentDocId : studentId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    Log.d(TAG, "Student listener called");
                    subjectsProgressBar.setVisibility(View.GONE);
                    if (e != null) {
                        Log.e(TAG, "Error getting student data: ", e);
                        Log.d(TAG, "Exception type: " + e.getClass().getSimpleName());
                        Log.d(TAG, "Exception message: " + e.getMessage());
                        showNoSubjectsView("Error loading student information.");
                        return;
                    }
                    Log.d(TAG, "Document exists: " + (documentSnapshot != null && documentSnapshot.exists()));
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Log.d(TAG, "Student document fetched successfully. From cache: "
                                + documentSnapshot.getMetadata().isFromCache());
                        Log.d(TAG, "Student document data: " + documentSnapshot.getData());
                        String section = documentSnapshot.getString("section");
                        String gradeLevel = documentSnapshot.getString("gradeLevel");
                        Log.d(TAG, "Student section: " + section);
                        Log.d(TAG, "Student gradeLevel: " + gradeLevel);
                        if (gradeLevel != null && !gradeLevel.isEmpty()) {
                            // Fetch subjects by gradeLevel only
                            fetchSubjectsByGradeLevel(gradeLevel, section);
                        } else {
                            showNoSubjectsView("Grade level information not found. Please contact support.");
                        }
                    } else {
                        showNoSubjectsView("Student record not found.");
                    }
                });
    }

    private void fetchSubjectsByGradeLevel(String gradeLevel, String section) {
        Log.d(TAG, "Fetching subjects for gradeLevel: " + gradeLevel + ", section: " + section);
        // Show loading state
        subjectsProgressBar.setVisibility(View.VISIBLE);
        noSubjectsText.setVisibility(View.GONE);
        subjectsRecyclerView.setVisibility(View.GONE);

        // First, load schedules for the section
        String fullSectionName = gradeLevel + " - " + section;
        db.collection("class_schedule")
                .whereEqualTo("sectionName", fullSectionName)
                .get()
                .addOnCompleteListener(scheduleTask -> {
                    if (scheduleTask.isSuccessful() && scheduleTask.getResult() != null) {
                        subjectIdToInstructor.clear();
                        subjectIdToSchedule.clear();
                        for (QueryDocumentSnapshot doc : scheduleTask.getResult()) {
                            String subjectId = doc.getString("subjectId");
                            String instructorName = doc.getString("instructorName");
                            String timeFrame = doc.getString("timeFrame");
                            if (subjectId != null) {
                                subjectIdToInstructor.put(subjectId, instructorName);
                                subjectIdToSchedule.put(subjectId, timeFrame);
                            }
                        }
                        Log.d(TAG, "Loaded schedules for " + scheduleTask.getResult().size() + " subjects");
                    } else {
                        Log.e(TAG, "Error loading schedules: ", scheduleTask.getException());
                    }

                    // Now fetch subjects
                    db.collection("subjects")
                            .whereEqualTo("gradeLevel", gradeLevel)
                            .get()
                            .addOnCompleteListener(task -> {
                                subjectsProgressBar.setVisibility(View.GONE);
                                Log.d(TAG, "Subjects fetch task successful: " + task.isSuccessful());
                                if (task.getException() != null) {
                                    Log.e(TAG, "Subjects fetch exception: ", task.getException());
                                }

                                if (task.isSuccessful() && task.getResult() != null) {
                                    Log.d(TAG, "Subjects result size: " + task.getResult().size());
                                    List<Subject> subjects = new ArrayList<>();
                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                        try {
                                            Log.d(TAG, "Processing document: " + document.getId());
                                            Log.d(TAG, "Document data: " + document.getData());
                                            Subject subject = new Subject();
                                            subject.setId(document.getId());
                                            subject.setCode(document.getString("description"));
                                            subject.setName(document.getString("subjectName"));
                                            subject.setInstructor(subjectIdToInstructor.get(document.getId()));
                                            subject.setSchedule(subjectIdToSchedule.get(document.getId()));
                                            subject.setRoom(section);

                                            // Log the subject details for debugging
                                            Log.d(TAG, "Subject loaded: " + subject.getName() +
                                                    " (Code: " + subject.getCode() +
                                                    ", Instructor: " + subject.getInstructor() +
                                                    ", Room: " + subject.getRoom() + ")");

                                            subjects.add(subject);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error parsing subject: " + e.getMessage());
                                        }
                                    }

                                    if (!subjects.isEmpty()) {
                                        // Update the adapter with the new list of subjects
                                        subjectAdapter.updateSubjects(subjects);
                                        subjectsRecyclerView.setVisibility(View.VISIBLE);
                                        noSubjectsText.setVisibility(View.GONE);
                                        Log.d(TAG, "Displaying " + subjects.size() + " subjects");
                                    } else {
                                        Log.d(TAG, "No subjects found for grade level: " + gradeLevel);
                                        showNoSubjectsView("No subjects found for your grade level.");
                                    }
                                } else {
                                    Log.e(TAG, "Error getting subjects: ", task.getException());
                                    showNoSubjectsView("Error loading subjects. Please try again.");
                                }
                            });
                });
    }

    private void fetchSubjectsDetails(List<String> subjectIds, String section) {
        Log.d(TAG, "Fetching subject details for IDs: " + subjectIds + ", section: " + section);
        // Fetch subjects that match both the IDs and the section
        db.collection("subjects")
                .whereIn("id", subjectIds)
                .whereEqualTo("section", section)
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error getting subjects: ", e);
                        showNoSubjectsView("Error loading subject details.");
                        return;
                    }
                    if (querySnapshot != null) {
                        Log.d(TAG, "Subjects query successful. Documents count: " + querySnapshot.size());
                        List<Subject> subjects = new ArrayList<>();
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            Log.d(TAG, "Subject document: " + document.getId() + ", from cache: "
                                    + document.getMetadata().isFromCache());
                            Subject subject = document.toObject(Subject.class);
                            subject.setId(document.getId());
                            subjects.add(subject);
                            Log.d(TAG, "Subject: " + subject.getName());
                        }

                        if (!subjects.isEmpty()) {
                            // Display subjects
                            subjectAdapter.updateSubjects(subjects);
                            subjectsRecyclerView.setVisibility(View.VISIBLE);
                            noSubjectsText.setVisibility(View.GONE);
                        } else {
                            Log.d(TAG, "No subjects found after query");
                            showNoSubjectsView("No subjects found.");
                        }
                    }
                });
    }

    private void showNoSubjectsView(String message) {
        subjectsRecyclerView.setVisibility(View.GONE);
        noSubjectsText.setText(message);
        noSubjectsText.setVisibility(View.VISIBLE);
    }

    private void setupMenuButton() {
        menuButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(MainActivity.this, menuButton);
            popup.getMenuInflater().inflate(R.menu.burger_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_profile) {
                    startActivity(new Intent(MainActivity.this, ProfileActivity.class));
                    return true;
                } else if (id == R.id.menu_schedule) {
                    startActivity(new Intent(MainActivity.this, ScheduleActivity.class));
                    return true;
                } else if (id == R.id.menu_logout) {
                    // Sign out from Firebase
                    mAuth.signOut();
                    // Clear shared preferences
                    sharedPreferences.edit().clear().apply();
                    // Redirect to login screen
                    startActivity(new Intent(MainActivity.this, StudentLoginActivity.class));
                    finish();
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }
}