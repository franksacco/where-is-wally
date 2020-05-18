package it.unipr.advmobdev.whereiswally;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.FileNotFoundException;

/**
 * This is the activity where the magic happens.
 */
public class FindWallyActivity extends AppCompatActivity implements View.OnClickListener {
    /**
     * Name of the intent that contains input image filename.
     */
    public static final String EXTRA_IMG_URI = "extra_img_filename";

    /**
     * Relative layout used as overlay during model execution.
     */
    private LinearLayout loadingOverlay;

    /**
     * Image view that shows input or output image.
     */
    private ImageView imageView;

    /**
     * ViewModel to handle displayed images during device rotation.
     */
    private FindWallyViewModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_wally);

        loadingOverlay = findViewById(R.id.loading_overlay);
        imageView = findViewById(R.id.img_input);

        String uri = getIntent().getStringExtra(EXTRA_IMG_URI);
        try {
            if (uri == null) {
                throw new FileNotFoundException("Empty image uri in intent");
            }
            // Initialize view model.
            model = new ViewModelProvider(this, new ViewModelProvider.NewInstanceFactory())
                    .get(FindWallyViewModel.class);
            if (model.hasOutputImage()) {
                imageView.setImageBitmap(model.getOutputImage());
            } else {
                model.loadInputImage(uri, getContentResolver());
                imageView.setImageBitmap(model.getInputImage());
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            finish();
            return;
        }

        // Setup click listeners for buttons.
        Button findWallyButton = findViewById(R.id.btn_find_wally);
        findWallyButton.setOnClickListener(this);
        Button cancelButton = findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_find_wally:
                // Show loading spinner with a transition.
                loadingOverlay.setVisibility(View.VISIBLE);
                loadingOverlay.animate().alpha(1);
                // Start background thread for model execution.
                FindWallyActivity.this.loadAndRunModel();
                break;
            case R.id.btn_cancel:
                finish();
                break;
        }
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
        return model.getInputImage();
    }

    /**
     * Show the output image when the model execution ends.
     * @param outputImage The image to be shown.
     */
    public void showOutputImage(final Bitmap outputImage) {
        model.setOutputImage(outputImage);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Hide loading spinner with a transition.
                loadingOverlay
                        .animate()
                        .alpha(0)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                loadingOverlay.setVisibility(View.GONE);
                            }
                        });
                // Show the output image.
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