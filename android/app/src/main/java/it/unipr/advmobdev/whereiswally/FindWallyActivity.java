package it.unipr.advmobdev.whereiswally;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * This is the activity where the magic happens.
 */
public class FindWallyActivity extends AppCompatActivity {
    /**
     * Activity tag for logging.
     */
    private static final String TAG = "FindWallyActivity";

    /**
     * Name of the intent that contains input image filename.
     */
    public static final String EXTRA_IMG_FILENAME = "extra_img_filename";

    /**
     * Image view that shows input or output image.
     */
    private ImageView imageView;
    /**
     * Input image as bitmap.
     */
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
                FindWallyActivity.this.loadAndRunModel();
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

    /**
     * Load and run the model using background tasks.
     */
    private void loadAndRunModel() {
        new ModelExecutor(this).start();
    }

    /**
     * Get the input image.
     * @return the input image as a bitmap.
     */
    public Bitmap getInputImage() {
        return inputImage;
    }

    /**
     * Show the output image when the model execution ends.
     * @param outputImage The image to be shown.
     */
    public void showOutputImage(final Bitmap outputImage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                imageView.setImageBitmap(outputImage);
            }
        });
    }

    /**
     * Show a message error to the user.
     * @param message The error message to be shown.
     */
    public void showError(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FindWallyActivity.this,
                        message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}