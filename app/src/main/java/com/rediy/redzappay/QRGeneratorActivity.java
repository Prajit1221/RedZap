package com.rediy.redzappay;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class QRGeneratorActivity extends AppCompatActivity {

    private static final String TAG = "QRGeneratorActivity";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private EditText editTextAmount, editTextUpiIdManual;
    private Spinner spinnerUpiIds;
    private Button buttonGenerateQr, buttonShareQr;
    private ImageView imageViewQrCode;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private List<String> upiIdList = new ArrayList<>();
    private String selectedUpiId;
    private String amount;
    RelativeLayout relativeLayout;

    private static class ActivityState {
        Bitmap qrCodeBitmapWithText;
        String currentTransactionId;
        boolean isTransactionSaving;
        String enteredAmount;
        String enteredUpiId;
        int selectedUpiIdIndex;
        String cacheFilePath;
        String upiIdUsedForQr;
    }

    private static ActivityState activityState = new ActivityState();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_generator);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        relativeLayout = findViewById(R.id.layout);
        editTextAmount = findViewById(R.id.editTextAmount);
        editTextUpiIdManual = findViewById(R.id.editTextUpiIdManual);
        spinnerUpiIds = findViewById(R.id.spinnerUpiIds);
        buttonGenerateQr = findViewById(R.id.buttonGenerateQr);
        buttonShareQr = findViewById(R.id.buttonShareQr);
        imageViewQrCode = findViewById(R.id.imageViewQrCode);

        buttonShareQr.setVisibility(View.GONE);
        imageViewQrCode.setVisibility(View.GONE);

        if (!checkPlayServices()) {
            showToast("Google Play Services is required.");
            buttonGenerateQr.setEnabled(false);
            buttonShareQr.setEnabled(false);
            return;
        }

        restoreState();
        loadUpiIds();

        buttonGenerateQr.setOnClickListener(v -> handleGenerateQrCode());
        buttonShareQr.setOnClickListener(v -> shareQRCode());
    }

    private void restoreState() {
        if (activityState.qrCodeBitmapWithText != null && !activityState.qrCodeBitmapWithText.isRecycled()) {
            imageViewQrCode.setImageBitmap(activityState.qrCodeBitmapWithText);
            imageViewQrCode.setVisibility(View.VISIBLE);
            buttonShareQr.setVisibility(View.VISIBLE);
        } else {
            imageViewQrCode.setVisibility(View.GONE);
            buttonShareQr.setVisibility(View.GONE);
        }
        editTextAmount.setText(activityState.enteredAmount);
        editTextUpiIdManual.setText(activityState.enteredUpiId);

        if (activityState.isTransactionSaving) {
            disableInputAndButtons();
        } else {
            enableInputAndButtons();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            if (activityState.qrCodeBitmapWithText != null && !activityState.qrCodeBitmapWithText.isRecycled()) {
                activityState.qrCodeBitmapWithText.recycle();
            }
            activityState.qrCodeBitmapWithText = null;
            activityState.currentTransactionId = null;
            activityState.isTransactionSaving = false;
            activityState.enteredAmount = null;
            activityState.enteredUpiId = null;
            activityState.selectedUpiIdIndex = -1;
            activityState.upiIdUsedForQr = null;
            cleanupCacheFile(new File(activityState.cacheFilePath));
            activityState.cacheFilePath = null;
        }
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                showToast("Google Play Services is not supported.");
            }
            return false;
        }
        return true;
    }

    private void loadUpiIds() {
        if (mAuth.getCurrentUser() == null) {
            showToast("Error: Not logged in.");
            return;
        }
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    upiIdList.clear();
                    spinnerUpiIds.setAdapter(null);

                    if (document.exists() && document.contains("upiIds")) {
                        List<String> fetchedUpiIds = (List<String>) document.get("upiIds");
                        if (fetchedUpiIds != null && !fetchedUpiIds.isEmpty()) {
                            upiIdList.addAll(fetchedUpiIds);
                            ArrayAdapter<String> adapter = new ArrayAdapter<>(QRGeneratorActivity.this,
                                    android.R.layout.simple_spinner_item, upiIdList);
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            spinnerUpiIds.setAdapter(adapter);

                            if (activityState.selectedUpiIdIndex >= 0 && activityState.selectedUpiIdIndex < upiIdList.size()) {
                                spinnerUpiIds.setSelection(activityState.selectedUpiIdIndex);
                            } else if (!upiIdList.isEmpty()) {
                                spinnerUpiIds.setSelection(0);
                                activityState.selectedUpiIdIndex = 0;
                            } else {
                                activityState.selectedUpiIdIndex = -1;
                            }
                        } else {
                            showToast("No UPI IDs found.");
                            activityState.selectedUpiIdIndex = -1;
                        }
                    } else {
                        showToast("No UPI IDs found. Add them in your profile.");
                        activityState.selectedUpiIdIndex = -1;
                    }
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to load UPI IDs: " + e.getMessage());
                    upiIdList.clear();
                    spinnerUpiIds.setAdapter(null);
                    activityState.selectedUpiIdIndex = -1;
                });
    }

    private void handleGenerateQrCode() {
        String currentAmountInput = editTextAmount.getText().toString().trim();
        String manualUpiId = editTextUpiIdManual.getText().toString().trim();
        selectedUpiId = "";
        int selectedIndex = spinnerUpiIds.getSelectedItemPosition();

        if (!TextUtils.isEmpty(manualUpiId)) {
            selectedUpiId = manualUpiId;
        } else if (selectedIndex != android.widget.AdapterView.INVALID_POSITION && upiIdList != null && selectedIndex >= 0 && selectedIndex < upiIdList.size()) {
            selectedUpiId = upiIdList.get(selectedIndex);
        } else if (spinnerUpiIds.getSelectedItem() != null) {
            selectedUpiId = spinnerUpiIds.getSelectedItem().toString();
        }

        if (!isValidUpiId(selectedUpiId)) {
            showToast("Please enter a valid UPI ID.");
            return;
        }

        if (TextUtils.isEmpty(currentAmountInput)) {
            showToast("Please enter the amount.");
            return;
        }
        try {
            double amountValue = Double.parseDouble(currentAmountInput);
            if (amountValue <= 0) {
                showToast("Amount must be positive.");
                return;
            }
            amount = String.format(Locale.US, "%.2f", amountValue);
            editTextAmount.setText(amount);

            activityState.enteredAmount = amount;
            activityState.enteredUpiId = manualUpiId;
            activityState.selectedUpiIdIndex = selectedIndex;
            activityState.upiIdUsedForQr = selectedUpiId;

            storeTransactionDetails(selectedUpiId, amount);

        } catch (NumberFormatException e) {
            showToast("Invalid amount.");
        }
    }

    private void storeTransactionDetails(String upiId, String amount) {
        if (activityState.isTransactionSaving) return;
        activityState.isTransactionSaving = true;
        disableInputAndButtons();

        if (mAuth.getCurrentUser() == null) {
            showToast("Error: Not logged in.");
            activityState.isTransactionSaving = false;
            enableInputAndButtons();
            return;
        }
        String userId = mAuth.getCurrentUser().getUid();
        String newTransactionId = UUID.randomUUID().toString();
        activityState.currentTransactionId = newTransactionId;

        Map<String, Object> transaction = new HashMap<>();
        transaction.put("transactionId", newTransactionId);
        transaction.put("userId", userId);
        transaction.put("upiId", upiId);
        transaction.put("amount", amount);
        transaction.put("status", "PENDING");
        transaction.put("timestamp", FieldValue.serverTimestamp());

        db.collection("transactions")
                .document(newTransactionId)
                .set(transaction)
                .addOnSuccessListener(aVoid -> {
                    showToast("Transaction details saved.");
                    generateQRCodeWithText(upiId, amount);
                    activityState.isTransactionSaving = false;
                    enableInputAndButtons();
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to save transaction details: " + e.getMessage());
                    activityState.isTransactionSaving = false;
                    activityState.currentTransactionId = null;
                    enableInputAndButtons();
                    clearQrStateAndHide();
                });
    }

    private void generateQRCodeWithText(final String upiId, final String amount) {
        // Construct the UPI URI
        Uri.Builder upiUriBuilder = new Uri.Builder()
                .scheme("upi")
                .authority("pay")
                .appendQueryParameter("pa", upiId)
                .appendQueryParameter("am", amount)
                .appendQueryParameter("cu", "INR");

        String qrData = upiUriBuilder.build().toString();

        try {
            Bitmap qrCodeBitmap = generateQRCode(qrData);
            Bitmap qrBitmapWithText = addTextToQRCode(qrCodeBitmap, upiId, amount);

            if (activityState.qrCodeBitmapWithText != null && !activityState.qrCodeBitmapWithText.isRecycled()) {
                activityState.qrCodeBitmapWithText.recycle();
            }
            activityState.qrCodeBitmapWithText = qrBitmapWithText;

            imageViewQrCode.setImageBitmap(activityState.qrCodeBitmapWithText);
            imageViewQrCode.setVisibility(View.VISIBLE);
            buttonShareQr.setVisibility(View.VISIBLE);
            buttonShareQr.setEnabled(true);

        } catch (WriterException e) {
            showToast("Error generating QR code.");
            clearQrStateAndHide();
        }
    }

    private void clearQrStateAndHide() {
        if (activityState.qrCodeBitmapWithText != null && !activityState.qrCodeBitmapWithText.isRecycled()) {
            activityState.qrCodeBitmapWithText.recycle();
        }
        activityState.qrCodeBitmapWithText = null;
        runOnUiThread(() -> {
            imageViewQrCode.setVisibility(View.GONE);
            buttonShareQr.setVisibility(View.GONE);
            buttonShareQr.setEnabled(false);
        });
    }

    private Bitmap generateQRCode(String data) throws WriterException {
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        BitMatrix bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 800, 800);
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        return barcodeEncoder.createBitmap(bitMatrix);
    }

    private Bitmap addTextToQRCode(Bitmap qrCodeBitmap, String upiId, String amount) {
        int width = qrCodeBitmap.getWidth();
        int height = qrCodeBitmap.getHeight();
        float density = getResources().getDisplayMetrics().density;
        int textHeightPx = (int) (16 * getResources().getDisplayMetrics().scaledDensity);
        int appNameHeightPx = (int) (30 * density);
        int paddingPx = (int) (8 * density);
        float appNameTextSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24, getResources().getDisplayMetrics());
        float detailsTextSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, getResources().getDisplayMetrics());
        int totalHeight = height + appNameHeightPx + (2 * textHeightPx) + (4 * paddingPx);
        Bitmap combinedBitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(combinedBitmap);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        canvas.drawColor(android.graphics.Color.WHITE);
        Typeface font = null;
        try {
            font = ResourcesCompat.getFont(this, R.font.autour_one);
        } catch (Exception e) {
        }
        paint.setTypeface((font != null) ? Typeface.create(font, Typeface.BOLD) : Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setColor(android.graphics.Color.RED);
        paint.setTextSize(appNameTextSizePx);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        float appNameCenterY = paddingPx + (appNameHeightPx / 2f);
        float appNameBaseLine = appNameCenterY - (paint.descent() + paint.ascent()) / 2f;
        canvas.drawText("RedZap", width / 2f, appNameBaseLine, paint);
        float qrCodeY = appNameBaseLine + paint.descent() + paddingPx;
        canvas.drawBitmap(qrCodeBitmap, 0, qrCodeY, null);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setColor(android.graphics.Color.BLACK);
        paint.setTextSize(detailsTextSizePx);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        float upiIdCenterY = qrCodeY + height + paddingPx + (textHeightPx / 2f);
        float upiIdBaseLine = upiIdCenterY - (paint.descent() + paint.ascent()) / 2f;
        canvas.drawText("UPI ID: " + upiId, width / 2f, upiIdBaseLine, paint);
        if (!TextUtils.isEmpty(amount)) {
            float amountCenterY = upiIdBaseLine + paint.descent() - paint.ascent() + paddingPx + (textHeightPx / 2f);
            float amountBaseLine = amountCenterY - (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText("Amount: ₹" + amount, width / 2f, amountBaseLine, paint);
        }
        return combinedBitmap;
    }

    private void shareQRCode() {
        if (activityState.qrCodeBitmapWithText == null || activityState.qrCodeBitmapWithText.isRecycled()) {
            showToast("No valid QR code to share.");
            return;
        }

        String upiId = activityState.upiIdUsedForQr;
        String amountToShare = activityState.enteredAmount;
        String transactionId = activityState.currentTransactionId;

        String upiLink = null;
        if (!TextUtils.isEmpty(upiId) && !TextUtils.isEmpty(amountToShare)) {
            if (!isValidUpiId(upiId)) { // Validate UPI ID before proceeding
                showToast("Invalid UPI ID. Cannot share.");
                return;
            }
            Uri.Builder upiUriBuilder = new Uri.Builder()
                    .scheme("upi")
                    .authority("pay")
                    .appendQueryParameter("pa", upiId)
                    .appendQueryParameter("am", amountToShare)
                    .appendQueryParameter("cu", "INR");
            upiLink = upiUriBuilder.build().toString();
        } else {
            showToast("Error: Missing UPI ID or Amount.");
            return;
        }

        File cacheFile = null;
        try {
            File cacheDir = getExternalCacheDir();
            if (cacheDir == null) throw new IOException("External cache dir null");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) throw new IOException("Failed to create cache dir");

            if (activityState.cacheFilePath != null) {
                cleanupCacheFile(new File(activityState.cacheFilePath));
                activityState.cacheFilePath = null;
            }
            cacheFile = new File(cacheDir, "redzap_qr_" + System.currentTimeMillis() + ".png");

            try (FileOutputStream out = new FileOutputStream(cacheFile)) {
                activityState.qrCodeBitmapWithText.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            if (!cacheFile.exists() || cacheFile.length() == 0)
                throw new IOException("Cache file missing or empty");
            activityState.cacheFilePath = cacheFile.getAbsolutePath();


            Uri contentUri;
            try {
                String authority = getApplicationContext().getPackageName() + ".fileprovider";
                contentUri = FileProvider.getUriForFile(this, authority, cacheFile);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("FileProvider error: " + e.getMessage());
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String shareSubject = "Payment Request via RedZap: ₹" + amountToShare;
            String shareBody = "Please pay ₹" + amountToShare + " using the attached QR code.";
            if (upiLink != null) {
                shareBody += "\n\nOr click to pay:\n" + upiLink;
            } else {
                shareBody += "\n(Payment link could not be generated).";
            }
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);

            Intent chooserIntent = Intent.createChooser(shareIntent, "Share Payment Details Via...");
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooserIntent);

        } catch (Exception e) {
            showToast("Failed to share: " + e.getMessage());
            if (cacheFile != null) cleanupCacheFile(cacheFile);
            activityState.cacheFilePath = null;
        }
    }

    private void cleanupCacheFile(File cacheFile) {
        if (cacheFile != null && cacheFile.exists()) {
            if (cacheFile.delete()) {
            } else {
            }
        }
    }

    private void showToast(final String message) {
        runOnUiThread(() -> Toast.makeText(QRGeneratorActivity.this, message, Toast.LENGTH_LONG).show());
    }

    private void clearInputFieldsAndEnableUI() {
        runOnUiThread(() -> {
            editTextAmount.getText().clear();
            editTextUpiIdManual.getText().clear();
            if (upiIdList != null && !upiIdList.isEmpty()) {
                spinnerUpiIds.setSelection(0);
            }
            clearQrStateAndHide();
            activityState.currentTransactionId = null;
            activityState.upiIdUsedForQr = null;

            if (activityState.cacheFilePath != null) {
                cleanupCacheFile(new File(activityState.cacheFilePath));
                activityState.cacheFilePath = null;
            }
            enableInputAndButtons();
        });
    }

    private void disableInputAndButtons() {
        runOnUiThread(() -> {
            editTextAmount.setEnabled(false);
            editTextUpiIdManual.setEnabled(false);
            spinnerUpiIds.setEnabled(false);
            // buttonGenerateQr.setEnabled(false);  // Removed this line
            buttonShareQr.setEnabled(false);
        });
    }

    private void enableInputAndButtons() {
        runOnUiThread(() -> {
            editTextAmount.setEnabled(true);
            editTextUpiIdManual.setEnabled(true);
            spinnerUpiIds.setEnabled(true);
            buttonGenerateQr.setEnabled(true);
            buttonShareQr.setEnabled(imageViewQrCode.getVisibility() == View.VISIBLE);
        });
    }

    private boolean isValidUpiId(String upiId) {
        if (TextUtils.isEmpty(upiId)) {
            return false;
        }
        // Basic UPI ID validation regex
        String upiPattern = "^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$";
        return Pattern.matches(upiPattern, upiId);
    }
}

