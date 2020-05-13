package it.unipr.advmobdev.whereiswally;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Background task used to load and run the model.
 */
class ModelExecutor extends Thread {
    /**
     * Activity tag for logging.
     */
    private static final String TAG = "ModelExecutor";
    /**
     * Model filename in assets folder.
     */
    private static final String MODEL_FILENAME = "unet_v2.f1lo-b14-e60-lr0.001.44.tflite";
    /**
     * Height and width of sub-images.
     */
    private static final int SUB_IMAGE_SIZE = 256;
    /**
     * Maximum number of concurrent sub-image tasks.
     */
    private static final int MAX_NUM_THREAD = 4;

    /**
     * Task that predict the mask for a single sub-image.
     */
    private static class SubImageTask implements Callable<Bitmap> {
        private Interpreter interpreter;
        private Bitmap subImage;

        SubImageTask(Interpreter interpreter, Bitmap subImage) {
            this.interpreter = interpreter;
            this.subImage = subImage;
        }

        @Override
        public Bitmap call() {
            // Input image: buffer of floats
            // Dimensions: SUB_IMAGE_SIZE x SUB_IMAGE_SIZE x 3 channels
            TensorImage input = new TensorImage(DataType.FLOAT32);
            input.load(subImage);
            // Normalize input: converting RGB values from [0, 255] to [0, 1].
            ImageProcessor inputProcessor = new ImageProcessor.Builder()
                    .add(new NormalizeOp(0f, 255f))
                    .build();
            input = inputProcessor.process(input);

            // Output mask: buffer of floats
            // Dimensions: SUB_IMAGE_SIZE x SUB_IMAGE_SIZE x 1 channel
            TensorBuffer output = TensorBuffer.createFixedSize(
                    new int[]{1, SUB_IMAGE_SIZE, SUB_IMAGE_SIZE}, DataType.FLOAT32);

            // Run the model.
            interpreter.run(input.getBuffer(), output.getBuffer());

            return convertByteBufferToBitmap(output.getBuffer());
        }

        /**
         * Convert output buffer to bitmap mask.
         *
         * @param byteBuffer The output buffer.
         * @return the mask as bitmap.
         */
        private Bitmap convertByteBufferToBitmap(ByteBuffer byteBuffer) {
            int size = SUB_IMAGE_SIZE;

            byteBuffer.rewind();
            byteBuffer.order(ByteOrder.nativeOrder());
            int[] pixels = new int[size * size];
            for (int i = 0; i < size * size; i++) {
                if (byteBuffer.getFloat() > 0.5) {
                    pixels[i] = Color.WHITE;
                } else {
                    pixels[i] = Color.BLACK;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
            return bitmap;
        }
    }

    /**
     * Reference to the android activity.
     */
    private FindWallyActivity activity;

    ModelExecutor(FindWallyActivity activity) {
        super();
        this.activity = activity;
    }

    @Override
    public void run() {
        Interpreter interpreter;
        try {
            interpreter = loadInterpreter();

        } catch (IOException e) {
            e.printStackTrace();
            activity.showError("Model file not exists or cannot be opened");
            return;

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            activity.showError("Model file is badly encoded");
            return;
        }

        Bitmap image = activity.getInputImage();
        Log.d(TAG, "Initial size: " + image.getHeight() + " x " + image.getWidth());
        image = makeSizeMultipleOfSubImage(image);
        Log.d(TAG, "Padded size: " + image.getHeight() + " x " + image.getWidth());

        // TODO test with images with different size
        assert image.getHeight() == SUB_IMAGE_SIZE;
        assert image.getWidth() == SUB_IMAGE_SIZE;

        // Determine how many sub-images will be analyzed.
        int numSubImagesY = image.getHeight() / SUB_IMAGE_SIZE;
        int numSubImagesX = image.getWidth() / SUB_IMAGE_SIZE;
        Log.d(TAG, "Number of sub-images: " + (numSubImagesY * numSubImagesX));

        List<Callable<Bitmap>> tasks = new ArrayList<>();
        Bitmap subImage;
        for (int j = 0; j < numSubImagesY; j++) {
            for (int i = 0; i < numSubImagesX; i++) {
                subImage = Bitmap.createBitmap(image,
                        i * SUB_IMAGE_SIZE, j * SUB_IMAGE_SIZE,
                        SUB_IMAGE_SIZE, SUB_IMAGE_SIZE);
                tasks.add(new SubImageTask(interpreter, subImage));
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(MAX_NUM_THREAD);
        try {
            List<Future<Bitmap>> results = executor.invokeAll(tasks);
            for (Future<Bitmap> f: results) {
                activity.showOutputImage(f.get());
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            activity.showError("A thread was interrupted");

        } catch (ExecutionException e) {
            e.printStackTrace();
            activity.showError(e.getMessage());
        }

        interpreter.close();
    }

    /**
     * Load model file from assets and create interpreter instance.
     *
     * @return the interpreter instance.
     * @throws IOException if model file not exists or cannot be opened.
     * @throws IllegalArgumentException if model file is badly encoded.
     */
    private Interpreter loadInterpreter() throws IOException, IllegalArgumentException {
        MappedByteBuffer model = FileUtil.loadMappedFile(activity, MODEL_FILENAME);

        // The GPU Delegate allows the interpreter to run appropriate operations on the device's GPU.
        GpuDelegate delegate = new GpuDelegate();
        Interpreter.Options options = (new Interpreter.Options()).addDelegate(delegate);
        // Ensuring that interpreter uses only one thread for each sub-image.
        options.setNumThreads(1);

        return new Interpreter(model, options);
    }

    /**
     * Make height and width of the image multiple of SUB_IMAGE_SIZE.
     *
     * <p>This method performs a zero-padding to fit the correct size.</p>
     *
     * @param image The image to be processed.
     * @return the precessed image.
     */
    private Bitmap makeSizeMultipleOfSubImage(Bitmap image) {
        TensorImage tensorImage = TensorImage.fromBitmap(image);

        int size = SUB_IMAGE_SIZE;
        int height = image.getHeight();
        int width = image.getWidth();
        // Calculate new height and width.
        if (height % size != 0) {
            height += size - height % size;
        }
        if (width % size != 0) {
            width += size - width % size;
        }

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeWithCropOrPadOp(height, width))
                .build();
        tensorImage = imageProcessor.process(tensorImage);
        return tensorImage.getBitmap();
    }
}
