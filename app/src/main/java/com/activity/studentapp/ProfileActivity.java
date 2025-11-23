package com.activity.studentapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {
    private static final String TAG = "ProfileActivity";

    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private String studentId;
    private String studentDocId;

    private TextView tvStudentId, tvName, tvGradeLevel, tvSection, tvError;
    private ProgressBar profileProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        studentId = sharedPreferences.getString("studentId", null);
        studentDocId = sharedPreferences.getString("studentDocId", null);

        // Initialize views
        tvStudentId = findViewById(R.id.tvStudentId);
        tvName = findViewById(R.id.tvName);
        tvGradeLevel = findViewById(R.id.tvGradeLevel);
        tvSection = findViewById(R.id.tvSection);
        tvError = findViewById(R.id.tvError);
        profileProgressBar = findViewById(R.id.profileProgressBar);

        loadProfile();
    }

    private void loadProfile() {
        Log.d(TAG, "loadProfile called");
        Log.d(TAG, "studentId: " + studentId);
        Log.d(TAG, "studentDocId: " + studentDocId);

        if (studentId == null || studentId.isEmpty()) {
            showError("Student information not available");
            return;
        }

        profileProgressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);

        db.collection("students")
                .document(studentDocId != null ? studentDocId : studentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    profileProgressBar.setVisibility(View.GONE);
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Log.d(TAG, "Profile data: " + documentSnapshot.getData());

                        tvStudentId.setText(documentSnapshot.getString("studentId"));
                        tvName.setText(documentSnapshot.getString("name") != null ? documentSnapshot.getString("name") : "Not provided");
                        tvGradeLevel.setText(documentSnapshot.getString("gradeLevel"));
                        tvSection.setText(documentSnapshot.getString("section"));
                    } else {
                        showError("Profile not found");
                    }
                })
                .addOnFailureListener(e -> {
                    profileProgressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Error loading profile", e);
                    showError("Error loading profile");
                });
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        profileProgressBar.setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}