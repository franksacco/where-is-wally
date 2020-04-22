package it.unipr.advmobdev.whereiswally;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

/**
 * This is the activity where the magic happens.
 */
public class FindWallyActivity extends AppCompatActivity {
    private static final String TAG = "FindWallyActivity";

    public static final String EXTRA_IMG_FILENAME = "extra_img_filename";

    private ImageView imageView;
    private Bitmap inputImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_wally);

        imageView = findViewById(R.id.img_input);
        // Load the passed image from the storage.
        String filename = getIntent().getStringExtra(EXTRA_IMG_FILENAME);
        if (filename != null) {
            Log.d(TAG, "Image filename: " + filename);

            inputImage = BitmapFactory.decodeFile(filename);
            imageView.setImageBitmap(inputImage);
        }

        Button findWallyButton = findViewById(R.id.btn_find_wally);
        findWallyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: find Wally
            }
        });

        Button cancelButton = findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}