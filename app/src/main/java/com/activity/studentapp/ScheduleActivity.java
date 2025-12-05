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
                .addSnapshotListener((querySnapshot, e) -> {
                    scheduleProgressBar.setVisibility(View.GONE);
                    if (e != null) {
                        Log.e(TAG, "Error listening to schedule", e);
                        showNoSchedule("Error loading schedule");
                        return;
                    }
                    if (querySnapshot != null) {
                        Log.d(TAG, "Schedule snapshot received, documents: " + querySnapshot.size());
                        List<Schedule> schedules = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            try {
                                Schedule schedule = doc.toObject(Schedule.class);
                                Log.d(TAG, "Fetched schedule: " + schedule.getSubjectName() + " on " + schedule.getDay()
                                        + " at " + schedule.getTimeFrame());
                                schedules.add(schedule);
                            } catch (Exception ex) {
                                Log.e(TAG, "Error parsing schedule document", ex);
                            }
                        }
                        Log.d(TAG, "Total schedules fetched: " + schedules.size());
                        displaySchedules(schedules);
                    } else {
                        showNoSchedule("No schedule data available");
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

        // Group schedules by time frame
        Map<String, List<Schedule>> timeToSchedules = new HashMap<>();
        for (Schedule schedule : sorted) {
            String timeFrame = schedule.getTimeFrame();
            if (!timeToSchedules.containsKey(timeFrame)) {
                timeToSchedules.put(timeFrame, new ArrayList<>());
            }
            timeToSchedules.get(timeFrame).add(schedule);
        }

        // Create rows for each time frame
        for (String timeFrame : timeToSchedules.keySet()) {
            String timeStr = timeFrame.split(" - ")[0];
            ScheduleRow row = new ScheduleRow(timeStr);

            List<Schedule> timeSchedules = timeToSchedules.get(timeFrame);
            for (Schedule schedule : timeSchedules) {
                String day = schedule.getDay();
                int[] dayIndices = getDayIndices(day);

                for (int dayIndex : dayIndices) {
                    if (dayIndex >= 0 && dayIndex < 5) {
                        String subjectInfo = schedule.getSubjectName();
                        if (schedule.getInstructorName() != null && !schedule.getInstructorName().isEmpty()) {
                            subjectInfo += "\n" + schedule.getInstructorName();
                        }
                        row.setSubject(dayIndex, subjectInfo);
                    }
                }
            }

            rows.add(row);
        }

        // Sort rows by time
        rows.sort((r1, r2) -> Integer.compare(parseTime(r1.getTime()), parseTime(r2.getTime())));

        adapter.updateRows(rows);
        scheduleRecyclerView.setVisibility(View.VISIBLE);
        tvNoSchedule.setVisibility(View.GONE);
    }

    private int[] getDayIndices(String day) {
        if (day == null) return new int[]{0, 1, 2, 3, 4}; // Default to all days

        switch (day.toLowerCase()) {
            case "monday":
            case "mon":
                return new int[]{0};
            case "tuesday":
            case "tue":
                return new int[]{1};
            case "wednesday":
            case "wed":
                return new int[]{2};
            case "thursday":
            case "thu":
                return new int[]{3};
            case "friday":
            case "fri":
                return new int[]{4};
            case "m-f":
            default:
                return new int[]{0, 1, 2, 3, 4}; // All weekdays
        }
    }

    private int parseTime(String timeStr) {
        try {
            // timeStr is like "8:00 AM"
            String[] parts = timeStr.trim().split(" ");
            if (parts.length < 2) {
                // Try parsing as just time without AM/PM
                String[] hm = timeStr.split(":");
                int hour = Integer.parseInt(hm[0]);
                int min = hm.length > 1 ? Integer.parseInt(hm[1]) : 0;
                return hour * 60 + min;
            }

            String time = parts[0];
            String ampm = parts[1];
            String[] hm = time.split(":");
            int hour = Integer.parseInt(hm[0]);
            int min = Integer.parseInt(hm[1]);
            if (ampm.equals("PM") && hour != 12)
                hour += 12;
            if (ampm.equals("AM") && hour == 12)
                hour = 0;
            return hour * 60 + min;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing time: " + timeStr, e);
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