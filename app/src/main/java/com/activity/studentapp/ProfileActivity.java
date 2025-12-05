package com.activity.studentapp;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.activity.studentapp.databinding.ActivityProfileBinding;
import com.activity.studentapp.model.Student;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private Student student;
    private FirebaseFirestore db;
    private com.google.firebase.firestore.ListenerRegistration studentListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Get student data from shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        String studentDocId = sharedPreferences.getString("studentDocId", null);

        if (studentDocId == null) {
            Toast.makeText(this, "Student information not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        loadStudentData(studentDocId);
        setupClickListeners();

        // Set up real-time listener for student data updates
        studentListener = db.collection("students").document(studentDocId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Log.e("ProfileActivity", "Error listening to student data: " + e.getMessage());
                        return;
                    }
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Student updatedStudent = documentSnapshot.toObject(Student.class);
                        if (updatedStudent != null) {
                            updatedStudent.setId(documentSnapshot.getId());
                            this.student = updatedStudent;
                            runOnUiThread(() -> setupStudentDetails());
                        }
                    }
                });
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Profile");
        }
    }

    private void loadStudentData(String studentDocId) {
        db.collection("students")
                .document(studentDocId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        student = documentSnapshot.toObject(Student.class);
                        if (student != null) {
                            student.setId(documentSnapshot.getId());
                            setupStudentDetails();
                        } else {
                            Toast.makeText(this, "Failed to load student data", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    } else {
                        Toast.makeText(this, "Student data not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ProfileActivity", "Error loading student data: " + e.getMessage());
                    Toast.makeText(this, "Error loading profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void setupStudentDetails() {
        if (student == null)
            return;

        // Set student name
        binding.studentName.setText(student.getFullName());

        // Set student info (grade level and section)
        String info = student.getGradeLevel();
        if (student.getSection() != null && !student.getSection().isEmpty()) {
            info += " - " + student.getSection();
        }
        if (student.getSchoolYear() != null && !student.getSchoolYear().isEmpty()) {
            info += " | " + student.getSchoolYear();
        }
        binding.studentInfo.setText(info);

        // Set status
        binding.studentStatus.setText(student.getStatus());

        // Set status chip color based on status
        int statusColor = R.color.gray_500; // Default color
        switch (student.getStatus()) {
            case "Active":
                statusColor = R.color.green_600;
                break;
            case "Inactive":
                statusColor = R.color.red_600;
                break;
            case "Graduated":
                statusColor = R.color.blue_600;
                break;
            case "Transferred":
                statusColor = R.color.orange_600;
                break;
        }
        binding.studentStatus.setChipBackgroundColorResource(statusColor);

        // Set student ID
        binding.studentId.setText(student.getStudentId());

        // Set name components
        binding.studentFirstName.setText(student.getFirstName() != null ? student.getFirstName() : "Not specified");
        binding.studentMiddleName.setText(student.getMiddleName() != null ? student.getMiddleName() : "Not specified");
        binding.studentLastName.setText(student.getLastName() != null ? student.getLastName() : "Not specified");

        // Set email
        binding.studentEmail.setText(student.getEmail());

        // Set phone
        binding.studentPhone.setText(student.getPhone());

        // Parse address components from the combined address string
        String fullAddress = student.getAddress();
        if (fullAddress != null && !fullAddress.isEmpty()) {
            String[] addressParts = fullAddress.split(", ");
            if (addressParts.length >= 4) {
                // Format: "houseNumber, street, barangay, municipality"
                binding.studentHouseNumber.setText(addressParts[0].trim());
                binding.studentStreet.setText(addressParts[1].trim());
                binding.studentBarangay.setText(addressParts[2].trim());
                binding.studentMunicipality.setText(addressParts[3].trim());
            } else if (addressParts.length >= 2) {
                // Fallback: assume "houseNumber street, barangay, municipality"
                String houseStreet = addressParts[0];
                binding.studentBarangay.setText(addressParts[1].trim());
                if (addressParts.length >= 3) {
                    binding.studentMunicipality.setText(addressParts[2].trim());
                }
                // Try to split house and street
                String[] houseStreetParts = houseStreet.split(" ", 2);
                if (houseStreetParts.length >= 2) {
                    binding.studentHouseNumber.setText(houseStreetParts[0].trim());
                    binding.studentStreet.setText(houseStreetParts[1].trim());
                } else {
                    binding.studentStreet.setText(houseStreet.trim());
                    binding.studentHouseNumber.setText("Not specified");
                }
            } else {
                // Fallback for incomplete address
                binding.studentHouseNumber.setText("Not specified");
                binding.studentStreet.setText(fullAddress.trim());
                binding.studentBarangay.setText("Not specified");
                binding.studentMunicipality.setText("Not specified");
            }
        } else {
            binding.studentHouseNumber.setText("Not specified");
            binding.studentStreet.setText("Not specified");
            binding.studentBarangay.setText("Not specified");
            binding.studentMunicipality.setText("Not specified");
        }

        // Set full address
        binding.studentAddress.setText(student.getAddress());

        // Set birth date
        binding.studentBirthDate.setText(student.getBirthDate() != null ? student.getBirthDate() : "Not specified");

        // Set gender
        binding.studentGender.setText(student.getGender() != null ? student.getGender() : "Not specified");

        // Set status
        binding.studentStatusText.setText(student.getStatus() != null ? student.getStatus() : "Not specified");

        // Set created and updated dates
        if (student.getCreatedAt() > 0) {
            binding.studentCreatedDate
                    .setText(new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            .format(new java.util.Date(student.getCreatedAt())));
        } else {
            binding.studentCreatedDate.setText("Not available");
        }

        // Load student photo if available
        if (student.getPhotoUrl() != null && !student.getPhotoUrl().isEmpty()) {
            // For now, just use default avatar since Glide might not be available
            binding.studentAvatar.setImageResource(R.drawable.ic_person);
        } else {
            binding.studentAvatar.setImageResource(R.drawable.ic_person);
        }
    }

    private void setupClickListeners() {
        binding.btnDownloadPdf.setOnClickListener(v -> downloadStudentRecordPdf());
    }

    private void downloadStudentRecordPdf() {
        // Get student section info to fetch enrolled subjects
        String sectionName = student.getGradeLevel() + " - " + student.getSection();

        // Fetch subjects from class_schedule
        db.collection("class_schedule")
                .whereEqualTo("sectionName", sectionName)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    java.util.Set<String> subjectIds = new java.util.HashSet<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        String subjectId = doc.getString("subjectId");
                        if (subjectId != null) {
                            subjectIds.add(subjectId);
                        }
                    }
                    if (subjectIds.isEmpty()) {
                        generatePdf(new java.util.ArrayList<>());
                        return;
                    }
                    // Fetch subject names
                    db.collection("subjects")
                            .whereIn("__name__", new java.util.ArrayList<>(subjectIds))
                            .get()
                            .addOnSuccessListener(subjectsSnapshot -> {
                                java.util.Map<String, String> subjectMap = new java.util.HashMap<>();
                                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : subjectsSnapshot) {
                                    String name = doc.getString("subjectName");
                                    if (name != null) {
                                        subjectMap.put(doc.getId(), name);
                                    }
                                }
                                java.util.List<String> subjects = new java.util.ArrayList<>();
                                for (String id : subjectIds) {
                                    String name = subjectMap.get(id);
                                    if (name != null) {
                                        subjects.add(name);
                                    }
                                }
                                generatePdf(subjects);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to fetch subject names: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                                generatePdf(new java.util.ArrayList<>());
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch subjects: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    generatePdf(null);
                });
    }

    private void generatePdf(java.util.List<String> subjects) {
        PdfDocument pdfDocument = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4 size
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(24);
        titlePaint.setFakeBoldText(true);

        Paint labelPaint = new Paint();
        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(14);
        labelPaint.setFakeBoldText(true);

        Paint valuePaint = new Paint();
        valuePaint.setColor(Color.BLACK);
        valuePaint.setTextSize(14);

        int yPosition = 50;

        // Title
        canvas.drawText("Student Registration Record", 50, yPosition, titlePaint);
        yPosition += 50;

        // Personal Information Section
        canvas.drawText("Personal Information", 50, yPosition, labelPaint);
        yPosition += 25;

        // Personal Information
        canvas.drawText("Full Name:", 50, yPosition, labelPaint);
        canvas.drawText(student.getFullName(), 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("Student ID:", 50, yPosition, labelPaint);
        canvas.drawText(student.getStudentId(), 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("First Name:", 50, yPosition, labelPaint);
        canvas.drawText(student.getFirstName() != null ? student.getFirstName() : "N/A", 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("Middle Name:", 50, yPosition, labelPaint);
        canvas.drawText(student.getMiddleName() != null ? student.getMiddleName() : "N/A", 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("Last Name:", 50, yPosition, labelPaint);
        canvas.drawText(student.getLastName() != null ? student.getLastName() : "N/A", 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("Birth Date:", 50, yPosition, labelPaint);
        canvas.drawText(student.getBirthDate() != null ? student.getBirthDate() : "N/A", 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("Gender:", 50, yPosition, labelPaint);
        canvas.drawText(student.getGender() != null ? student.getGender() : "N/A", 150, yPosition, valuePaint);
        yPosition += 30;

        // Contact Information Section
        canvas.drawText("Contact Information", 50, yPosition, labelPaint);
        yPosition += 25;
        canvas.drawText("Email:", 50, yPosition, labelPaint);
        canvas.drawText(student.getEmail() != null ? student.getEmail() : "N/A", 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("Phone:", 50, yPosition, labelPaint);
        canvas.drawText(student.getPhone() != null ? student.getPhone() : "N/A", 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("Address:", 50, yPosition, labelPaint);
        canvas.drawText(student.getAddress() != null ? student.getAddress() : "N/A", 150, yPosition, valuePaint);
        yPosition += 30;

        // Academic Information Section
        canvas.drawText("Academic Information", 50, yPosition, labelPaint);
        yPosition += 25;
        canvas.drawText("Grade Level:", 50, yPosition, labelPaint);
        canvas.drawText(student.getGradeLevel() != null ? student.getGradeLevel() : "N/A", 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("Section:", 50, yPosition, labelPaint);
        canvas.drawText(student.getSection() != null ? student.getSection() : "N/A", 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("School Year:", 50, yPosition, labelPaint);
        canvas.drawText(student.getSchoolYear() != null ? student.getSchoolYear() : "N/A", 150, yPosition, valuePaint);
        yPosition += 20;
        canvas.drawText("Status:", 50, yPosition, labelPaint);
        canvas.drawText("Enrolled", 150, yPosition, valuePaint);
        yPosition += 30;

        // Enrolled Subjects Section
        canvas.drawText("Enrolled Subjects", 50, yPosition, labelPaint);
        yPosition += 25;
        if (subjects != null && !subjects.isEmpty()) {
            for (String subject : subjects) {
                canvas.drawText("- " + subject, 70, yPosition, valuePaint);
                yPosition += 20;
            }
        } else {
            canvas.drawText("None", 70, yPosition, valuePaint);
            yPosition += 20;
        }
        yPosition += 20;

        // Record Information
        if (student.getCreatedAt() > 0) {
            canvas.drawText("Enrolled: " + new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                    .format(new java.util.Date(student.getCreatedAt())), 50, yPosition, valuePaint);
        }

        pdfDocument.finishPage(page);

        // Save to file
        String studentName = student.getFullName().replaceAll("[^a-zA-Z0-9\\s]", "").replace(" ", "_");
        String fileName = studentName + "_Registration_Form.pdf";
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }
        File file = new File(downloadsDir, fileName);
        try {
            pdfDocument.writeTo(new FileOutputStream(file));
            Toast.makeText(this, "PDF saved to Downloads: " + file.getName(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        pdfDocument.close();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (studentListener != null) {
            studentListener.remove();
        }
        binding = null;
    }
}