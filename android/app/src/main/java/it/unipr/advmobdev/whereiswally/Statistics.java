package it.unipr.advmobdev.whereiswally;

import android.util.Size;

import androidx.annotation.NonNull;

import java.util.Date;
import java.util.Locale;

/**
 * This class manages statistics about model execution.
 */
class Statistics {
    /**
     * The original size of the image.
     */
    private Size originalSize = new Size(0, 0);
    /**
     * The size of input image after padding.
     */
    private Size paddedSize = new Size(0, 0);

    /**
     * Whether the GPU acceleration is enabled.
     */
    private boolean isGpuAccelerationEnabled = false;

    /**
     * The start of main execution in milliseconds
     * since January 1, 1970, 00:00:00 GMT.
     */
    private long totalExecutionStart = 0;
    /**
     * The end of main execution in milliseconds
     * since January 1, 1970, 00:00:00 GMT.
     */
    private long totalExecutionEnd = 0;

    /**
     * The start of tasks execution in milliseconds
     * since January 1, 1970, 00:00:00 GMT.
     */
    private long tasksExecutionStart = 0;
    /**
     * The end of tasks execution in milliseconds
     * since January 1, 1970, 00:00:00 GMT.
     */
    private long tasksExecutionEnd = 0;

    /**
     * The number of sub-images/tasks.
     */
    private int tasksNumber = 0;

    /**
     * The maximum number of parallel tasks.
     */
    private int parallelTasksNumber = 0;

    /**
     * Set the original size of the image.
     *
     * @param width The width in pixels.
     * @param height The height in pixels.
     */
    public void setOriginalSize(int width, int height) {
        originalSize = new Size(width, height);
    }

    /**
     * Set the size of input image after padding.
     *
     * @param width The width in pixels.
     * @param height The height in pixels.
     */
    public void setPaddedSize(int width, int height) {
        paddedSize = new Size(width, height);
    }

    /**
     * Set whether the GPU acceleration is enabled.
     *
     * @param gpuAccelerationEnabled Whether the GPU acceleration is enabled.
     */
    public void setGpuAccelerationEnabled(boolean gpuAccelerationEnabled) {
        this.isGpuAccelerationEnabled = gpuAccelerationEnabled;
    }

    /**
     * Trigger the start of main execution.
     */
    public void triggerTotalExecutionStart() {
        totalExecutionStart = new Date().getTime();
    }

    /**
     * Trigger the end of main execution.
     */
    public void triggerTotalExecutionEnd() {
        totalExecutionEnd = new Date().getTime();
    }

    /**
     * Trigger the start of tasks execution.
     */
    public void triggerModelExecutionStart() {
        tasksExecutionStart = new Date().getTime();
    }

    /**
     * Trigger the end of tasks execution.
     */
    public void triggerModelExecutionEnd() {
        tasksExecutionEnd = new Date().getTime();
    }

    /**
     * Set the number of sub-images/tasks used in the execution.
     *
     * @param tasksNumber The number of sub-images/tasks.
     */
    public void setTasksNumber(int tasksNumber) {
        this.tasksNumber = tasksNumber;
    }

    /**
     * Set the maximum number of parallel tasks.
     *
     * @param parallelTasksNumber The maximum number of parallel tasks.
     */
    public void setParallelTasksNumber(int parallelTasksNumber) {
        this.parallelTasksNumber = parallelTasksNumber;
    }

    @NonNull
    @Override
    public String toString() {
        float totalExecutionTime = (totalExecutionEnd - totalExecutionStart) / 1000f;
        float taskExecutionTime = (tasksExecutionEnd - tasksExecutionStart) / 1000f;
        float avgTimePerTask = tasksNumber == 0 ? 0f : taskExecutionTime / tasksNumber;
        String format = "Original size: %d x %d\n" +
                "Size with padding: %d x %d\n" +
                "GPU enabled: %b\n" +
                "Number of parallel tasks: %d\n" +
                "Total execution time: %.3f s\n" +
                "Tasks execution time: %.3f s\n" +
                "Number of tasks: %d\n" +
                "Average time per task: %.3f s\n";
        return String.format(Locale.getDefault(),
                format,
                originalSize.getWidth(), originalSize.getHeight(),
                paddedSize.getWidth(), paddedSize.getHeight(),
                isGpuAccelerationEnabled,
                parallelTasksNumber,
                totalExecutionTime,
                taskExecutionTime,
                tasksNumber,
                avgTimePerTask);
    }
}