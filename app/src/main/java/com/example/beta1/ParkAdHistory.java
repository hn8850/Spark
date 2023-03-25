package com.example.beta1;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.ListView;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ParkAdHistory extends AppCompatActivity {
    String currUserID;
    FirebaseDatabase fbDB;
    ListView listView;
    ArrayList<HashMap<String, String>> parkAdHistoryDataList = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_park_ad_history);
        listView = findViewById(R.id.listview2);

        Intent gi = getIntent();
        currUserID = gi.getStringExtra("UID");

        fbDB = FirebaseDatabase.getInstance();
        VerifyParkAdDates();
    }

    public void VerifyParkAdDates() {
        DatabaseReference AdsDB = fbDB.getReference("ParkAds");
        AdsDB.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                System.out.println("check");
                SimpleDateFormat dateFormat = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
                SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                for (DataSnapshot snapshot1 : snapshot.getChildren()) {
                    ParkAd parkAd = snapshot1.getValue(ParkAd.class);
                    System.out.println("yes");

                    String currentDate = dateFormat.format(new Date());
                    currentDate = Services.addLeadingZerosToDate(currentDate, false);
                    String parkAdDateStr = parkAd.getDate();
                    parkAdDateStr = Services.addLeadingZerosToDate(parkAdDateStr, false);
                    try {
                        System.out.println("Dates: " + currentDate + "," + parkAdDateStr);
                        //System.out.println("Dates: " + Integer.valueOf(currentDate) + "," + Integer.valueOf(parkAdDateStr));
                        if (Integer.valueOf(currentDate) > Integer.valueOf(parkAdDateStr)) {
                            UpdateParkAdCompleted(snapshot1.getKey()); //parkDate has passed,hence its completed
                        } else if (parkAdDateStr.matches(currentDate)) {
                            long currentTimeMillis = System.currentTimeMillis();
                            Date current2 = new Date(currentTimeMillis);
                            String currentHour = hourFormat.format(current2);
                            System.out.println("HOURS: " + currentHour + "," + parkAd.getBeginHour() + "," + parkAd.getFinishHour());
                            if (!isFirstTimeBeforeSecond(currentHour, parkAd.getFinishHour())) {
                                UpdateParkAdCompleted(snapshot1.getKey()); //ParkHour has passed,hence its completed
                            }
                        }
                    } catch (Error e) {
                        System.out.println("CHECK THIS");
                    }
                }
                readParkAdHistory();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    public void UpdateParkAdCompleted(String ParkAdID) {
        DatabaseReference ExpiredAd = fbDB.getReference("ParkAds").child(ParkAdID);
        ExpiredAd.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ParkAd completeAd = snapshot.getValue(ParkAd.class);
                completeAd.setActive(0);
                DatabaseReference completeBranch = fbDB.getReference("Users").child(completeAd.getUserID()).child("ParkAds");
                completeBranch.child(ParkAdID).setValue(completeAd);
                ExpiredAd.setValue(null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }


    public void readParkAdHistory() {
        DatabaseReference userAds = fbDB.getReference("Users").child(currUserID).child("ParkAds");
        userAds.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot snapshot1 : snapshot.getChildren()) {
                    ParkAd parkAd = snapshot1.getValue(ParkAd.class);
                    if (parkAd.getActive() == 0) {
                        HashMap<String, String> data = new HashMap<>();
                        data.put("date", parkAd.getDate());
                        data.put("begin", parkAd.getBeginHour());
                        data.put("end", parkAd.getFinishHour());
                        data.put("address", parkAd.getAddress());
                        data.put("price", "NONE");
                        parkAdHistoryDataList.add(data);

                    }
                }
                System.out.println("Data = " + parkAdHistoryDataList.toString());

                CustomParkAdListAdapter adapter = new CustomParkAdListAdapter(parkAdHistoryDataList);
                listView.setAdapter(adapter);
            }


            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    public static boolean isFirstTimeBeforeSecond(String firstTimeStr, String secondTimeStr) {
        try {
            // Format the input strings with leading zeros for single-digit hours
            firstTimeStr = String.format("%02d", Integer.parseInt(firstTimeStr.substring(0, firstTimeStr.indexOf(":")))) + firstTimeStr.substring(firstTimeStr.indexOf(":"));
            secondTimeStr = String.format("%02d", Integer.parseInt(secondTimeStr.substring(0, secondTimeStr.indexOf(":")))) + secondTimeStr.substring(secondTimeStr.indexOf(":"));

            // Parse the time strings into LocalTime objects
            LocalTime firstTime = LocalTime.parse(firstTimeStr);
            LocalTime secondTime = LocalTime.parse(secondTimeStr);

            // Compare the LocalTime objects and return the result
            return firstTime.isBefore(secondTime);
        } catch (Error e) {
            // Handle any parse errors
            System.err.println("Error parsing time string: " + e.getMessage());
            return false;
        }
    }

//    private static boolean isHourBetween(String checkHour, String beginHour, String endHour) {
//        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
//        try {
//            Date check = formatter.parse(checkHour);
//            Date begin = formatter.parse(beginHour);
//            Date end = formatter.parse(endHour);
//            if (check.after(begin) && check.before(end)) {
//                return true;
//            }
//        } catch (ParseException e) {
//            e.printStackTrace();
//        }
//        return false;
//    }

//    private boolean doAppsExist(List<Intent> apps) {
//        PackageManager packageManager = getPackageManager();
//        List<ResolveInfo> activities = packageManager.queryIntentActivities(apps.get(0), 0);
//        boolean isIntentSafe = activities.size() > 0;
//        if (!isIntentSafe) {
//            activities = packageManager.queryIntentActivities(apps.get(1), 0);
//            isIntentSafe = activities.size() > 0;
//            return isIntentSafe;
//        }
//        return true;
//    }


}