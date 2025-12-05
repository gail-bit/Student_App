package com.activity.studentapp;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.activity.studentapp.adapter.FileAdapter;
import com.activity.studentapp.model.Activity;
import com.activity.studentapp.model.Subject;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssignmentDetailsActivity extends AppCompatActivity {

    private Activity assignment;
    private Subject subject;

    // UI components
    private TextView assignmentTitle;
    private TextView assignmentDescription;
    private TextView assignmentDueDate;
    private TextView assignmentSubject;
    private TextView assignmentInstructor;
    private TextView submissionStatus;
    private TextView submittedFilesLabel;
    private TextView uploadSectionLabel;
    private LinearLayout gradeSection;
    private TextView assignmentGrade;
    private TextView gradeComments;
    private ImageButton backButton;
    private RecyclerView attachedFilesRecyclerView;
    private RecyclerView submittedFilesRecyclerView;
    private com.google.android.material.button.MaterialButton attachFileButton;
    private com.google.android.material.button.MaterialButton submitAssignmentButton;

    private List<Uri> attachedFiles;
    private FileAdapter adapter;

    // Firebase instances
    private FirebaseFirestore db;
    private String studentId;

    private static final int PICK_FILES_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assignment_details);

        // Get assignment and subject data from intent
        assignment = (Activity) getIntent().getSerializableExtra("assignment");
        subject = (Subject) getIntent().getSerializableExtra("subject");

        if (assignment == null) {
            Toast.makeText(this, "Assignment data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase instances
        db = FirebaseFirestore.getInstance();

        // Retrieve studentId from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);
        studentId = prefs.getString("studentId", null);
        if (studentId == null) {
            Toast.makeText(this, "Student ID not found. Please log in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupUI();
        checkSubmissionStatus();
        setupClickListeners();

        // Handle back press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void initializeViews() {
        assignmentTitle = findViewById(R.id.assignmentTitle);
        assignmentDescription = findViewById(R.id.assignmentDescription);
        assignmentDueDate = findViewById(R.id.assignmentDueDate);
        assignmentSubject = findViewById(R.id.assignmentSubject);
        assignmentInstructor = findViewById(R.id.assignmentInstructor);
        submissionStatus = findViewById(R.id.submissionStatus);
        submittedFilesLabel = findViewById(R.id.submittedFilesLabel);
        uploadSectionLabel = findViewById(R.id.uploadSectionLabel);
        gradeSection = findViewById(R.id.gradeSection);
        assignmentGrade = findViewById(R.id.assignmentGrade);
        gradeComments = findViewById(R.id.gradeComments);
        backButton = findViewById(R.id.backButton);
        attachedFilesRecyclerView = findViewById(R.id.attachedFilesRecyclerView);
        submittedFilesRecyclerView = findViewById(R.id.submittedFilesRecyclerView);
        attachFileButton = findViewById(R.id.attachFileButton);
        submitAssignmentButton = findViewById(R.id.submitAssignmentButton);
    }

    private void setupUI() {
        // Set assignment details
        if (assignmentTitle != null) {
            assignmentTitle.setText(assignment.getTitle() != null ? assignment.getTitle() : "Untitled Assignment");
        }

        if (assignmentDescription != null) {
            assignmentDescription.setText(
                    assignment.getDescription() != null ? assignment.getDescription() : "No description available");
        }

        if (assignmentDueDate != null) {
            String dueDateText = "Due: ";
            if (assignment.getDueDate() > 0) {
                // Format the due date
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a",
                        java.util.Locale.getDefault());
                dueDateText += sdf.format(new java.util.Date(assignment.getDueDate()));
            } else {
                dueDateText += "No due date specified";
            }
            assignmentDueDate.setText(dueDateText);
        }

        if (assignmentSubject != null && subject != null) {
            assignmentSubject.setText("Subject: " + (subject.getName() != null ? subject.getName() : "Unknown"));
        }

        if (assignmentInstructor != null) {
            String instructorName = assignment.getInstructorName();
            if (instructorName == null && subject != null) {
                instructorName = subject.getInstructor();
            }
            assignmentInstructor.setText("Instructor: " + (instructorName != null ? instructorName : "Unknown"));
        }

        Log.d("AssignmentDetailsActivity", "Displaying assignment: " + assignment.getTitle());

        // Initialize file attachment components
        attachedFiles = new ArrayList<>();
        adapter = new FileAdapter(attachedFiles, position -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete File")
                    .setMessage("Are you sure you want to delete this file?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        attachedFiles.remove(position);
                        adapter.notifyItemRemoved(position);
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
        attachedFilesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        attachedFilesRecyclerView.setAdapter(adapter);

        // Initialize submitted files RecyclerView
        submittedFilesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupClickListeners() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (attachFileButton != null) {
            attachFileButton.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, PICK_FILES_REQUEST);
            });
        }

        if (submitAssignmentButton != null) {
            submitAssignmentButton.setOnClickListener(v -> submitAssignment());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILES_REQUEST && resultCode == RESULT_OK && data != null) {
            List<Uri> selectedUris = new ArrayList<>();
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    selectedUris.add(uri);
                }
            } else if (data.getData() != null) {
                selectedUris.add(data.getData());
            }

            List<Uri> validFiles = new ArrayList<>();
            for (Uri uri : selectedUris) {
                long size = getFileSize(uri);
                if (size > 0 && size <= 10 * 1024 * 1024) {
                    validFiles.add(uri);
                } else {
                    Toast.makeText(this, "File too large. Maximum size is 10MB.", Toast.LENGTH_SHORT).show();
                }
            }
            attachedFiles.addAll(validFiles);
            adapter.notifyDataSetChanged();
        }
    }

    private long getFileSize(Uri uri) {
        try {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            long size = pfd.getStatSize();
            pfd.close();
            return size;
        } catch (Exception e) {
            return -1;
        }
    }

    private String encodeFileToBase64(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            inputStream.close();
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e("AssignmentDetails", "Error encoding file to base64", e);
            return null;
        }
    }

    private void submitAssignment() {
        if (attachedFiles.isEmpty()) {
            Toast.makeText(this, "No files attached", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Processing files...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Map<String, Object> submission = new HashMap<>();
        submission.put("studentId", studentId);
        submission.put("assignmentId", assignment.getId());
        submission.put("submittedAt", System.currentTimeMillis());
        submission.put("status", "submitted");
        submission.put("grade", null);
        submission.put("gradeComments", null);

        db.collection("submissions").add(submission)
                .addOnSuccessListener(documentReference -> {
                    // Now save files in subcollection
                    saveFilesToSubcollection(documentReference.getId(), attachedFiles, progressDialog);
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to save submission: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveFilesToSubcollection(String submissionId, List<Uri> files, ProgressDialog progressDialog) {
        if (files.isEmpty()) {
            progressDialog.dismiss();
            Toast.makeText(this, "Assignment submitted successfully", Toast.LENGTH_SHORT).show();
            // Refresh the UI to show submitted state
            checkSubmissionStatus();
            return;
        }

        Uri fileUri = files.get(0);
        String fileName = getFileName(fileUri);
        String base64 = encodeFileToBase64(fileUri);

        if (base64 == null) {
            progressDialog.dismiss();
            Toast.makeText(this, "Failed to encode file: " + fileName, Toast.LENGTH_SHORT).show();
            return;
        }

        // Split base64 into chunks if too large (Firestore field limit ~1MB)
        List<String> chunks = splitBase64IntoChunks(base64, 900000); // Use 900KB chunks to be safe

        // Save file metadata first
        Map<String, Object> fileDoc = new HashMap<>();
        fileDoc.put("name", fileName);
        fileDoc.put("chunkCount", chunks.size());

        db.collection("submissions").document(submissionId).collection("files").add(fileDoc)
                .addOnSuccessListener(fileDocRef -> {
                    // Save chunks as separate documents
                    saveBase64Chunks(fileDocRef.getId(), chunks, submissionId, files, progressDialog);
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to save file metadata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private List<String> splitBase64IntoChunks(String base64, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = base64.length();
        for (int i = 0; i < length; i += chunkSize) {
            int end = Math.min(i + chunkSize, length);
            chunks.add(base64.substring(i, end));
        }
        return chunks;
    }

    private void saveBase64Chunks(String fileId, List<String> chunks, String submissionId, List<Uri> files,
            ProgressDialog progressDialog) {
        // Calculate the total number of chunks originally
        int totalChunks = chunks.size();
        saveBase64ChunksRecursive(fileId, chunks, 0, totalChunks, submissionId, files, progressDialog);
    }

    private void saveBase64ChunksRecursive(String fileId, List<String> chunks, int currentIndex, int totalChunks,
            String submissionId, List<Uri> files, ProgressDialog progressDialog) {
        if (currentIndex >= totalChunks) {
            // All chunks saved, move to next file
            files.remove(0);
            saveFilesToSubcollection(submissionId, files, progressDialog);
            return;
        }

        String chunk = chunks.get(currentIndex);
        Map<String, Object> chunkDoc = new HashMap<>();
        chunkDoc.put("data", chunk);
        chunkDoc.put("index", currentIndex);

        Log.d("AssignmentDetails",
                "Saving chunk " + currentIndex + "/" + (totalChunks - 1) + " for file " + fileId + ", data length: "
                        + chunk.length());

        db.collection("submissions").document(submissionId).collection("files").document(fileId).collection("chunks")
                .document("chunk_" + currentIndex) // Use fixed document ID instead of auto-generated
                .set(chunkDoc)
                .addOnSuccessListener(aVoid -> {
                    Log.d("AssignmentDetails", "Successfully saved chunk " + currentIndex + " for file " + fileId);
                    saveBase64ChunksRecursive(fileId, chunks, currentIndex + 1, totalChunks, submissionId, files,
                            progressDialog);
                })
                .addOnFailureListener(e -> {
                    Log.e("AssignmentDetails", "Failed to save chunk " + currentIndex + " for file " + fileId, e);
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to save file chunk: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void checkSubmissionStatus() {
        db.collection("submissions")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("assignmentId", assignment.getId())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Submitted
                        DocumentSnapshot submissionDoc = querySnapshot.getDocuments().get(0);
                        long submittedAt = submissionDoc.getLong("submittedAt");
                        showSubmittedUI(submittedAt);
                        loadSubmittedFiles(submissionDoc.getReference());
                        loadGrade(submissionDoc.getReference());
                    } else {
                        // Not submitted, check due date
                        if (isDueDatePassed()) {
                            showPastDueUI();
                        } else {
                            showUploadUI();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AssignmentDetails", "Error checking submission", e);
                    // Default to upload UI if error
                    if (!isDueDatePassed()) {
                        showUploadUI();
                    } else {
                        showPastDueUI();
                    }
                });
    }

    private boolean isDueDatePassed() {
        return assignment.getDueDate() > 0 && assignment.getDueDate() < System.currentTimeMillis();
    }

    private void showSubmittedUI(long submittedAt) {
        // Show submission status
        if (submissionStatus != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a",
                    java.util.Locale.getDefault());
            String statusText = "Submitted on " + sdf.format(new java.util.Date(submittedAt));
            submissionStatus.setText(statusText);
            submissionStatus.setVisibility(View.VISIBLE);
        }

        // Show submitted files section
        if (submittedFilesLabel != null) {
            submittedFilesLabel.setVisibility(View.VISIBLE);
        }
        if (submittedFilesRecyclerView != null) {
            submittedFilesRecyclerView.setVisibility(View.VISIBLE);
        }

        // Hide upload section
        hideUploadUI();
    }

    private void showPastDueUI() {
        // Show past due status
        if (submissionStatus != null) {
            submissionStatus.setText("Assignment is past due");
            submissionStatus.setTextColor(getResources().getColor(R.color.error));
            submissionStatus.setVisibility(View.VISIBLE);
        }

        // Hide upload section
        hideUploadUI();
    }

    private void showUploadUI() {
        // Show upload section
        if (uploadSectionLabel != null) {
            uploadSectionLabel.setVisibility(View.VISIBLE);
        }
        if (attachFileButton != null) {
            attachFileButton.setVisibility(View.VISIBLE);
        }
        if (attachedFilesRecyclerView != null) {
            attachedFilesRecyclerView.setVisibility(View.VISIBLE);
        }
        if (submitAssignmentButton != null) {
            submitAssignmentButton.setVisibility(View.VISIBLE);
        }
    }

    private void hideUploadUI() {
        if (uploadSectionLabel != null) {
            uploadSectionLabel.setVisibility(View.GONE);
        }
        if (attachFileButton != null) {
            attachFileButton.setVisibility(View.GONE);
        }
        if (attachedFilesRecyclerView != null) {
            attachedFilesRecyclerView.setVisibility(View.GONE);
        }
        if (submitAssignmentButton != null) {
            submitAssignmentButton.setVisibility(View.GONE);
        }
    }

    private void loadSubmittedFiles(DocumentReference submissionRef) {
        submissionRef.collection("files").get()
                .addOnSuccessListener(querySnapshot -> {
                    if (submittedFilesRecyclerView != null && !querySnapshot.isEmpty()) {
                        List<Map<String, String>> files = new ArrayList<>();
                        loadFileChunks(querySnapshot.getDocuments(), 0, files, submissionRef.getId());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AssignmentDetails", "Error loading submitted files", e);
                });
    }

    private void loadGrade(DocumentReference submissionRef) {
        submissionRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String grade = documentSnapshot.getString("grade");
                String comments = documentSnapshot.getString("gradeComments");

                if (grade != null && !grade.isEmpty()) {
                    if (gradeSection != null) {
                        gradeSection.setVisibility(View.VISIBLE);
                    }
                    if (assignmentGrade != null) {
                        // Parse grade to calculate percentage
                        String displayText = calculateGradeDisplayText(grade);
                        assignmentGrade.setText(displayText);
                    }
                    if (gradeComments != null && comments != null && !comments.isEmpty()) {
                        gradeComments.setText(comments);
                        gradeComments.setVisibility(View.VISIBLE);
                    }
                }
            }
        }).addOnFailureListener(e -> {
            Log.e("AssignmentDetails", "Error loading grade", e);
        });
    }

    private String calculateGradeDisplayText(String grade) {
        try {
            double score = 0;
            double total = assignment.getTotalPoints();

            if (grade.contains("/")) {
                // Format: "score/total"
                String[] parts = grade.split("/");
                if (parts.length == 2) {
                    score = Double.parseDouble(parts[0].trim());
                    total = Double.parseDouble(parts[1].trim());
                }
            } else {
                // Assume grade is just the score
                score = Double.parseDouble(grade.trim());
            }

            if (total > 0) {
                double percentage = (score / total) * 100;
                return String.format("%s (%.1f%%)", grade, percentage);
            } else {
                return grade; // Fallback to original grade if total is 0 or invalid
            }
        } catch (NumberFormatException e) {
            Log.e("AssignmentDetails", "Error parsing grade: " + grade, e);
            return grade; // Fallback to original grade
        }
    }

    private void loadFileChunks(List<DocumentSnapshot> fileDocs, int index, List<Map<String, String>> files,
            String submissionId) {
        if (index >= fileDocs.size()) {
            // All files loaded, create adapter
            SubmittedFilesAdapter adapter = new SubmittedFilesAdapter(files, this);
            submittedFilesRecyclerView.setAdapter(adapter);
            return;
        }

        DocumentSnapshot fileDoc = fileDocs.get(index);
        String fileName = fileDoc.getString("name");
        Long chunkCount = fileDoc.getLong("chunkCount");

        if (chunkCount != null && chunkCount > 0) {
            // Load chunks for this file
            loadChunksForFile(fileDoc.getReference(), chunkCount.intValue(), fileName, files, fileDocs, index,
                    submissionId);
        } else {
            // No chunks, skip this file
            loadFileChunks(fileDocs, index + 1, files, submissionId);
        }
    }

    private void loadChunksForFile(DocumentReference fileRef, int chunkCount, String fileName,
            List<Map<String, String>> files,
            List<DocumentSnapshot> fileDocs, int fileIndex, String submissionId) {
        Log.d("AssignmentDetails", "Loading " + chunkCount + " chunks for file: " + fileName);

        // Load chunks sequentially using fixed document IDs
        loadChunksSequentially(fileRef, chunkCount, fileName, files, fileDocs, fileIndex, submissionId, 0,
                new StringBuilder());
    }

    private void loadChunksSequentially(DocumentReference fileRef, int chunkCount, String fileName,
            List<Map<String, String>> files, List<DocumentSnapshot> fileDocs, int fileIndex,
            String submissionId, int currentChunkIndex, StringBuilder base64Builder) {

        if (currentChunkIndex >= chunkCount) {
            // All chunks loaded, create file map
            String reconstructedBase64 = base64Builder.toString();
            Log.d("AssignmentDetails",
                    "Reconstructed base64 length: " + reconstructedBase64.length() + " for file: " + fileName);

            Map<String, String> fileMap = new HashMap<>();
            fileMap.put("name", fileName);
            fileMap.put("base64", reconstructedBase64);
            files.add(fileMap);

            // Load next file
            loadFileChunks(fileDocs, fileIndex + 1, files, submissionId);
            return;
        }

        String chunkDocId = "chunk_" + currentChunkIndex;
        fileRef.collection("chunks").document(chunkDocId).get()
                .addOnSuccessListener(chunkDoc -> {
                    if (chunkDoc.exists()) {
                        String chunkData = chunkDoc.getString("data");
                        Log.d("AssignmentDetails", "Loaded chunk " + currentChunkIndex + ", data length: "
                                + (chunkData != null ? chunkData.length() : "null"));
                        if (chunkData != null) {
                            base64Builder.append(chunkData);
                        }
                    } else {
                        Log.w("AssignmentDetails",
                                "Chunk document " + chunkDocId + " does not exist for file: " + fileName);
                    }

                    // Load next chunk
                    loadChunksSequentially(fileRef, chunkCount, fileName, files, fileDocs, fileIndex, submissionId,
                            currentChunkIndex + 1, base64Builder);
                })
                .addOnFailureListener(e -> {
                    Log.e("AssignmentDetails", "Error loading chunk " + currentChunkIndex + " for file " + fileName, e);
                    // Continue with next chunk even if this one failed
                    loadChunksSequentially(fileRef, chunkCount, fileName, files, fileDocs, fileIndex, submissionId,
                            currentChunkIndex + 1, base64Builder);
                });
    }

    // Adapter for displaying submitted files by decoding base64
    private static class SubmittedFilesAdapter extends RecyclerView.Adapter<SubmittedFilesAdapter.ViewHolder> {

        private List<Map<String, String>> files;
        private android.content.Context context;

        public SubmittedFilesAdapter(List<Map<String, String>> files, android.content.Context context) {
            this.files = files;
            this.context = context;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_submitted_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Map<String, String> fileMap = files.get(position);
            String fileName = fileMap.get("name");
            String base64Data = fileMap.get("base64");

            Log.d("SubmittedFilesAdapter", "Binding file: " + fileName + ", base64 length: " +
                    (base64Data != null ? base64Data.length() : "null"));

            holder.fileNameTextView.setText(fileName != null ? fileName : "Unknown File");

            // Reset views
            holder.fileImageView.setVisibility(View.GONE);
            holder.downloadButton.setVisibility(View.GONE);

            // Try to decode and display if it's an image
            if (base64Data != null && isImageFile(fileName)) {
                Log.d("SubmittedFilesAdapter", "File is image, attempting to decode: " + fileName);
                try {
                    byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    Log.d("SubmittedFilesAdapter", "Decoded bytes length: " + decodedBytes.length);

                    // Check if the decoded bytes look like valid image data
                    if (decodedBytes.length > 4) {
                        String header = String.format("%02X%02X%02X%02X", decodedBytes[0], decodedBytes[1],
                                decodedBytes[2], decodedBytes[3]);
                        Log.d("SubmittedFilesAdapter", "Image header: " + header);
                    }

                    // Try decoding with options first
                    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length, options);

                    Log.d("SubmittedFilesAdapter", "Image dimensions: " + options.outWidth + "x" + options.outHeight
                            + ", format: " + options.outMimeType);

                    if (options.outWidth > 0 && options.outHeight > 0) {
                        // Reset options for actual decoding
                        options.inJustDecodeBounds = false;
                        options.inSampleSize = calculateInSampleSize(options, 800, 600); // Max 800x600 for display
                        options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;

                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0,
                                decodedBytes.length, options);

                        if (bitmap != null) {
                            Log.d("SubmittedFilesAdapter", "Bitmap created successfully, size: " + bitmap.getWidth()
                                    + "x" + bitmap.getHeight());
                            holder.fileImageView.setImageBitmap(bitmap);
                            holder.fileImageView.setVisibility(View.VISIBLE);
                            holder.fileNameTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_library_books,
                                    0,
                                    0, 0);
                            holder.fileNameTextView.setCompoundDrawablePadding(8);
                        } else {
                            Log.w("SubmittedFilesAdapter", "Bitmap is null after decode, falling back to download");
                            holder.downloadButton.setVisibility(View.VISIBLE);
                            holder.fileNameTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file_download,
                                    0,
                                    0, 0);
                            holder.fileNameTextView.setCompoundDrawablePadding(8);
                        }
                    } else {
                        Log.w("SubmittedFilesAdapter", "Invalid image dimensions, falling back to download");
                        holder.downloadButton.setVisibility(View.VISIBLE);
                        holder.fileNameTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file_download, 0,
                                0, 0);
                        holder.fileNameTextView.setCompoundDrawablePadding(8);
                    }
                } catch (Exception e) {
                    Log.e("SubmittedFilesAdapter", "Error decoding image: " + e.getMessage(), e);
                    holder.downloadButton.setVisibility(View.VISIBLE);
                    holder.fileNameTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file_download, 0, 0,
                            0);
                    holder.fileNameTextView.setCompoundDrawablePadding(8);
                }
            } else {
                Log.d("SubmittedFilesAdapter", "File is not image or base64 is null: " + fileName +
                        ", isImage: " + isImageFile(fileName) + ", base64 null: " + (base64Data == null));
                // For non-image files, show download button
                holder.downloadButton.setVisibility(View.VISIBLE);
                holder.fileNameTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file_download, 0, 0, 0);
                holder.fileNameTextView.setCompoundDrawablePadding(8);
            }

            // Set click listener for download
            final String finalBase64Data = base64Data;
            final String finalFileName = fileName;
            holder.downloadButton.setOnClickListener(v -> {
                if (finalBase64Data != null && finalFileName != null) {
                    downloadFile(finalFileName, finalBase64Data);
                }
            });

            // Make the whole item clickable for download if no image
            if (holder.fileImageView.getVisibility() != View.VISIBLE) {
                holder.itemView.setOnClickListener(v -> {
                    if (finalBase64Data != null && finalFileName != null) {
                        downloadFile(finalFileName, finalBase64Data);
                    }
                });
            } else {
                holder.itemView.setOnClickListener(null);
            }
        }

        private boolean isImageFile(String fileName) {
            if (fileName == null)
                return false;
            String lowerName = fileName.toLowerCase();
            return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                    lowerName.endsWith(".bmp") || lowerName.endsWith(".webp");
        }

        private int calculateInSampleSize(android.graphics.BitmapFactory.Options options, int reqWidth, int reqHeight) {
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            return inSampleSize;
        }

        private void downloadFile(String fileName, String base64Data) {
            try {
                byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);

                // Save to downloads directory
                java.io.File downloadsDir = android.os.Environment
                        .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                java.io.File file = new java.io.File(downloadsDir, fileName);

                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                fos.write(decodedBytes);
                fos.close();

                Toast.makeText(context, "File saved to Downloads: " + fileName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(context, "Error saving file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("SubmittedFilesAdapter", "Error saving file", e);
            }
        }

        @Override
        public int getItemCount() {
            return files != null ? files.size() : 0;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView fileNameTextView;
            ImageView fileImageView;
            com.google.android.material.button.MaterialButton downloadButton;

            ViewHolder(android.view.View itemView) {
                super(itemView);
                fileNameTextView = itemView.findViewById(R.id.fileNameTextView);
                fileImageView = itemView.findViewById(R.id.fileImageView);
                downloadButton = itemView.findViewById(R.id.downloadButton);
            }
        }
    }
}