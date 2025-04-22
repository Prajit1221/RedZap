package com.rediy.redzappay;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private TextView textViewName, textViewDOB, textViewMobile, textViewEmail, textViewUpiIds;
    private Button buttonEditProfile;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        textViewName = findViewById(R.id.textViewName);
        textViewDOB = findViewById(R.id.textViewDOB);
        textViewMobile = findViewById(R.id.textViewMobile);
        textViewEmail = findViewById(R.id.textViewEmail);
        textViewUpiIds = findViewById(R.id.textViewUpiIds);
        buttonEditProfile = findViewById(R.id.buttonEditProfile);

        loadUserProfile();

        buttonEditProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProfileActivity.this, EditProfileActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserProfile();
    }

    private void loadUserProfile() {
        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            String name = document.getString("name");
                            String dob = document.getString("dob");
                            String mobile = document.getString("mobile");
                            String email = document.getString("email");
                            List<String> upiIdsList = (List<String>) document.get("upiIds");
                            String upiIds = upiIdsList != null ? String.join(", ", upiIdsList) : "No UPI IDs";

                            textViewName.setText(name);
                            textViewDOB.setText(dob);
                            textViewMobile.setText(mobile);
                            textViewEmail.setText(email);
                            textViewUpiIds.setText(upiIds);
                        } else {
                            Toast.makeText(ProfileActivity.this, "Could not load profile information.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(ProfileActivity.this, "Failed to load profile information.", Toast.LENGTH_SHORT).show();
                    });
        } else {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
        }
    }
}
