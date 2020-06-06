package it.unipr.advmobdev.whereiswally;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

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
     * Loading spinner.
     */
    private ProgressBar progressBar;

    /**
     * Button that starts Wally search.
     */
    private Button searchButton;
    /**
     * Button that shows statistics.
     */
    private Button statsButton;
    /**
     * Button that shows the output mask.
     */
    private MaterialButton maskButton;

    /**
     * Image view that shows input or output image.
     */
    private ImageView imageView;

    /**
     * ViewModel to handle displayed images during device rotation.
     */
    private FindWallyViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_wally);

        loadingOverlay = findViewById(R.id.loading_overlay);
        progressBar = findViewById(R.id.loading_progress);
        imageView = findViewById(R.id.img_input);

        String uri = getIntent().getStringExtra(EXTRA_IMG_URI);
        try {
            if (uri == null) {
                throw new FileNotFoundException("Empty image uri in intent");
            }
            // Initialize view model.
            viewModel = new ViewModelProvider(this, new ViewModelProvider.NewInstanceFactory())
                    .get(FindWallyViewModel.class);
            if (viewModel.isModelExecuted()) {
                imageView.setImageBitmap(viewModel.isVisibleOutputMask() ?
                        viewModel.getOutputMask() : viewModel.getOutputImage());
            } else {
                viewModel.loadInputImage(uri, getContentResolver());
                imageView.setImageBitmap(viewModel.getInputImage());
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            finish();
            return;
        }

        // Setup click listeners for buttons.
        searchButton = findViewById(R.id.btn_find_wally);
        searchButton.setOnClickListener(this);
        statsButton = findViewById(R.id.btn_stats);
        statsButton.setOnClickListener(this);
        maskButton = findViewById(R.id.btn_mask);
        maskButton.setOnClickListener(this);
        Button cancelButton = findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(this);

        if (viewModel.isModelExecuted()) {
            // Show correct button when activity is created.
            searchButton.setAlpha(0);
            searchButton.setVisibility(View.GONE);
            statsButton.setAlpha(1);
            statsButton.setVisibility(View.VISIBLE);
            maskButton.setAlpha(1);
            maskButton.setVisibility(View.VISIBLE);
            maskButton.setIcon(getDrawable(viewModel.isVisibleOutputMask() ?
                    R.drawable.ic_baseline_visibility_24 : R.drawable.ic_baseline_visibility_off_24));
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_find_wally:
                // Hide Find Wally button.
                searchButton.animate()
                        .alpha(0)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                searchButton.setVisibility(View.GONE);
                            }
                        });

                // Show loading spinner with a transition.
                loadingOverlay.setVisibility(View.VISIBLE);
                loadingOverlay.animate().alpha(1);

                // Start background thread for model execution.
                FindWallyActivity.this.loadAndRunModel();
                break;

            case R.id.btn_stats:
                DialogFragment fragment = new StatisticsDialogFragment(viewModel);
                fragment.show(getSupportFragmentManager(), "stats");
                break;

            case R.id.btn_mask:
                boolean isVisible = viewModel.isVisibleOutputMask();
                viewModel.setIsVisibleOutputMask(!isVisible);
                imageView.setImageBitmap(isVisible ?
                        viewModel.getOutputImage() : viewModel.getOutputMask());
                maskButton.setIcon(getDrawable(isVisible ?
                        R.drawable.ic_baseline_visibility_off_24 : R.drawable.ic_baseline_visibility_24));
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
        new ModelExecutor(this, true).start();
    }

    /**
     * Get the input image.
     *
     * @return the input image as a bitmap.
     */
    public Bitmap getInputImage() {
        return viewModel.getInputImage();
    }

    /**
     * Update the progress of the spinner.
     *
     * If the provided progress is not between 0 and 100, the mode of the
     * progress bar wil be set to indeterminate.
     *
     * @param progress The new progress.
     */
    public void updateProgress(final int progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progress >= 0 && progress <= 100) {
                    if (progressBar.isIndeterminate()) {
                        progressBar.setIndeterminate(false);
                    }
                    progressBar.setProgress(progress);
                } else {
                    progressBar.setIndeterminate(true);
                }
            }
        });
    }

    /**
     * Show the output image when the model execution ends.
     *
     * @param outputImage The output image to be shown.
     * @param outputMask The output mask returned by the model.
     * @param stats The statistics about execution.
     */
    public void onModelExecutionEnd(final Bitmap outputImage,
                                    final Bitmap outputMask,
                                    final Statistics stats) {
        viewModel.setOutputImage(outputImage);
        viewModel.setOutputMask(outputMask);
        viewModel.setStats(stats);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Hide loading spinner with a transition.
                loadingOverlay.animate()
                        .alpha(0)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                loadingOverlay.setVisibility(View.GONE);
                            }
                        });

                // Show mask button.
                maskButton.setVisibility(View.VISIBLE);
                maskButton.animate().alpha(1);
                // Show statistics button.
                statsButton.setVisibility(View.VISIBLE);
                statsButton.animate().alpha(1);

                // Show the output image.
                imageView.setImageBitmap(outputImage);
            }
        });
    }

    /**
     * Show a message error to the user.
     *
     * @param message The error message to be shown.
     */
    public void showError(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FindWallyActivity.this, message, Toast.LENGTH_LONG)
                        .show();
            }
        });
    }

    /**
     * Dialog fragment for statistics.
     */
    public static class StatisticsDialogFragment extends DialogFragment {
        private FindWallyViewModel viewModel;

        public StatisticsDialogFragment(FindWallyViewModel viewModel) {
            super();
            this.viewModel = viewModel;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.DialogTheme);
            builder.setTitle(R.string.stats)
                    .setMessage(viewModel.getStats().toString())
                    .setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            return builder.create();
        }
    }
}