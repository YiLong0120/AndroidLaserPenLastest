package com.example.laserpenv1;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class HSVActivity extends AppCompatActivity {

    private OpenCVProcessor openCVProcessor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        openCVProcessor = new OpenCVProcessor(this, null);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inputhsv);

        EditText editH = findViewById(R.id.H);
        EditText editS = findViewById(R.id.S);
        EditText editV = findViewById(R.id.V);
        Button btnSaveHSV = findViewById(R.id.Submit);

        btnSaveHSV.setOnClickListener(v -> {
            try {
                float h = Float.parseFloat(editH.getText().toString());
                float s = Float.parseFloat(editS.getText().toString());
                float vv = Float.parseFloat(editV.getText().toString());

                // 保存HSV值或传递给OpenCVProcessor
                // 例如：openCVProcessor.inputHSV(h, s, v);
                openCVProcessor.inputHSV(h, s, vv);

                Toast.makeText(this, "HSV Values Saved: H=" + h + " S=" + s + " V=" + v, Toast.LENGTH_SHORT).show();
                finish(); // 关闭当前Activity
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid HSV Values", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
