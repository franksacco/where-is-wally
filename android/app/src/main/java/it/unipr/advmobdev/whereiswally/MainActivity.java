package it.unipr.advmobdev.whereiswally;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button openCameraButton = findViewById(R.id.button_open_camera);
        openCameraButton.setOnClickListener(this);

        Button loadImageButton = findViewById(R.id.button_load_image);
        loadImageButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_open_camera) {
            Log.d(TAG, "button open camera clicked");
            startActivity(new Intent(this, CameraActivity.class));

        } else if (v.getId() == R.id.button_load_image) {
            Log.d(TAG, "button load image clicked");

        }
    }
}