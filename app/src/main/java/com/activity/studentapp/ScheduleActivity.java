package com.activity.studentapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.activity.studentapp.adapter.ScheduleAdapter;
import com.activity.studentapp.adapter.ScheduleRowAdapter;
import com.activity.studentapp.model.Schedule;
import com.activity.studentapp.model.ScheduleRow;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleActivity extends AppCompatActivity {
    private static final String TAG = "ScheduleActivity";

    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private String studentId;
    private String studentDocId;

    private RecyclerView scheduleRecyclerView;
    private ProgressBar scheduleProgressBar;
    private TextView tvNoSchedule;
    private ScheduleRowAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        studentId = sharedPreferences.getString("studentId", null);
        studentDocId = sharedPreferences.getString("studentDocId", null);

        // Initialize views
        scheduleRecyclerView = findViewById(R.id.scheduleRecyclerView);
        scheduleProgressBar = findViewById(R.id.scheduleProgressBar);
        tvNoSchedule = findViewById(R.id.tvNoSchedule);

        adapter = new ScheduleRowAdapter(new ArrayList<>());
        scheduleRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        scheduleRecyclerView.setAdapter(adapter);

        loadSchedule();
    }

    private void loadSchedule() {
        Log.d(TAG, "loadSchedule called");
        Log.d(TAG, "studentId: " + studentId);
        Log.d(TAG, "studentDocId: " + studentDocId);

        if (studentId == null || studentId.isEmpty()) {
            showNoSchedule("Student information not available");
            return;
        }

        scheduleProgressBar.setVisibility(View.VISIBLE);
        tvNoSchedule.setVisibility(View.GONE);

        // First get student data to get section and gradeLevel
        db.collection("students")
                .document(studentDocId != null ? studentDocId : studentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String section = documentSnapshot.getString("section");
                        String gradeLevel = documentSnapshot.getString("gradeLevel");
                        Log.d(TAG, "Student section: " + section + ", gradeLevel: " + gradeLevel);
                        if (gradeLevel != null && section != null) {
                            fetchSchedule(gradeLevel, section);
                        } else {
                            showNoSchedule("Grade level or section not found");
                        }
                    } else {
                        showNoSchedule("Student record not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting student data", e);
                    showNoSchedule("Error loading student information");
                });
    }

    private void fetchSchedule(String gradeLevel, String section) {
        String fullSectionName = gradeLevel + " - " + section;
        Log.d(TAG, "Fetching schedule for section: " + fullSectionName);

        db.collection("class_schedule")
                .whereEqualTo("sectionName", fullSectionName)
                .get()
                .addOnCompleteListener(task -> {
                    scheduleProgressBar.setVisibility(View.GONE);
                    if (task.isSuccessful() && task.getResult() != null) {
                        Log.d(TAG, "Schedule query successful, documents: " + task.getResult().size());
                        List<Schedule> schedules = new ArrayList<>();
                        int total = task.getResult().size();
                        int[] processed = {0};
                        if (total == 0) {
                            Log.d(TAG, "No schedule documents found");
                            showNoSchedule("No schedule found");
                            return;
                        }
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            String subjectId = doc.getString("subjectId");
                            String instructorName = doc.getString("instructorName");
                            String timeFrame = doc.getString("timeFrame");
                            Log.d(TAG, "Processing schedule doc, subjectId: " + subjectId + ", timeFrame: " + timeFrame);

                            // Get subject name from subjects collection
                            db.collection("subjects").document(subjectId).get()
                                    .addOnSuccessListener(subjectDoc -> {
                                        String subjectName = subjectDoc.getString("subjectName");
                                        Log.d(TAG, "Fetched subject: " + subjectName + ", timeFrame: " + timeFrame);
                                        if (subjectName != null) {
                                            schedules.add(new Schedule(subjectName, instructorName, timeFrame));
                                        }
                                        processed[0]++;
                                        if (processed[0] == total) {
                                            Log.d(TAG, "All subjects processed, displaying schedules");
                                            displaySchedules(schedules);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to fetch subject: " + subjectId, e);
                                        processed[0]++;
                                        if (processed[0] == total) {
                                            Log.d(TAG, "All subjects processed (with failures), displaying schedules");
                                            displaySchedules(schedules);
                                        }
                                    });
                        }
                    } else {
                        Log.e(TAG, "Error loading schedule", task.getException());
                        showNoSchedule("Error loading schedule");
                    }
                });
    }

    private void displaySchedules(List<Schedule> schedules) {
        Log.d(TAG, "Displaying schedules, count: " + schedules.size());
        if (schedules.isEmpty()) {
            Log.d(TAG, "No schedule to display");
            showNoSchedule("No schedule available");
            return;
        }

        // Sort schedules by start time
        List<Schedule> sorted = new ArrayList<>(schedules);
        sorted.sort((s1, s2) -> Integer.compare(parseTime(s1.getTimeFrame()), parseTime(s2.getTimeFrame())));

        List<ScheduleRow> rows = new ArrayList<>();

        // Header row
        ScheduleRow header = new ScheduleRow("Time");
        header.setSubject(0, "Mon");
        header.setSubject(1, "Tue");
        header.setSubject(2, "Wed");
        header.setSubject(3, "Thu");
        header.setSubject(4, "Fri");
        rows.add(header);

        // Map time to row
        Map<Integer, ScheduleRow> timeToRow = new HashMap<>();

        for (int i = 0; i < Math.min(sorted.size(), 5); i++) {
            Schedule schedule = sorted.get(i);
            int minutes = parseTime(schedule.getTimeFrame());
            String timeStr = schedule.getTimeFrame().split(" - ")[0];

            if (!timeToRow.containsKey(minutes)) {
                ScheduleRow row = new ScheduleRow(timeStr);
                timeToRow.put(minutes, row);
                rows.add(row);
            }

            ScheduleRow row = timeToRow.get(minutes);
            row.setSubject(i, schedule.getSubjectName());
        }

        adapter.updateRows(rows);
        scheduleRecyclerView.setVisibility(View.VISIBLE);
        tvNoSchedule.setVisibility(View.GONE);
    }

    private int parseTime(String timeFrame) {
        try {
            String start = timeFrame.split(" - ")[0];
            String[] parts = start.split(" ");
            String time = parts[0];
            String ampm = parts[1];
            String[] hm = time.split(":");
            int hour = Integer.parseInt(hm[0]);
            int min = Integer.parseInt(hm[1]);
            if (ampm.equals("PM") && hour != 12) hour += 12;
            if (ampm.equals("AM") && hour == 12) hour = 0;
            return hour * 60 + min;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing time: " + timeFrame, e);
            return 0;
        }
    }

    private void showNoSchedule(String message) {
        scheduleRecyclerView.setVisibility(View.GONE);
        tvNoSchedule.setText(message);
        tvNoSchedule.setVisibility(View.VISIBLE);
        scheduleProgressBar.setVisibility(View.GONE);
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