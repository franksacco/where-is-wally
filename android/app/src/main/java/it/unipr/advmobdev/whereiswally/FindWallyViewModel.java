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
    private Bitmap inputImage;
    private Bitmap outputImage;

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
     * Check whether the output image is set.
     *
     * @return <code>true</code> if output image is available,
     *     <code>false</code> otherwise.
     */
    boolean hasOutputImage() {
        return outputImage != null;
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
     * Set the output image.
     *
     * @param outputImage The output image.
     */
    void setOutputImage(Bitmap outputImage) {
        this.outputImage = outputImage;
    }
}
