package it.unipr.advmobdev.whereiswally;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * Custom image view used to enable move and zoom the displayed image.
 */
public class CustomImageView extends androidx.appcompat.widget.AppCompatImageView {
    /**
     * States in which the image view can be.
     */
    private enum State {NONE, DRAGGING, ZOOMING}

    /**
     * The state of the view.
     */
    State state = State.NONE;

    /**
     * Transformation matrix applied to the displayed image.
     */
    Matrix matrix;
    /**
     * Auxiliary transformation matrix.
     */
    Matrix savedMatrix = new Matrix();

    /**
     * Starting point when dragging.
     */
    PointF startingPoint = new PointF();
    /**
     * Mid point when zooming.
     */
    PointF midPoint = new PointF();

    /**
     * Initial distance of fingers when scaling.
     */
    float initialDistance = 1f;

    public CustomImageView(Context context){
        this(context, null, 0);
    }

    public CustomImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (matrix == null) {
            // Calculate the matrix to fit image inside the view.
            Bitmap bitmap = ((BitmapDrawable) getDrawable()).getBitmap();
            int viewWidth = getWidth();
            int viewHeight = getHeight();
            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();
            // Calculate the scaling factor for each axis.
            float xScale = ((float) viewWidth) / bitmapWidth;
            float yScale = ((float) viewHeight) / bitmapHeight;
            float scale = Math.min(xScale, yScale);
            matrix = new Matrix();
            matrix.setScale(scale, scale);
            // Move the image to the center.
            if (xScale <= yScale) {
                // The scaled width of the image will correspond to view width.
                matrix.postTranslate(0, viewHeight / 2f - bitmapHeight * scale / 2);
            } else {
                // The scaled height of the image will correspond to view height.
                matrix.postTranslate(viewWidth / 2f - bitmapWidth * scale / 2, 0);
            }
            // Finally, apply the matrix.
            setImageMatrix(matrix);
        }
        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                startingPoint.set(event.getX(), event.getY());
                state = State.DRAGGING;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                initialDistance = distance(event);
                if (initialDistance > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(midPoint, event);
                    state = State.ZOOMING;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                state = State.NONE;
                break;

            case MotionEvent.ACTION_MOVE:
                if (state == State.DRAGGING) {
                    matrix.set(savedMatrix);
                    matrix.postTranslate(event.getX() - startingPoint.x,
                            event.getY() - startingPoint.y);

                } else if (state == State.ZOOMING) {
                    float newDist = distance(event);
                    if (newDist > 10f) {
                        matrix.set(savedMatrix);
                        float scale = newDist / initialDistance;
                        matrix.postScale(scale, scale, midPoint.x, midPoint.y);
                    }
                }
                break;
        }

        setImageMatrix(matrix);
        return true;
    }

    /**
     * Calculate the distance between two pointer of the given event.
     *
     * @param event The event.
     * @return the Euclidean distance between pointers.
     */
    private float distance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Calculate the midpoint between two pointer of the given event.
     *
     * @param point The output midpoint.
     * @param event The event.
     */
    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }
}
