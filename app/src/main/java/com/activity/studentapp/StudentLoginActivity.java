package com.activity.studentapp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
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
    private BroadcastReceiver nfcStateReceiver;
    private Handler nfcRetryHandler;
    private boolean isUsbConnected = false;
    private boolean nfcInitializationAttempted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("StudentAppPrefs", MODE_PRIVATE);

        // Initialize handler for NFC retry logic
        nfcRetryHandler = new Handler(Looper.getMainLooper());

        // Check USB connection status
        checkUsbConnection();

        // Initialize NFC with improved reliability
        initializeNfc();

        // Register NFC state change receiver
        nfcStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(action)) {
                    final int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF);
                    Log.d("StudentLogin", "NFC state changed: " + state);

                    switch (state) {
                        case NfcAdapter.STATE_ON:
                            Log.d("StudentLogin", "NFC turned ON");
                            initializeNfc();
                            break;
                        case NfcAdapter.STATE_OFF:
                            Log.d("StudentLogin", "NFC turned OFF");
                            updateRfidButtonStatus("❌ NFC DISABLED - ENABLE IN SETTINGS",
                                    android.R.color.holo_red_dark);
                            showToast("NFC is disabled. Please enable NFC to use RFID login.");
                            break;
                        case NfcAdapter.STATE_TURNING_OFF:
                            Log.d("StudentLogin", "NFC turning OFF");
                            updateRfidButtonStatus("❌ NFC TURNING OFF", android.R.color.holo_red_dark);
                            break;
                        case NfcAdapter.STATE_TURNING_ON:
                            Log.d("StudentLogin", "NFC turning ON");
                            updateRfidButtonStatus("⏳ NFC TURNING ON", android.R.color.holo_orange_dark);
                            break;
                    }
                }
            }
        };

        // Register the receiver
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        registerReceiver(nfcStateReceiver, filter);

        setupClickListeners();
    }

    private void checkUsbConnection() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbManager != null) {
            // Check if any USB devices are connected
            isUsbConnected = !usbManager.getDeviceList().isEmpty();
            Log.d("StudentLogin", "USB connected: " + isUsbConnected);
        }
    }

    private boolean isDebuggingEnabled() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
    }

    private void updateRfidButtonStatus(String status, int backgroundColor) {
        runOnUiThread(() -> {
            binding.btnRfidLogin.setText(status);
            binding.btnRfidLogin.setBackgroundTintList(getColorStateList(backgroundColor));
        });
    }

    private void initializeNfc() {
        nfcInitializationAttempted = true;

        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(this);

            if (nfcAdapter == null) {
                Log.d("StudentLogin", "NFC is not available on this device");
                updateRfidButtonStatus("❌ NFC NOT AVAILABLE", android.R.color.holo_red_dark);
                showToast("NFC is not available on this device.");
                return;
            }

            if (!nfcAdapter.isEnabled()) {
                Log.d("StudentLogin", "NFC is disabled");
                updateRfidButtonStatus("❌ NFC DISABLED - ENABLE IN SETTINGS", android.R.color.holo_red_dark);
                showToast("Please enable NFC in settings to use RFID login.");
                return;
            }

            // Check for USB debugging and connection - consolidate warnings into single
            // message
            boolean hasUsbWarning = false;
            StringBuilder usbWarning = new StringBuilder();

            if (isDebuggingEnabled()) {
                Log.w("StudentLogin", "USB debugging detected - this may interfere with NFC functionality");
                usbWarning.append("USB debugging enabled");
                hasUsbWarning = true;
            }

            if (isUsbConnected) {
                Log.w("StudentLogin", "USB cable detected - this may interfere with NFC functionality");
                if (hasUsbWarning) {
                    usbWarning.append(" & USB cable connected");
                } else {
                    usbWarning.append("USB cable connected");
                    hasUsbWarning = true;
                }
            }

            if (hasUsbWarning) {
                updateRfidButtonStatus("⚠️ " + usbWarning.toString().toUpperCase(), android.R.color.holo_orange_dark);
                showToast("⚠️ " + usbWarning.toString()
                        + ". NFC may not work properly. For best performance, disable USB debugging and disconnect USB cable.");
            }

            // NFC is available and enabled, set up the functionality
            Log.d("StudentLogin", "NFC is available and enabled, setting up functionality");

            // Create pending intent with proper flags for different Android versions
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }

            pendingIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    flags);

            // Create intent filters for different tag types
            IntentFilter tag = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
            IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            try {
                ndef.addDataType("*/*");
            } catch (IntentFilter.MalformedMimeTypeException e) {
                Log.e("StudentLogin", "Error adding NDEF data type", e);
                throw new RuntimeException("fail", e);
            }
            IntentFilter tech = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);

            intentFilters = new IntentFilter[] { tag, ndef, tech };

            Log.d("StudentLogin", "NFC initialization completed successfully");

            if (!isUsbConnected && !isDebuggingEnabled()) {
                updateRfidButtonStatus("✅ RFID READY - TAP CARD TO LOGIN", android.R.color.holo_green_dark);
                showToast("✅ NFC is ready. Tap your RFID card to login.");
            } else {
                // Status already updated above with appropriate warnings
            }

        } catch (Exception e) {
            Log.e("StudentLogin", "Error initializing NFC", e);
            showToast("❌ Error initializing NFC functionality.");

            // Retry NFC initialization after a delay if it failed
            nfcRetryHandler.postDelayed(() -> {
                if (!isDestroyed()) {
                    Log.d("StudentLogin", "Retrying NFC initialization...");
                    initializeNfc();
                }
            }, 3000); // Retry after 3 seconds
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Re-check USB connection status
        checkUsbConnection();

        // USB debugging warning is now handled in initializeNfc() to avoid duplicate
        // messages

        // Re-check NFC state and enable foreground dispatch if available
        if (nfcAdapter != null && nfcAdapter.isEnabled() && pendingIntent != null && intentFilters != null) {
            try {
                nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, null);

                // USB warnings are now consolidated in initializeNfc() to avoid multiple
                // messages
            } catch (Exception e) {
                Log.e("StudentLogin", "Error enabling NFC foreground dispatch", e);
                showToast("❌ NFC setup failed. Please restart the app.");
            }
        } else {
            if (!nfcInitializationAttempted) {
                // Try to initialize NFC if it hasn't been attempted yet
                initializeNfc();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableForegroundDispatch(this);
                Log.d("StudentLogin", "NFC foreground dispatch disabled");
            } catch (Exception e) {
                Log.e("StudentLogin", "Error disabling NFC foreground dispatch", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up handlers and receivers
        if (nfcRetryHandler != null) {
            nfcRetryHandler.removeCallbacksAndMessages(null);
        }

        // Unregister the NFC state receiver
        if (nfcStateReceiver != null) {
            try {
                unregisterReceiver(nfcStateReceiver);
                Log.d("StudentLogin", "NFC state receiver unregistered");
            } catch (Exception e) {
                Log.e("StudentLogin", "Error unregistering NFC receiver", e);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) ||
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action) ||
                NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                processNfcTag(tag);
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

                // Query Firestore for student email using studentId
                android.util.Log.d("StudentLogin", "Starting Firestore query for studentId: " + studentId);
                db.collection("students").whereEqualTo("studentId", studentId).get()
                        .addOnCompleteListener(task -> {
                            android.util.Log.d("StudentLogin",
                                    "Firestore query completed. Success: " + task.isSuccessful());
                            if (task.isSuccessful() && task.getResult() != null) {
                                android.util.Log.d("StudentLogin", "Query result size: " + task.getResult().size());
                                if (!task.getResult().isEmpty()) {
                                    DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                                    String email = doc.getString("email");
                                    android.util.Log.d("StudentLogin", "Retrieved email: " + email);
                                    if (email != null && !email.isEmpty()) {
                                        android.util.Log.d("StudentLogin", "Found email for student: " + email);
                                        signInWithEmailAndPassword(email, password, studentId);
                                    } else {
                                        android.util.Log.d("StudentLogin", "Email is null or empty");
                                        showToast("Student email not found. Please contact administration.");
                                        resetLoginButton();
                                    }
                                } else {
                                    android.util.Log.d("StudentLogin",
                                            "No documents found for studentId: " + studentId);
                                    showToast("Student ID not found. Please contact administration.");
                                    resetLoginButton();
                                }
                            } else {
                                android.util.Log.d("StudentLogin", "Query failed or result is null");
                                showToast("Student ID not found. Please contact administration.");
                                resetLoginButton();
                            }
                        })
                        .addOnFailureListener(e -> {
                            android.util.Log.e("StudentLogin", "Error querying student: " + e.getMessage());
                            showToast("Error verifying student ID: " + e.getMessage());
                            resetLoginButton();
                        });
            }
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
                            android.util.Log.d("StudentLogin",
                                    "Passing studentId to checkIfStudentAndRedirect: " + studentId);
                            // Since students log in with student ID, check using the entered student ID
                            checkIfStudentAndRedirect(studentId);
                            // Store studentId in SharedPreferences for future sessions
                            sharedPreferences.edit().putString("studentId", studentId).apply();
                        } else {
                            android.util.Log.d("StudentLogin", "Sign in successful but currentUser is null");
                        }
                    } else {
                        // If sign in fails, try to create account if it's a new student
                        String errorMessage = task.getException() != null ? task.getException().getMessage()
                                : "Unknown error";
                        android.util.Log.e("StudentLogin", "Authentication failed: " + errorMessage);

                        // Check if this is a new student account that needs to be created
                        checkAndCreateStudentAccount(email, password, studentId);
                    }
                });
    }

    private void checkAndCreateStudentAccount(String email, String password, String studentId) {
        android.util.Log.d("StudentLogin", "Checking if student account needs to be created for: " + email);

        // Check if student exists in Firestore (already verified, but double-check)
        db.collection("students").whereEqualTo("studentId", studentId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        // Student exists in Firestore, check if Firebase Auth account exists
                        android.util.Log.d("StudentLogin",
                                "Student found in Firestore, checking Firebase Auth account");

                        mAuth.fetchSignInMethodsForEmail(email)
                                .addOnCompleteListener(fetchTask -> {
                                    if (fetchTask.isSuccessful()) {
                                        java.util.List<String> signInMethods = fetchTask.getResult().getSignInMethods();
                                        if (signInMethods != null && !signInMethods.isEmpty()) {
                                            // Account already exists, password is incorrect
                                            android.util.Log.d("StudentLogin",
                                                    "Firebase Auth account already exists, password is incorrect");
                                            showToast("Incorrect password. Please try again or reset your password.");
                                            resetLoginButton();
                                        } else {
                                            // Account doesn't exist, create it
                                            android.util.Log.d("StudentLogin",
                                                    "Firebase Auth account doesn't exist, creating account");
                                            createFirebaseAuthAccount(email, password, studentId);
                                        }
                                    } else {
                                        String fetchError = fetchTask.getException() != null
                                                ? fetchTask.getException().getMessage()
                                                : "Unknown error";
                                        android.util.Log.e("StudentLogin",
                                                "Error checking Firebase Auth account: " + fetchError);
                                        showToast("Error verifying account: " + fetchError);
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

    private void createFirebaseAuthAccount(String email, String password, String studentId) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(createTask -> {
                    if (createTask.isSuccessful()) {
                        android.util.Log.d("StudentLogin",
                                "Firebase Auth account created successfully");
                        // Now try to sign in again
                        signInWithEmailAndPassword(email, password, studentId);
                    } else {
                        // Check if account already exists (from enrollment verification)
                        if (createTask
                                .getException() instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                            android.util.Log.d("StudentLogin",
                                    "Account already exists, password is incorrect");
                            showToast("Incorrect password. Please try again or reset your password.");
                            resetLoginButton();
                        } else {
                            String createError = createTask.getException() != null
                                    ? createTask.getException().getMessage()
                                    : "Unknown error";
                            android.util.Log.e("StudentLogin",
                                    "Failed to create Firebase Auth account: " + createError);
                            showToast("Account setup failed: " + createError);
                            resetLoginButton();
                        }
                    }
                });
    }

    private void checkIfStudentAndRedirect(String identifier) {
        android.util.Log.d("StudentLogin", "Checking if user is student with identifier: " + identifier);

        // First try to find by studentId field
        android.util.Log.d("StudentLogin", "Querying Firestore for studentId: " + identifier);
        db.collection("students").whereEqualTo("studentId", identifier).get()
                .addOnCompleteListener(task -> {
                    android.util.Log.d("StudentLogin",
                            "Firestore query by studentId completed. Success: " + task.isSuccessful());
                    if (task.isSuccessful() && task.getResult() != null) {
                        android.util.Log.d("StudentLogin", "Query result size: " + task.getResult().size());
                        if (!task.getResult().isEmpty()) {
                            android.util.Log.d("StudentLogin",
                                    "User is a student (found by studentId), redirecting to main activity");
                            DocumentSnapshot document = task.getResult().getDocuments().get(0);
                            String section = document.getString("section");
                            // Store studentId, document ID, and section in SharedPreferences
                            sharedPreferences.edit().putString("studentId", identifier)
                                    .putString("studentDocId", document.getId())
                                    .putString("studentSection", section).apply();
                            // User is a student, redirect to main activity
                            redirectToMainActivity();
                        } else {
                            android.util.Log.d("StudentLogin", "No documents found by studentId, trying document ID");
                            // If not found by studentId, try to find by document ID (for existing sessions)
                            db.collection("students").document(identifier).get()
                                    .addOnCompleteListener(docTask -> {
                                        android.util.Log.d("StudentLogin",
                                                "Firestore document get by ID completed. Success: "
                                                        + docTask.isSuccessful());
                                        if (docTask.isSuccessful() && docTask.getResult() != null) {
                                            android.util.Log.d("StudentLogin",
                                                    "Document exists: " + docTask.getResult().exists());
                                            if (docTask.getResult().exists()) {
                                                android.util.Log.d("StudentLogin",
                                                        "User is a student (found by document ID), redirecting to main activity");
                                                // Get studentId from document and store it and document ID
                                                String studentIdFromDoc = docTask.getResult().getString("studentId");
                                                if (studentIdFromDoc != null) {
                                                    sharedPreferences.edit().putString("studentId", studentIdFromDoc)
                                                            .putString("studentDocId", identifier).apply();
                                                }
                                                // User is a student, redirect to main activity
                                                redirectToMainActivity();
                                            } else {
                                                android.util.Log.d("StudentLogin",
                                                        "User is not a student, signing out");
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
                                        android.util.Log.e("StudentLogin",
                                                "Error verifying student status by document ID: " + e.getMessage());
                                        showToast("Error verifying student status: " + e.getMessage());
                                        resetLoginButton();
                                    });
                        }
                    } else {
                        android.util.Log.d("StudentLogin", "Query by studentId failed or result is null");
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("StudentLogin",
                            "Error verifying student status by studentId: " + e.getMessage());
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

        // Query Firestore for student with this RFID tag
        db.collection("students").whereEqualTo("rfidTag", rfidId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        String studentId = document.getString("studentId");
                        String temporaryPassword = document.getString("temporaryPassword");
                        String section = document.getString("section");
                        android.util.Log.d("StudentLogin", "RFID document data: " + document.getData());
                        if (studentId != null && temporaryPassword != null) {
                            android.util.Log.d("StudentLogin",
                                    "RFID authentication successful for student: " + studentId);
                            // Sign in with Firebase Auth anonymously for RFID
                            android.util.Log.d("StudentLogin", "Signing in RFID user anonymously");
                            mAuth.signInAnonymously()
                                    .addOnCompleteListener(authTask -> {
                                        if (authTask.isSuccessful()) {
                                            android.util.Log.d("StudentLogin",
                                                    "RFID anonymous Firebase Auth sign in successful");
                                            android.util.Log.d("StudentLogin", "Storing studentId: " + studentId
                                                    + ", studentDocId: " + document.getId() + ", section: " + section);
                                            // Store student info and redirect
                                            sharedPreferences.edit().putString("studentId", studentId)
                                                    .putString("studentDocId", document.getId())
                                                    .putString("studentSection", section).apply();
                                            showToast("Login successful!");
                                            redirectToMainActivity();
                                        } else {
                                            android.util.Log.e("StudentLogin",
                                                    "RFID anonymous Firebase Auth sign in failed: "
                                                            + authTask.getException().getMessage());
                                            showToast("RFID authentication failed");
                                        }
                                    });
                        } else {
                            android.util.Log.d("StudentLogin",
                                    "studentId or temporaryPassword is null in RFID document");
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

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
