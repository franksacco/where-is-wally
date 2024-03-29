package it.unipr.advmobdev.whereiswally;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Camera activity used for taking pictures.
 */
public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";

    private static final int REQUEST_CAMERA_PERMISSION = 200;

    /**
     * Texture for camera preview.
     */
    private TextureView textureView;
    /**
     * Listener for texture view.
     */
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    /**
     * Take picture button.
     */
    FloatingActionButton takePictureButton;

    /**
     * System service manager for camera devices.
     */
    CameraManager cameraManager;
    /**
     * Reference to the main camera device.
     */
    private CameraDevice cameraDevice;
    /**
     * Handle state update on camera device.
     */
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera opened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera disconnected");
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera error: " + error);
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    /**
     * Map device rotation to image orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * The builder for capture request of the camera preview.
     */
    private CaptureRequest.Builder captureRequestBuilder;
    /**
     * Capture session used to display the camera preview.
     */
    private CameraCaptureSession cameraCaptureSessions;

    /**
     * The dimensions of preview image.
     */
    private Size imageDimensions;

    /**
     * Background thread used for updating the preview.
     */
    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Handle texture view.
        textureView = findViewById(R.id.cameraPreview);
        textureView.setSurfaceTextureListener(textureListener);

        // Handle take picture button.
        takePictureButton = findViewById(R.id.btn_take_picture);
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        // Set the position of take picture button.
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) takePictureButton.getLayoutParams();
        switch (rotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                break;
            case Surface.ROTATION_90:
                params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                break;
            case Surface.ROTATION_270:
                params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
                break;
        }
        takePictureButton.setLayoutParams(params);

        // Get camera manager service.
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            Log.e(TAG, "Camera manager is null");
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
        if (textureView.isAvailable()) {
            // If texture view is already available, open the camera device.
            openCamera();
        } else {
            // Otherwise (when the activity is started for the first time),
            // setup the texture listener.
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();

        super.onPause();
    }

    /**
     * Initialize camera device.
     */
    private void openCamera() {
        try {
            // Get ID of the main camera.
            String cameraId = cameraManager.getCameraIdList()[0];
            // Get image dimensions of the main camera.
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                imageDimensions = map.getOutputSizes(SurfaceTexture.class)[0];
            }

            // Ask permission for camera.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(
                        CameraActivity.this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION
                );
                return;
            }
            // Finally, open the camera device.
            cameraManager.openCamera(cameraId, stateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        scaleAndRotateTextureView();
    }

    /**
     * Add transformations to texture view in order to have a good preview.
     */
    private void scaleAndRotateTextureView() {
        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, imageDimensions.getHeight(), imageDimensions.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        Matrix matrix = new Matrix();

        // Fit the image inside the view respecting aspect ratio.
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
        float scale = Math.max(
                (float) viewHeight / imageDimensions.getHeight(),
                (float) viewWidth / imageDimensions.getWidth());
        matrix.postScale(scale, scale, centerX, centerY);

        // Rotate image according to device orientation.
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180, centerX, centerY);
        }

        textureView.setTransform(matrix);
    }

    /**
     * Close camera device.
     */
    private void closeCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    /**
     * Once the camera device is opened, show the preview in the texture view.
     */
    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(imageDimensions.getWidth(), imageDimensions.getHeight());
            Surface surface = new Surface(texture);

            // Create the request for the camera preview.
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    // Assuring that the camera is not already closed.
                    if (cameraDevice != null) {
                        // When the session is ready, we start displaying the preview.
                        cameraCaptureSessions = session;
                        // Manual control for auto-exposure, auto-white-balance and auto-focus is disabled.
                        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                        try {
                            // Update the preview using a background thread.
                            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "Camera preview session cannot be configured");
                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handle the click of "Take picture" button.
     */
    private void takePicture() {
        if (cameraDevice == null) {
            Log.e(TAG, "Unable to take picture: camera device is null");
            return;
        }

        try {
            // Calculate the dimensions of the output image.
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = null;
            if (map != null) {
                sizes = map.getOutputSizes(ImageFormat.JPEG);
            }
            int width = 1920;
            int height = 1080;
            if (sizes != null && sizes.length > 0) {
                width = sizes[0].getWidth();
                height = sizes[0].getHeight();
            }

            final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            // Create the request for an image capture.
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            // Manual control for auto-exposure, auto-white-balance and auto-focus is disabled.
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Check orientation base on device
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = reader.acquireLatestImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);
                    image.close();

                    // Save the acquired image in pictures folder.
                    File path = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                    final File file = new File(path, "IMG_" + sdf.format(new Date()) + ".jpg");
                    try {
                        // The WRITE_EXTERNAL_STORAGE permission is not needed since API level 19
                        // because we are writing in application-specific directories. See
                        // https://developer.android.com/reference/android/Manifest.permission#WRITE_EXTERNAL_STORAGE
                        OutputStream os = new FileOutputStream(file);
                        os.write(bytes);
                        os.close();

                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(CameraActivity.this,
                                    "Photo saved",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                    // Pass the filename of the acquired image to FindWallyActivity.
                    Intent intent = new Intent(CameraActivity.this, FindWallyActivity.class);
                    intent.putExtra(FindWallyActivity.EXTRA_IMG_URI, Uri.fromFile(file).toString());
                    startActivity(intent);

                }
            }, backgroundHandler);

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try{
                        session.capture(captureBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {}
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Close the activity.
                Toast.makeText(this,
                        "You can't use the camera without granting permission", Toast.LENGTH_LONG)
                        .show();
                finish();
            }
        }
    }

    /**
     * Start the background thread.
     */
    private void startBackgroundThread() {
        backgroundHandlerThread = new HandlerThread("Camera Background");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }

    /**
     * Stop the background thread.
     */
    private void stopBackgroundThread() {
        try {
            backgroundHandlerThread.quitSafely();
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}