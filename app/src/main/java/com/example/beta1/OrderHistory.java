package com.example.beta1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * @author Harel Navon harelnavon2710@gmail.com
 * @version 1.0
 * @since 8/3/2023
 * This Activity is designed to show the user its order history.
 */

public class OrderHistory extends AppCompatActivity {
    String currUserID;
    FirebaseDatabase fbDB;
    ListView listView;
    ArrayList<HashMap<String, String>> orderHistoryDataList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);
        listView = findViewById(R.id.listview3);

        Intent gi = getIntent();
        currUserID = gi.getStringExtra("UID");

        fbDB = FirebaseDatabase.getInstance();
        VerifyDateOfOrders();
    }


    /**
     * Method used to iterate through the Orders Branch of the current User in the database, and
     * update the completion status of each order according to the current date.
     */
    public void VerifyDateOfOrders() {
        DatabaseReference userOrders = fbDB.getReference("Users").child(currUserID).child("Orders");
        userOrders.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
                SimpleDateFormat sdf2 = new SimpleDateFormat("HH:mm", Locale.getDefault());
                for (DataSnapshot orderSnap : snapshot.getChildren()) {
                    Order order = orderSnap.getValue(Order.class);
                    String currentDate = sdf.format(new Date());
                    String parkAdDateStr = order.getParkDate();
                    DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                            .appendPattern("d/M/yyyy")
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                            .toFormatter();
                    try {
                        LocalDate current = LocalDate.parse(currentDate, formatter);
                        LocalDate parkAdDate = LocalDate.parse(parkAdDateStr, formatter);
                        if (current.isAfter(parkAdDate)) {
                            UpdateOrderCompleted(orderSnap.getKey()); //OrderDate has passed,hence its completed
                        } else if (current.toString().equals(parkAdDate.toString())) {
                            long currentTimeMillis = System.currentTimeMillis();
                            Date current2 = new Date(currentTimeMillis);
                            String currentHour = sdf2.format(current2);
                            if (!Services.isFirstTimeBeforeSecond(currentHour, order.getBeginHour())) {
                                if (!Services.isHourBetween(currentHour, order.getBeginHour(), order.getEndHour())) {
                                    UpdateOrderCompleted(orderSnap.getKey()); //OrderHour has passed,hence its completed
                                }
                            }
                        }
                    } catch (Error e) {
                        e.printStackTrace();
                    }
                }
                readOrderHistory();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }


    /**
     * SubMethod for the VerifyDateOfOrders Method. Used to update the completion status of a given
     * order to 'completed'.
     *
     * @param OrderID: The KeyID in the database for the completed order.
     */
    public void UpdateOrderCompleted(String OrderID) {
        DatabaseReference finishedOrder = fbDB.getReference("Users").child(currUserID).child("Orders").child(OrderID);
        finishedOrder.child("complete").setValue(true);
        finishedOrder.child("active").setValue(false);
        DatabaseReference orderBranch = fbDB.getReference("Orders").child(OrderID);
        orderBranch.setValue(null);
    }


    /**
     * Method used to iterate through the now updated Orders Branch for the current user in the
     * database, and populate the ListView with the completed orders.
     */
    public void readOrderHistory() {
        DatabaseReference userAds = fbDB.getReference("Users").child(currUserID).child("Orders");
        userAds.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot snapshot1 : snapshot.getChildren()) {
                    Order order = snapshot1.getValue(Order.class);
                    if (order.isComplete() || order.isCanceled()) {
                        String sellerId = order.getSellerId();
                        DatabaseReference sellerRef = fbDB.getReference("Users").child(sellerId);
                        sellerRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                User seller = snapshot.getValue(User.class);
                                saveStringToSharedPref("seller", seller.getName());
                                createHashMap4Order(order);
                                CustomOrderListAdapter adapter = new CustomOrderListAdapter(orderHistoryDataList);
                                listView.setAdapter(adapter);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {

                            }
                        });

                    }
                }
                if (orderHistoryDataList.size()==0){
                    String[] listString = new String[]{"Nothing to see here!"};
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(OrderHistory.this, android.R.layout.simple_list_item_1, listString);
                    listView.setAdapter(adapter);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        deleteSharedPref();
    }

    /**
     * SubMethod for the readOrderHistory Method. Used to create a HashMap for each completed
     * order and add it to the orderHistoryDataList.
     *
     * @param order: The Order Object that was read from the database.
     */
    public void createHashMap4Order(Order order) {
        HashMap<String, String> data = new HashMap<>();
        SharedPreferences sharedPreferences = getSharedPreferences("my_shared_prefs", MODE_PRIVATE);
        String sellerName = sharedPreferences.getString("seller", null);
        data.put("seller", sellerName);
        data.put("date", order.getParkDate());
        data.put("begin", order.getBeginHour());
        data.put("end", order.getEndHour());
        data.put("address", order.getParkAddress());
        data.put("price", String.valueOf(order.getPrice()));
        if (order.isComplete()) data.put("status", "Complete");
        else data.put("status", "Canceled");
        data.put("confirm", order.getConfirmDate());
        orderHistoryDataList.add(data);
    }


    /**
     * SubMethod for readOrderHistory Method. Used to save information about an Order Object for the
     * orderHistoryDataList.
     *
     * @param key:   The key of the information to be saved.
     * @param value: The value of the information to be saved.
     */
    public void saveStringToSharedPref(String key, String value) {
        SharedPreferences sharedPreferences = getSharedPreferences("my_shared_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }


    /**
     * SubMethod for readOrderHistory Method. Used to delete the SharedPrefs file created for saving
     * Order information,in order to clear up space.
     */
    public void deleteSharedPref() {
        SharedPreferences sharedPreferences = getSharedPreferences("my_shared_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        File sharedPreferencesFile = new File(getApplicationInfo().dataDir + "/shared_prefs/my_shared_prefs.xml");
        sharedPreferencesFile.delete();
    }


}