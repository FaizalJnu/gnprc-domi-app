package com.example.guagereaderapp;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.Manifest;

import com.example.guagereaderapp.ml.GuageModel;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private Button camerabtn, gallerybtn, inferbtn;
    private ImageView imageView;
    private TextView textView;
    private GuageModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        setupWindowInsets();
        initializeViews();
        setupClickListeners();
        initializeModel();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void initializeViews() {
        camerabtn = findViewById(R.id.camerabtn);
        gallerybtn = findViewById(R.id.uploadbtn);
        inferbtn = findViewById(R.id.inferbtn);
        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);
    }

    private void setupClickListeners() {
        camerabtn.setOnClickListener(v -> requestCameraPermission());
        gallerybtn.setOnClickListener(v -> requestStoragePermissionAndOpenGallery());
        inferbtn.setOnClickListener(v -> handleInference());
    }

    private void initializeModel() {
        try {
            model = GuageModel.newInstance(this);
        } catch (IOException e) {
            Log.e("MainActivity", "Error loading model", e);
            Toast.makeText(this, "Error loading Guage model", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleInference() {
        if (imageView.getDrawable() == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            imageView.setDrawingCacheEnabled(true);
            Bitmap bitmap = imageView.getDrawingCache();
            Intent inferenceIntent = new Intent(MainActivity.this, Inference.class);
            inferenceIntent.putExtra("captured_image", bitmap);
            imageView.setDrawingCacheEnabled(false);
            startActivity(inferenceIntent);
        } catch (Exception e) {
            Log.e("MainActivity", "Error preparing image for inference", e);
            Toast.makeText(this, "Error preparing image", Toast.LENGTH_SHORT).show();
        }
    }

    private static final int STORAGE_PERMISSION_CODE = 200;
    private static final int GALLERY_REQUEST_CODE = 1000;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int CAMERA_REQUEST_CODE = 1888;

    private void requestStoragePermissionAndOpenGallery() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION_CODE);
        } else {
            openGallery();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"),
                GALLERY_REQUEST_CODE);
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        } else {
            openCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case CAMERA_PERMISSION_CODE:
                    Toast.makeText(this, "Camera Permission Granted",
                            Toast.LENGTH_SHORT).show();
                    openCamera();
                    break;
                case STORAGE_PERMISSION_CODE:
                    Toast.makeText(this, "Storage Permission Granted",
                            Toast.LENGTH_SHORT).show();
                    openGallery();
                    break;
            }
        } else {
            String permission = requestCode == CAMERA_PERMISSION_CODE ? "Camera" : "Storage";
            Toast.makeText(this, permission + " Permission Denied",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Log.e("MainActivity", "Camera not available", e);
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        try {
            switch (requestCode) {
                case GALLERY_REQUEST_CODE:
                    Uri selectedImageUri = data.getData();
                    if (selectedImageUri != null) {
                        imageView.setImageURI(selectedImageUri);
                    }
                    break;

                case CAMERA_REQUEST_CODE:
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        Bitmap photo = (Bitmap) extras.get("data");
                        if (photo != null) {
                            imageView.setImageBitmap(photo);
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error processing image", e);
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (model != null) {
            model.close();
        }
    }
}