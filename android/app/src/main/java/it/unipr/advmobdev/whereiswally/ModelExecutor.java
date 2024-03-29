package it.unipr.advmobdev.whereiswally;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

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
     * Model filename in assets folder.
     */
    private static final String MODEL_FILENAME = "unet_v2.f1lo-b14-e60-lr0.001.44.tflite";
    /**
     * Height and width of sub-images.
     */
    private static final int SUB_IMAGE_SIZE = 256;

    /**
     * Task that predict the mask for a single sub-image.
     */
    private static class SubImageTask implements Callable<Bitmap> {
        private final Interpreter interpreter;
        private final Bitmap subImage;
        private final Runnable onTaskEnd;

        /**
         * Initialize task to apply model on a sub-image.
         *
         * @param interpreter The model to be run.
         * @param subImage The image on which the model will be applied.
         * @param onTaskEnd The runnable called at the end of the task.
         */
        SubImageTask(Interpreter interpreter, Bitmap subImage, Runnable onTaskEnd) {
            this.interpreter = interpreter;
            this.subImage = subImage;
            this.onTaskEnd = onTaskEnd;
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

            Bitmap outputMask = convertByteBufferToBitmap(output.getBuffer());
            onTaskEnd.run();
            return outputMask;
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

    /**
     * The number of parallel tasks.
     */
    private final int parallelTasksNumber;

    /**
     * Whether the GPU acceleration is enabled.
     */
    private final boolean isGpuAccelerationEnabled;

    /**
     * Current progress on the execution of tasks.
     */
    private float progress = 0;
    /**
     * Progress increment based on the number of tasks.
     */
    private float progressIncrement;

    /**
     * Initialize the model executor.
     *
     * @param activity The activity that launched the execution.
     * @param parallelTasksNumber The number of parallel tasks.
     * @param isGpuAccelerationEnabled Whether the GPU acceleration is enabled.
     */
    ModelExecutor(FindWallyActivity activity, int parallelTasksNumber, boolean isGpuAccelerationEnabled) {
        super();
        this.activity = activity;
        this.parallelTasksNumber = parallelTasksNumber;
        this.isGpuAccelerationEnabled = isGpuAccelerationEnabled;
    }

    @Override
    public void run() {
        Statistics stats = new Statistics();
        stats.setGpuAccelerationEnabled(isGpuAccelerationEnabled);
        stats.setParallelTasksNumber(parallelTasksNumber);
        stats.triggerTotalExecutionStart();

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
        stats.setOriginalSize(image.getWidth(), image.getHeight());
        image = makeSizeMultipleOfSubImage(image);
        stats.setPaddedSize(image.getWidth(), image.getHeight());

        // Determine how many sub-images and tasks will be created for each axis.
        int numTasksX = image.getWidth() / SUB_IMAGE_SIZE;
        int numTasksY = image.getHeight() / SUB_IMAGE_SIZE;
        int numTasks = numTasksX * numTasksY;
        stats.setTasksNumber(numTasks);
        progressIncrement = 100f / numTasks;

        // Associate each sub-image to a single task.
        List<Callable<Bitmap>> tasks = getTaskList(interpreter, image, numTasksX, numTasksY);
        // Execute all tasks using a thread pool with a fixed number of threads.
        ExecutorService executor = Executors.newFixedThreadPool(parallelTasksNumber);
        Bitmap mask = null;
        try {
            stats.triggerModelExecutionStart();
            activity.updateProgress(0);
            List<Future<Bitmap>> results = executor.invokeAll(tasks);
            activity.updateProgress(-1);
            stats.triggerModelExecutionEnd();

            // Create the final mask to be applied on the image.
            mask = composeSubMasks(image, results, numTasksX, numTasksY);

        } catch (InterruptedException e) {
            e.printStackTrace();
            activity.showError("A thread was interrupted");

        } catch (ExecutionException e) {
            e.printStackTrace();
            activity.showError(e.getMessage());

        } finally {
            interpreter.close();
        }

        if (mask != null) {
            applyMask(image, mask);

            // Restore the original size of the image.
            image = restoreInitialSize(image);
            mask = restoreInitialSize(mask);

            // Show final result to the user.
            stats.triggerTotalExecutionEnd();
            activity.onModelExecutionEnd(image, mask, stats);
        }
    }

    /**
     * Load model file from assets and create interpreter instance.
     *
     * @return the interpreter instance.
     *
     * @throws IOException if model file not exists or cannot be opened.
     * @throws IllegalArgumentException if model file is badly encoded.
     */
    private Interpreter loadInterpreter() throws IOException, IllegalArgumentException {
        MappedByteBuffer model = FileUtil.loadMappedFile(activity, MODEL_FILENAME);

        Interpreter.Options options = new Interpreter.Options();
        if (isGpuAccelerationEnabled) {
            // The GPU Delegate allows the interpreter to run appropriate
            // operations on the device's GPU.
            options.addDelegate(new GpuDelegate());
        }
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

    /**
     * Create task list composed by callable objects that returns the mask
     * associated to the provided sub-image using the same interpreter.
     *
     * @param interpreter The TensorFlow Lite interpreter.
     * @param image The input image.
     * @param numSubImagesX The number of sub-images along the x-axis.
     * @param numSubImagesY The number of sub-images along the y-axis.
     * @return the list of callable objects.
     */
    private List<Callable<Bitmap>> getTaskList(Interpreter interpreter,
                                               Bitmap image,
                                               int numSubImagesX,
                                               int numSubImagesY)
    {
        Runnable onTaskEnd = new Runnable() {
            @Override
            public void run() {
                progress += progressIncrement;
                activity.updateProgress((int) progress);
            }
        };

        List<Callable<Bitmap>> tasks = new ArrayList<>();
        Bitmap subImage;
        for (int j = 0; j < numSubImagesY; j++) {
            for (int i = 0; i < numSubImagesX; i++) {
                subImage = Bitmap.createBitmap(image,
                        i * SUB_IMAGE_SIZE, j * SUB_IMAGE_SIZE,
                        SUB_IMAGE_SIZE, SUB_IMAGE_SIZE);
                tasks.add(new SubImageTask(interpreter, subImage, onTaskEnd));
            }
        }
        return tasks;
    }

    /**
     * Compose masks from each task execution to generate the final
     * mask with the same size of the given image.
     *
     * @param image The input image.
     * @param results The list of result from task execution.
     * @param numSubImagesX The number of sub-images along the x-axis.
     * @param numSubImagesY The number of sub-images along the y-axis.
     * @return the final mask composed by all generated sub-masks.
     * @throws ExecutionException if a task aborted throwing an exception.
     * @throws InterruptedException if a task was interrupted.
     */
    private Bitmap composeSubMasks(Bitmap image, List<Future<Bitmap>> results,
                                   int numSubImagesX, int numSubImagesY)
            throws ExecutionException, InterruptedException
    {
        Bitmap mask = Bitmap.createBitmap(image.getWidth(), image.getHeight(), image.getConfig());
        Canvas canvas = new Canvas(mask);
        for (int j = 0; j < numSubImagesY; j++) {
            for (int i = 0; i < numSubImagesX; i++) {
                canvas.drawBitmap(
                        results.get(i + j * numSubImagesX).get(),
                        i * SUB_IMAGE_SIZE,
                        j * SUB_IMAGE_SIZE,
                        null
                );
            }
        }
        return mask;
    }

    /**
     * Apply provided mask to the given image converting to grayscale pixels
     * that not corresponds to a white pixel in the mask.
     *
     * @param image The image to be filtered.
     * @param mask The mask to be applied.
     */
    private void applyMask(Bitmap image, Bitmap mask) {
        int rgb, r, g, b, gray;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                // Convert to grayscale pixels that not corresponds
                // to a white pixel in the mask.
                if (mask.getPixel(x, y) != Color.WHITE) {
                    rgb = image.getPixel(x, y);
                    r = (rgb >> 16) & 0xff;
                    g = (rgb >> 8) & 0xff;
                    b = rgb & 0xff;
                    gray = (r + g + b) / 3;
                    image.setPixel(x, y, 0xff000000 | (gray << 16) | (gray << 8) | gray);
                }
            }
        }
    }

    /**
     * Restore the original size of the input image.
     *
     * @param image The image to be processed.
     * @return the precessed image.
     */
    private Bitmap restoreInitialSize(Bitmap image) {
        TensorImage tensorImage = TensorImage.fromBitmap(image);

        int width = activity.getInputImage().getWidth();
        int height = activity.getInputImage().getHeight();

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeWithCropOrPadOp(height, width))
                .build();
        tensorImage = imageProcessor.process(tensorImage);
        return tensorImage.getBitmap();
    }
}
