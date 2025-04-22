package com.rediy.redzappay;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class Transaction {
    private String transactionId;
    private String date;
    private String time;
    private double amount;
    private String upiId;

    public Transaction(String transactionId, String date, String time, double amount, String upiId) {
        this.transactionId = transactionId;
        this.date = date;
        this.time = time;
        this.amount = amount;
        this.upiId = upiId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public double getAmount() {
        return amount;
    }

    public String getUpiId() {
        return upiId;
    }
}

class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<Transaction> transactionList;

    public TransactionAdapter(List<Transaction> transactionList) {
        this.transactionList = transactionList;
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = transactionList.get(position);

        holder.dateTextView.setText(transaction.getDate());
        holder.timeTextView.setText(transaction.getTime());
        holder.amountTextView.setText(String.format(Locale.getDefault(), "₹%.2f", transaction.getAmount()));
        holder.upiIdTextView.setText("UPI ID: " + transaction.getUpiId());
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView dateTextView;
        TextView timeTextView;
        TextView amountTextView;
        TextView upiIdTextView;

        public TransactionViewHolder(View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            amountTextView = itemView.findViewById(R.id.amountTextView);
            upiIdTextView = itemView.findViewById(R.id.upiIdTextView);
        }
    }
}

public class TransactionHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TransactionAdapter adapter;
    private List<Transaction> transactionList;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Button fromDateButton, toDateButton, filterButton;
    private TextView fromDateTextView, toDateTextView;
    private Calendar calendar;
    private Date fromDate, toDate;
    private SimpleDateFormat dateFormatter;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_history);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        recyclerView = findViewById(R.id.recyclerViewTransactionHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        transactionList = new ArrayList<>();
        adapter = new TransactionAdapter(transactionList);
        recyclerView.setAdapter(adapter);

        fromDateButton = findViewById(R.id.fromDateButton);
        toDateButton = findViewById(R.id.toDateButton);
        filterButton = findViewById(R.id.filterButton);
        fromDateTextView = findViewById(R.id.fromDateTextView);
        toDateTextView = findViewById(R.id.toDateTextView);

        calendar = Calendar.getInstance();
        dateFormatter = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

        loadTransactions(null, null);

        fromDateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePickerDialog(true);
            }
        });

        toDateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePickerDialog(false);
            }
        });

        filterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fromDate != null && toDate != null) {
                    loadTransactions(fromDate, toDate);
                } else {
                    Toast.makeText(TransactionHistoryActivity.this, "Please select both 'From' and 'To' dates to filter.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showDatePickerDialog(final boolean isFromDate) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        Calendar newCalendar = Calendar.getInstance();
                        newCalendar.set(year, monthOfYear, dayOfMonth);
                        Date selectedDate = newCalendar.getTime();
                        if (isFromDate) {
                            fromDate = selectedDate;
                            fromDateTextView.setText("From: " + dateFormatter.format(fromDate));
                        } else {
                            toDate = selectedDate;
                            toDateTextView.setText("To: " + dateFormatter.format(toDate));
                        }
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void loadTransactions(Date startDateFilter, Date endDateFilter) {
        String userId = mAuth.getCurrentUser().getUid();
        if (userId == null) {
            Toast.makeText(TransactionHistoryActivity.this, "User not logged in.", Toast.LENGTH_LONG).show();
            return;
        }

        Query query = db.collection("transactions")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        if (startDateFilter != null && endDateFilter != null) {
            Calendar startOfDay = Calendar.getInstance();
            startOfDay.setTime(startDateFilter);
            startOfDay.set(Calendar.HOUR_OF_DAY, 0);
            startOfDay.set(Calendar.MINUTE, 0);
            startOfDay.set(Calendar.SECOND, 0);
            startOfDay.set(Calendar.MILLISECOND, 0);
            Date startDate = startOfDay.getTime();

            Calendar endOfDay = Calendar.getInstance();
            endOfDay.setTime(endDateFilter);
            endOfDay.set(Calendar.HOUR_OF_DAY, 23);
            endOfDay.set(Calendar.MINUTE, 59);
            endOfDay.set(Calendar.SECOND, 59);
            endOfDay.set(Calendar.MILLISECOND, 999);
            Date endDate = endOfDay.getTime();

            query = query.whereGreaterThanOrEqualTo("timestamp", startDate)
                    .whereLessThanOrEqualTo("timestamp", endDate);
        }

        query.get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        transactionList.clear();
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                            SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());

                            for (DocumentSnapshot document : querySnapshot) {
                                Map<String, Object> data = document.getData();
                                if (data != null) {
                                    String transactionId = document.getId();
                                    String upiId = (data.get("upiId") != null) ? data.get("upiId").toString() : "N/A";
                                    Double amount = 0.0;
                                    if (data.get("amount") instanceof Number) {
                                        amount = ((Number) data.get("amount")).doubleValue();
                                    } else if (data.get("amount") instanceof String) {
                                        try {
                                            amount = Double.parseDouble((String) data.get("amount"));
                                        } catch (NumberFormatException e) {
                                        }
                                    }
                                    com.google.firebase.Timestamp firebaseTimestamp = (com.google.firebase.Timestamp) data.get("timestamp");
                                    Date timestamp = null;
                                    String formattedDate = "N/A";
                                    String formattedTime = "N/A";

                                    if (firebaseTimestamp != null) {
                                        timestamp = firebaseTimestamp.toDate();
                                        formattedDate = dateFormatter.format(timestamp);
                                        formattedTime = timeFormatter.format(timestamp);

                                        if ((startDateFilter == null && endDateFilter == null) ||
                                                (startDateFilter != null && endDateFilter != null && !timestamp.before(startDateFilter) && !timestamp.after(endDateFilter)) ||
                                                (startDateFilter != null && endDateFilter == null && !timestamp.before(startDateFilter)) ||
                                                (startDateFilter == null && endDateFilter != null && !timestamp.after(endDateFilter))) {
                                            Transaction transaction = new Transaction(transactionId, formattedDate, formattedTime, amount, upiId);
                                            transactionList.add(transaction);
                                        }
                                    }
                                }
                            }
                            adapter.notifyDataSetChanged();
                            if (transactionList.isEmpty() && startDateFilter != null && endDateFilter != null) {
                                Toast.makeText(this, "No transactions found for the selected date range.", Toast.LENGTH_SHORT).show();
                            } else if (transactionList.isEmpty() && startDateFilter == null && endDateFilter == null) {
                                Toast.makeText(this, "No transactions found.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(TransactionHistoryActivity.this, "No transactions found.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(TransactionHistoryActivity.this, "Error loading transactions: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}