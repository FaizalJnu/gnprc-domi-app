package com.example.guagereaderapp;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class Inference extends AppCompatActivity {
    private ImageView resultImageView;
    private TextView resultTextView;
    private Interpreter tflite;
    private ByteBuffer inputBuffer;
    private Map<Integer, Object> outputMap;
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.4f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_inference);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        resultImageView = findViewById(R.id.imageView2);
        resultTextView = findViewById(R.id.textView2);

        // Initialize model and IO
        initializeInterpreter();
        initializeIO();

        // Get and process the image
        Bitmap receivedImage = getIntent().getParcelableExtra("captured_image");
        if (receivedImage != null) {
            resultImageView.setImageBitmap(receivedImage);
            processImage(receivedImage);
        } else {
            Toast.makeText(this, "Error: No image received", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeInterpreter() {
        try {
            Interpreter.Options options = new Interpreter.Options();

            // Enable GPU if available
            CompatibilityList compatList = new CompatibilityList();
            if(compatList.isDelegateSupportedOnThisDevice()) {
                GpuDelegate.Options delegateOptions = new GpuDelegate.Options();
                GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
                options.addDelegate(gpuDelegate);
            } else {
                options.setNumThreads(4);
            }

            // Load model from assets
            tflite = new Interpreter(loadModelFile(), options);
        } catch (Exception e) {
            Log.e("Inference", "Error initializing interpreter", e);
            Toast.makeText(this, "Error initializing model", Toast.LENGTH_SHORT).show();
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        String modelPath = "gauge_model.tflite";
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void initializeIO() {
        // Initialize input buffer
        inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        // Initialize output map
        outputMap = new HashMap<>();
        outputMap.put(0, new float[1][100][4]);  // Boxes
        outputMap.put(1, new float[1][100]);     // Scores
        outputMap.put(2, new float[1][100]);     // Classes
    }

    private void processImage(Bitmap image) {
        if (tflite == null) {
            Toast.makeText(this, "Model not yet initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            GaugeReading reading = inferGauge(image);
            if (reading != null) {
                // Update UI with results
                String resultText = String.format(
                        "Gauge Reading: %.1f\nAngle: %.1f°\n" +
                                "Center: (%d, %d)\n" +
                                "Needle Tip: (%d, %d)",
                        reading.reading, reading.angle,
                        reading.center.x, reading.center.y,
                        reading.needleTip.x, reading.needleTip.y
                );
                resultTextView.setText(resultText);

                // Draw the detection on the image
                drawDetectionOnImage(image, reading);
            } else {
                resultTextView.setText("Could not detect gauge reading");
            }
        } catch (Exception e) {
            Log.e("Inference", "Error processing image", e);
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }

    private ByteBuffer preprocessImage(Bitmap image) {
        inputBuffer.rewind();
        if (!OpenCVLoader.initDebug()) {
            throw new RuntimeException("OpenCV initialization failed");
        }

        Mat mat = new Mat();
        Utils.bitmapToMat(image, mat);

        // Enhanced preprocessing using OpenCV
        Mat enhanced = new Mat();
        mat.convertTo(enhanced, -1, 1.2, 10);

        Mat gray = new Mat();
        Imgproc.cvtColor(enhanced, gray, Imgproc.COLOR_BGR2GRAY);

        CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
        Mat equalized = new Mat();
        clahe.apply(gray, equalized);

        Mat resized = new Mat();
        Imgproc.resize(equalized, resized, new Size(INPUT_SIZE, INPUT_SIZE));

        float[] pixelValues = new float[INPUT_SIZE * INPUT_SIZE];
        resized.get(0, 0, pixelValues);
        for (float pixelValue : pixelValues) {
            inputBuffer.putFloat(pixelValue / 255.0f);
        }

        return inputBuffer;
    }

    private GaugeReading inferGauge(Bitmap image) {
        ByteBuffer inputData = preprocessImage(image);
        tflite.runForMultipleInputsOutputs(new Object[]{inputData}, outputMap);

        float[][] boxes = (float[][]) outputMap.get(0);
        float[] scores = (float[]) outputMap.get(1);
        float[] classes = (float[]) outputMap.get(2);

        Point center = null;
        Point needleTip = null;
        Rectangle gaugeBox = null;

        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > CONFIDENCE_THRESHOLD) {
                float[] box = boxes[i];
                int classId = (int) classes[i];

                int x1 = (int) (box[0] * image.getWidth());
                int y1 = (int) (box[1] * image.getHeight());
                int x2 = (int) (box[2] * image.getWidth());
                int y2 = (int) (box[3] * image.getHeight());

                Point midPoint = new Point((x1 + x2) / 2, (y1 + y2) / 2);

                switch (classId) {
                    case 0: // Center
                        center = midPoint;
                        break;
                    case 1: // Gauge
                        gaugeBox = new Rectangle(x1, y1, x2 - x1, y2 - y1);
                        break;
                    case 2: // Needle
                        needleTip = midPoint;
                        break;
                }
            }
        }

        if (center != null && needleTip != null) {
            double angle = calculateAngle(center, needleTip);
            double reading = getReadingFromAngle(angle, 45, 515, 0, 100);
            return new GaugeReading(reading, angle, center, needleTip, gaugeBox);
        }

        return null;
    }

    private void drawDetectionOnImage(Bitmap image, GaugeReading reading) {
        Bitmap mutableBitmap = image.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);

        // Draw gauge box
        if (reading.gaugeBox != null) {
            paint.setColor(Color.GREEN);
            canvas.drawRect(
                    reading.gaugeBox.x,
                    reading.gaugeBox.y,
                    reading.gaugeBox.x + reading.gaugeBox.width,
                    reading.gaugeBox.y + reading.gaugeBox.height,
                    paint
            );
        }

        // Draw center point
        paint.setColor(Color.RED);
        canvas.drawCircle(reading.center.x, reading.center.y, 10, paint);

        // Draw needle line
        paint.setColor(Color.BLUE);
        canvas.drawLine(
                reading.center.x,
                reading.center.y,
                reading.needleTip.x,
                reading.needleTip.y,
                paint
        );

        resultImageView.setImageBitmap(mutableBitmap);
    }

    private double calculateAngle(Point center, Point needleTip) {
        double dx = needleTip.x - center.x;
        double dy = center.y - needleTip.y;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        return (angle + 360) % 360;
    }

    private double getReadingFromAngle(double angle, double minAngle, double maxAngle,
                                       double minValue, double maxValue) {
        if (angle > maxAngle) {
            angle -= 360;
        }

        double angleRange = maxAngle - minAngle;
        double valueRange = maxValue - minValue;
        double reading = ((angle - minAngle) / angleRange) * valueRange + minValue;

        return Math.round(reading * 10) / 10.0;
    }

    // Helper classes
    public static class Point {
        public final int x, y;
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Rectangle {
        public final int x, y, width, height;
        public Rectangle(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static class GaugeReading {
        public final double reading;
        public final double angle;
        public final Point center;
        public final Point needleTip;
        public final Rectangle gaugeBox;

        public GaugeReading(double reading, double angle, Point center,
                            Point needleTip, Rectangle gaugeBox) {
            this.reading = reading;
            this.angle = angle;
            this.center = center;
            this.needleTip = needleTip;
            this.gaugeBox = gaugeBox;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) {
            tflite.close();
        }
    }
}