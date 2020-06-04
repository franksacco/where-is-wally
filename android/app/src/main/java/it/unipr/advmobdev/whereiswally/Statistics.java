package it.unipr.advmobdev.whereiswally;

import androidx.annotation.NonNull;

import java.util.Date;
import java.util.Locale;

/**
 * This class manages statistics about model execution.
 */
class Statistics {
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
    private int maxParallelTasks = 0;

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
     * @param maxParallelTasks The maximum number of parallel tasks.
     */
    public void setMaxParallelTasks(int maxParallelTasks) {
        this.maxParallelTasks = maxParallelTasks;
    }

    @NonNull
    @Override
    public String toString() {
        float totalExecutionTime = (totalExecutionEnd - totalExecutionStart) / 1000f;
        float taskExecutionTime = (tasksExecutionEnd - tasksExecutionStart) / 1000f;
        float avgTimePerTask = tasksNumber == 0 ? 0f : taskExecutionTime / tasksNumber;
        String format = "Total execution time: %.3f s\n" +
                "Model execution time: %.3f s\n" +
                "Number of sub-images/tasks: %d\n" +
                "Average time per task: %.3f s\n" +
                "Max number of parallel tasks: %d\n";
        return String.format(Locale.getDefault(),
                format,
                totalExecutionTime,
                taskExecutionTime,
                tasksNumber,
                avgTimePerTask,
                maxParallelTasks);
    }
}