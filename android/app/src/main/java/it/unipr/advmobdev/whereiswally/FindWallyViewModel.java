package it.unipr.advmobdev.whereiswally;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.lifecycle.ViewModel;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * View model used to maintain FindWallyActivity's data during rotation.
 */
public class FindWallyViewModel extends ViewModel {
    /**
     * The input image.
     */
    private Bitmap inputImage;

    /**
     * The output image.
     */
    private Bitmap outputImage;

    /**
     * The output mask.
     */
    private Bitmap outputMask;

    /**
     * The statistics about execution.
     */
    private Statistics stats;

    /**
     * Load input image from storage.
     *
     * @param uri The URI of the image as string.
     * @param contentResolver The content resolver.
     * @throws FileNotFoundException if the provided URI could not be opened.
     */
    void loadInputImage(String uri, ContentResolver contentResolver) throws FileNotFoundException {
        if (inputImage == null) {
            InputStream is = contentResolver.openInputStream(Uri.parse(uri));
            inputImage = BitmapFactory.decodeStream(is);
        }
    }

    /**
     * Get the input image.
     *
     * @return the input image.
     */
    Bitmap getInputImage() {
        return inputImage;
    }

    /**
     * Check whether the model is executed.
     *
     * @return <code>true</code> if output image, output mask and statistics
     *         are available, <code>false</code> otherwise.
     */
    boolean isModelExecuted() {
        return outputImage != null && outputMask != null && stats != null;
    }

    /**
     * Set the output image.
     *
     * @param outputImage The output image.
     */
    void setOutputImage(Bitmap outputImage) {
        this.outputImage = outputImage;
    }

    /**
     * Get the output image.
     *
     * @return the output image if set, <code>null</code> otherwise.
     */
    Bitmap getOutputImage() {
        return outputImage;
    }

    /**
     * Set the output mask.
     *
     * @param outputMask The output mask.
     */
    public void setOutputMask(Bitmap outputMask) {
        this.outputMask = outputMask;
    }

    /**
     * Get the output mask.
     *
     * @return the output mask if set, <code>null</code> otherwise.
     */
    public Bitmap getOutputMask() {
        return outputMask;
    }

    /**
     * Set the statistics about execution.
     *
     * @param stats The statistics about execution.
     */
    public void setStats(Statistics stats) {
        this.stats = stats;
    }

    /**
     * Get the statistics about execution.
     *
     * @return the statistics if set, <code>null</code> otherwise.
     */
    public Statistics getStats() {
        return stats;
    }
}