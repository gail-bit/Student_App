package com.activity.studentapp;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.activity.studentapp.databinding.ActivityStudentLoginBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class StudentLoginActivity extends AppCompatActivity {
    private ActivityStudentLoginBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter[] intentFilters;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);

        // Initialize NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE);

            // Support multiple tag types
            IntentFilter tag = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
            IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            try {
                ndef.addDataType("*/*");
            } catch (IntentFilter.MalformedMimeTypeException e) {
                throw new RuntimeException("fail", e);
            }
            IntentFilter tech = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);

            intentFilters = new IntentFilter[]{tag, ndef, tech};
            showToast("NFC is ready. Tap your RFID card to login.");
        } else if (nfcAdapter != null && !nfcAdapter.isEnabled()) {
            showToast("Please enable NFC in settings to use RFID login.");
        } else {
            showToast("NFC is not available on this device.");
        }

        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        android.util.Log.d("StudentLogin", "NFC Intent received: " + action);

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) ||
            NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action) ||
            NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                showToast("RFID card detected!");
                android.util.Log.d("StudentLogin", "Tag detected, processing...");
                processNfcTag(tag);
            } else {
                showToast("RFID card detected but no tag data");
            }
        }
    }

    private void setupClickListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            String studentId = binding.etStudentId.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();

            android.util.Log.d("StudentLogin", "Entered Student ID: " + studentId);

            if (validateInputs(studentId, password)) {
                // Show loading state
                binding.btnLogin.setEnabled(false);
                binding.btnLogin.setText("Logging in...");

                // Use student ID as email for login
                String email = studentId + "@school.edu";
                android.util.Log.d("StudentLogin", "Using email for login: " + email);
                signInWithEmailAndPassword(email, password, studentId);
            }
        });

        binding.tvForgotPassword.setOnClickListener(v -> {
            // TODO: Implement forgot password logic here
            showForgotPasswordDialog();
        });
    }

    private boolean validateInputs(String studentId, String password) {
        if (studentId.isEmpty()) {
            binding.tilStudentId.setError("Student ID is required");
            return false;
        }
        binding.tilStudentId.setError(null);

        if (password.isEmpty()) {
            binding.tilPassword.setError("Password is required");
            return false;
        }
        binding.tilPassword.setError(null);

        return true;
    }

    private void signInWithEmailAndPassword(String email, String password, String studentId) {
        android.util.Log.d("StudentLogin", "Attempting sign in with email: " + email);
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    android.util.Log.d("StudentLogin", "Sign in successful");
                    // Sign in success, check if user is a student
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        android.util.Log.d("StudentLogin", "User UID: " + user.getUid());
                        android.util.Log.d("StudentLogin", "Passing studentId to checkIfStudentAndRedirect: " + studentId);
                        // Since students log in with student ID, check using the entered student ID
                        checkIfStudentAndRedirect(studentId);
                        // Store studentId in SharedPreferences for future sessions
                        sharedPreferences.edit().putString("studentId", studentId).apply();
                    } else {
                        android.util.Log.d("StudentLogin", "Sign in successful but currentUser is null");
                    }
                } else {
                    // If sign in fails, try to create account if it's a new student
                    String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    android.util.Log.e("StudentLogin", "Authentication failed: " + errorMessage);

                    // Check if this is a new student account that needs to be created
                    checkAndCreateStudentAccount(email, password);
                }
            });
    }

    private void checkAndCreateStudentAccount(String email, String password) {
        android.util.Log.d("StudentLogin", "Checking if student account needs to be created for: " + email);

        // Use the email to extract student ID
        String studentId = email.replace("@school.edu", "");

        // Check if student exists in Firestore
        db.collection("students").whereEqualTo("studentId", studentId).get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                    // Student exists in Firestore, try to create Firebase Auth account
                    android.util.Log.d("StudentLogin", "Student found in Firestore, creating Firebase Auth account");

                    String authEmail = studentId + "@school.edu";
                    mAuth.createUserWithEmailAndPassword(authEmail, password)
                        .addOnCompleteListener(createTask -> {
                            if (createTask.isSuccessful()) {
                                android.util.Log.d("StudentLogin", "Firebase Auth account created successfully");
                                // Now try to sign in again
                                signInWithEmailAndPassword(email, password, studentId);
                            } else {
                                String createError = createTask.getException() != null ? createTask.getException().getMessage() : "Unknown error";
                                android.util.Log.e("StudentLogin", "Failed to create Firebase Auth account: " + createError);
                                showToast("Account setup failed: " + createError);
                                resetLoginButton();
                            }
                        });
                } else {
                    // Student not found in Firestore
                    android.util.Log.d("StudentLogin", "Student not found in Firestore");
                    showToast("Student ID not found. Please contact administration.");
                    resetLoginButton();
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("StudentLogin", "Error checking student in Firestore: " + e.getMessage());
                showToast("Error verifying student ID: " + e.getMessage());
                resetLoginButton();
            });
    }

    private void checkIfStudentAndRedirect(String identifier) {
        android.util.Log.d("StudentLogin", "Checking if user is student with identifier: " + identifier);

        // First try to find by studentId field
        android.util.Log.d("StudentLogin", "Querying Firestore for studentId: " + identifier);
        db.collection("students").whereEqualTo("studentId", identifier).get()
            .addOnCompleteListener(task -> {
                android.util.Log.d("StudentLogin", "Firestore query by studentId completed. Success: " + task.isSuccessful());
                if (task.isSuccessful() && task.getResult() != null) {
                    android.util.Log.d("StudentLogin", "Query result size: " + task.getResult().size());
                    if (!task.getResult().isEmpty()) {
                        android.util.Log.d("StudentLogin", "User is a student (found by studentId), redirecting to main activity");
                        // Store studentId and document ID in SharedPreferences
                        sharedPreferences.edit().putString("studentId", identifier).putString("studentDocId", task.getResult().getDocuments().get(0).getId()).apply();
                        // User is a student, redirect to main activity
                        redirectToMainActivity();
                    } else {
                        android.util.Log.d("StudentLogin", "No documents found by studentId, trying document ID");
                        // If not found by studentId, try to find by document ID (for existing sessions)
                        db.collection("students").document(identifier).get()
                            .addOnCompleteListener(docTask -> {
                                android.util.Log.d("StudentLogin", "Firestore document get by ID completed. Success: " + docTask.isSuccessful());
                                if (docTask.isSuccessful() && docTask.getResult() != null) {
                                    android.util.Log.d("StudentLogin", "Document exists: " + docTask.getResult().exists());
                                    if (docTask.getResult().exists()) {
                                        android.util.Log.d("StudentLogin", "User is a student (found by document ID), redirecting to main activity");
                                        // Get studentId from document and store it and document ID
                                        String studentIdFromDoc = docTask.getResult().getString("studentId");
                                        if (studentIdFromDoc != null) {
                                            sharedPreferences.edit().putString("studentId", studentIdFromDoc).putString("studentDocId", identifier).apply();
                                        }
                                        // User is a student, redirect to main activity
                                        redirectToMainActivity();
                                    } else {
                                        android.util.Log.d("StudentLogin", "User is not a student, signing out");
                                        // User is not a student, sign them out and show error
                                        mAuth.signOut();
                                        showToast("Access denied. Please use the student app.");
                                        resetLoginButton();
                                    }
                                } else {
                                    android.util.Log.d("StudentLogin", "Document get failed or result is null");
                                }
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.e("StudentLogin", "Error verifying student status by document ID: " + e.getMessage());
                                showToast("Error verifying student status: " + e.getMessage());
                                resetLoginButton();
                            });
                    }
                } else {
                    android.util.Log.d("StudentLogin", "Query by studentId failed or result is null");
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("StudentLogin", "Error verifying student status by studentId: " + e.getMessage());
                showToast("Error verifying student status: " + e.getMessage());
                resetLoginButton();
            });
    }

    private void redirectToMainActivity() {
        Intent intent = new Intent(StudentLoginActivity.this, MainActivity.class);
        intent.putExtra("fromLogin", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void resetLoginButton() {
        binding.btnLogin.setEnabled(true);
        binding.btnLogin.setText("LOGIN");
    }


    private void processNfcTag(Tag tag) {
        // Read the tag ID
        String tagId = bytesToHexString(tag.getId());
        android.util.Log.d("StudentLogin", "NFC Tag ID: " + tagId);
        showToast("Reading RFID card...");

        // Log tag technologies
        String[] techList = tag.getTechList();
        for (String tech : techList) {
            android.util.Log.d("StudentLogin", "Tag technology: " + tech);
        }

        // Use the tag ID as student identifier
        authenticateWithRfid(tagId);
    }

    private void authenticateWithRfid(String rfidId) {
        // Show loading message
        showToast("Authenticating with RFID...");

        // Query Firestore for student with this RFID tag
        db.collection("students").whereEqualTo("rfidTag", rfidId).get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                    DocumentSnapshot document = task.getResult().getDocuments().get(0);
                    String studentId = document.getString("studentId");
                    if (studentId != null) {
                        android.util.Log.d("StudentLogin", "RFID authentication successful for student: " + studentId);
                        // Store student info and redirect
                        sharedPreferences.edit().putString("studentId", studentId).putString("studentDocId", document.getId()).apply();
                        showToast("Login successful!");
                        redirectToMainActivity();
                    } else {
                        showToast("Invalid RFID card data");
                    }
                } else {
                    android.util.Log.d("StudentLogin", "RFID ID not found in database");
                    showToast("RFID card not recognized");
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("StudentLogin", "Error querying RFID: " + e.getMessage());
                showToast("Error reading RFID card");
            });
    }


    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private void showForgotPasswordDialog() {
        // TODO: Implement forgot password dialog or navigation
        showToast("Forgot Password clicked");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
