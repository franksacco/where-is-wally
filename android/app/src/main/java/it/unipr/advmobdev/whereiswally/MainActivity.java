package it.unipr.advmobdev.whereiswally;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/**
 * Starting and main activity of the application.
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int READ_REQUEST_CODE = 100;
    private static final int REQUEST_READ_PERMISSION = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button openCameraButton = findViewById(R.id.btn_open_camera);
        openCameraButton.setOnClickListener(this);

        Button loadImageButton = findViewById(R.id.btn_load_image);
        loadImageButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_open_camera) {
            startActivity(new Intent(this, CameraActivity.class));

        } else if (v.getId() == R.id.btn_load_image) {
            // Ask permission for read external storage.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_READ_PERMISSION);
                return;
            }

            loadImage();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_READ_PERMISSION && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this,
                        "You can't load an image without granting permission", Toast.LENGTH_LONG)
                        .show();
            } else {
                loadImage();
            }
        }
    }

    /**
     * Let the user selects the image.
     */
    private void loadImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpg", "image/jpeg", "image/png"});
        this.startActivityForResult(intent, READ_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    // Pass the filename of the selected image to FindWallyActivity.
                    Intent intent = new Intent(this, FindWallyActivity.class);
                    intent.putExtra(FindWallyActivity.EXTRA_IMG_FILENAME, uri.getPath());
                    startActivity(intent);
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}