package com.rediy.redzappay;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private EditText editTextName, editTextDOB, editTextMobile, editTextEmail, editTextUpiIds;
    private Button buttonSaveProfile;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        editTextName = findViewById(R.id.editTextName);
        editTextDOB = findViewById(R.id.editTextDOB);
        editTextMobile = findViewById(R.id.editTextMobile);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextUpiIds = findViewById(R.id.editTextUpiIds);
        buttonSaveProfile = findViewById(R.id.buttonSaveProfile);

        loadUserProfileData();

        buttonSaveProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserProfile();
            }
        });
    }

    private void loadUserProfileData() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        editTextName.setText(document.getString("name"));
                        editTextDOB.setText(document.getString("dob"));
                        editTextMobile.setText(document.getString("mobile"));
                        editTextEmail.setText(document.getString("email"));
                        List<String> upiIdsList = (List<String>) document.get("upiIds");
                        String upiIds = upiIdsList != null ? String.join(", ", upiIdsList) : "";
                        editTextUpiIds.setText(upiIds);
                    } else {
                        Toast.makeText(EditProfileActivity.this, "Could not load profile information for editing.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(EditProfileActivity.this, "Failed to load profile information for editing.", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveUserProfile() {
        String name = editTextName.getText().toString().trim();
        String dob = editTextDOB.getText().toString().trim();
        String mobile = editTextMobile.getText().toString().trim();
        String email = editTextEmail.getText().toString().trim();
        String upiIdsText = editTextUpiIds.getText().toString().trim();
        List<String> upiIdsList = Arrays.asList(upiIdsText.split(",\\s*"));

        String userId = mAuth.getCurrentUser().getUid();
        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("dob", dob);
        user.put("mobile", mobile);
        user.put("email", email);
        user.put("upiIds", upiIdsList);

        db.collection("users").document(userId)
                .update(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(EditProfileActivity.this, "Profile updated successfully.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(EditProfileActivity.this, "Failed to update profile.", Toast.LENGTH_SHORT).show();
                });
    }
}

