package com.example.laserpenv1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class StudentAndSchoolInfoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_and_school_info);
    }

    // 打開教授網站
    public void openProfessorWebsite(View view) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/cswang/"));
        startActivity(browserIntent);
    }

    // 打開校園網站
    public void openCampusWebsite(View view) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://csie.au.edu.tw/index.php?Lang=zh-tw"));
        startActivity(browserIntent);
    }
}
