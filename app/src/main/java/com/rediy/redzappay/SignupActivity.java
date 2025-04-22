package com.rediy.redzappay;

import androidx.appcompat.app.AppCompatActivity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class SignupActivity extends AppCompatActivity {

    private EditText editTextName, editTextDOB, editTextMobile, editTextEmail, editTextPassword, editTextUpiId;
    private Button buttonSignup;
    private TextView textViewLoginLink;
    private ProgressBar progressBar;
    private Calendar calendar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private static final String DATE_FORMAT = "dd/MM/yyyy";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        calendar = Calendar.getInstance();

        editTextName = findViewById(R.id.editTextName);
        editTextDOB = findViewById(R.id.editTextDOB);
        editTextMobile = findViewById(R.id.editTextMobile);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextUpiId = findViewById(R.id.editTextUpiId);
        buttonSignup = findViewById(R.id.buttonSignup);
        textViewLoginLink = findViewById(R.id.textViewLoginLink);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        final DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDOBEditText();
        };

        editTextDOB.setOnClickListener(v -> {
            new DatePickerDialog(SignupActivity.this,
                    dateSetListener,
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        buttonSignup.setOnClickListener(v -> {
            String name = editTextName.getText().toString().trim();
            String dob = editTextDOB.getText().toString().trim();
            String mobile = editTextMobile.getText().toString().trim();
            String email = editTextEmail.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();
            String upiIdsText = editTextUpiId.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                editTextName.setError("Name is required.");
                editTextName.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(dob)) {
                editTextDOB.setError("Date of Birth is required.");
                editTextDOB.requestFocus();
                return;
            }

            if (!isValidDate(dob)) {
                editTextDOB.setError("Invalid date format. Use dd/MM/yyyy");
                editTextDOB.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(mobile)) {
                editTextMobile.setError("Mobile Number is required.");
                editTextMobile.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(email)) {
                editTextEmail.setError("Email is required.");
                editTextEmail.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(password)) {
                editTextPassword.setError("Password is required.");
                editTextPassword.requestFocus();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            createUserWithEmail(name, dob, mobile, email, password, upiIdsText);
        });

        textViewLoginLink.setOnClickListener(v -> {
            finish();
        });
    }

    private void createUserWithEmail(String name, String dob, String mobile, String email, String password, String upiIdsText) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            List<String> upiIds = Arrays.asList(upiIdsText.split(","));
                            List<String> filteredUpiIds = new ArrayList<>();
                            for (String upiId : upiIds) {
                                if (!upiId.trim().isEmpty()) {
                                    filteredUpiIds.add(upiId.trim());
                                }
                            }

                            String upiRegex = "^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$";
                            for (String upi : filteredUpiIds) {
                                if (!Pattern.matches(upiRegex, upi)) {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(SignupActivity.this, "Invalid UPI ID format: " + upi,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                            }
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("name", name);
                            userData.put("dob", dob);
                            userData.put("mobile", mobile);
                            userData.put("email", email);
                            userData.put("upiIds", filteredUpiIds);

                            db.collection("users").document(user.getUid())
                                    .set(userData)
                                    .addOnSuccessListener(aVoid -> {
                                        progressBar.setVisibility(View.GONE);
                                        Toast.makeText(SignupActivity.this, "Registration successful.", Toast.LENGTH_SHORT).show();

                                        Intent intent = new Intent(SignupActivity.this, HomeActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();

                                    })
                                    .addOnFailureListener(e -> {
                                        progressBar.setVisibility(View.GONE);
                                        Toast.makeText(SignupActivity.this, "Failed to save user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                                        startActivity(intent);
                                        finish();

                                    });
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(SignupActivity.this, "Registration failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
    }

    private void updateDOBEditText() {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
        editTextDOB.setText(sdf.format(calendar.getTime()));
    }

    private boolean isValidDate(String date) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
            sdf.setLenient(false);
            sdf.parse(date);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
}
