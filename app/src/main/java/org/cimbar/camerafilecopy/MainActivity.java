package org.cimbar.camerafilecopy;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity implements CvCameraViewListener2 {
    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 1;
    private static final int CREATE_FILE = 11;
    private static final int SELECT_DIRECTORY = 12;
    
    private static final String PREFS_NAME = "CimbarPrefs";
    private static final String PREF_SAVE_DIRECTORY = "save_directory";

    private CameraBridgeViewBase mOpenCvCameraView;
    private ToggleButton mModeSwitch;
    private int modeVal = 0;
    private int detectedMode = 68;
    private String dataPath;
    private String activePath;
    
    // Kullanıcının seçtiği dizini saklamak için
    private Uri savedDirectoryUri;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                Log.i(TAG, "OpenCV loaded successfully");

                // Load native library after(!) OpenCV initialization
                System.loadLibrary("cfc-cpp");

                mOpenCvCameraView.enableView();
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Permissions for Android 6+
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST
        );

        this.dataPath = this.getFilesDir().getPath();

        // Kaydedilmiş dizini yükle
        loadSavedDirectory();

        setContentView(R.layout.activity_main);
        mOpenCvCameraView = findViewById(R.id.main_surface);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mModeSwitch = (ToggleButton) findViewById(R.id.mode_switch);
        mModeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    modeVal = detectedMode;
                } else {
                    modeVal = 0;
                }
            }
        });
    }

    private void loadSavedDirectory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String uriString = prefs.getString(PREF_SAVE_DIRECTORY, null);
        if (uriString != null) {
            savedDirectoryUri = Uri.parse(uriString);
            // Persistable URI permission kontrolü
            try {
                getContentResolver().takePersistableUriPermission(
                    savedDirectoryUri, 
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
            } catch (SecurityException e) {
                Log.w(TAG, "Cannot take persistable URI permission, will prompt user again");
                savedDirectoryUri = null;
            }
        }
    }

    private void saveDirectory(Uri uri) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_SAVE_DIRECTORY, uri.toString());
        editor.apply();
        savedDirectoryUri = uri;
    }

    private void promptForDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, SELECT_DIRECTORY);
    }

    private String addZipExtension(String filename) {
        if (!filename.toLowerCase().endsWith(".zip")) {
            return filename + ".zip";
        }
        return filename;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
              //  mOpenCvCameraView.setCameraPermissionGranted();
            } else {
             ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            }
        } else {
            Log.e(TAG, "Unexpected permission request");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Toast.makeText(this, "Encode data at https://cimbar.org! :)",  Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPause() {
        shutdownJNI();
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onDestroy() {
        shutdownJNI();
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame frame) {
        // get current camera frame as OpenCV Mat object
        Mat mat = frame.rgba();

        // native call to process current camera frame
        String res = processImageJNI(mat.getNativeObjAddr(), this.dataPath, this.modeVal);

        // res will contain a file path if we completed a transfer. Ask the user where to save it
        if (res.startsWith("/")) {
            if (res.length() >= 2 && res.charAt(1) == '4') {
                detectedMode = 4;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mModeSwitch.setActivated(true);
                        mModeSwitch.setChecked(true);
                    }
                });
            }
            else {
                detectedMode = 68;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mModeSwitch.setActivated(false);
                        mModeSwitch.setChecked(true);
                    }
                });
            }
        }
        else if (!res.isEmpty()) {
            // Dosya adına .zip uzantısı ekle
            String filename = addZipExtension(res);
            this.activePath = this.dataPath + "/" + res;
            
            // Eğer kaydedilmiş dizin varsa, doğrudan oraya kaydet
            if (savedDirectoryUri != null) {
                saveToDirectory(filename);
            } else {
                // Kullanıcıdan dizin seçmesini iste
                promptForDirectory();
            }
        }

        // return processed frame for live preview
        return mat;
    }

    private void saveToDirectory(String filename) {
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(this, savedDirectoryUri);
            if (directory != null && directory.exists()) {
                DocumentFile newFile = directory.createFile("application/octet-stream", filename);
                if (newFile != null) {
                    copyFileToUri(newFile.getUri());
                } else {
                    Log.e(TAG, "Cannot create file in selected directory");
                    Toast.makeText(this, "Dosya oluşturulamadı, lütfen farklı bir dizin seçin", Toast.LENGTH_LONG).show();
                    promptForDirectory();
                }
            } else {
                Log.e(TAG, "Selected directory no longer exists");
                Toast.makeText(this, "Seçilen dizin artık mevcut değil, yeni bir dizin seçin", Toast.LENGTH_LONG).show();
                savedDirectoryUri = null;
                promptForDirectory();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to directory: " + e.toString());
            Toast.makeText(this, "Dosya kaydedilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyFileToUri(Uri destinationUri) {
        try (
                InputStream istream = new FileInputStream(this.activePath);
                OutputStream ostream = getContentResolver().openOutputStream(destinationUri)
        ) {
            byte[] buf = new byte[8192];
            int length;
            while ((length = istream.read(buf)) > 0) {
                ostream.write(buf, 0, length);
            }
            ostream.flush();
            
            Toast.makeText(this, "Dosya başarıyla kaydedildi!", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "File saved successfully to: " + destinationUri.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to write file: " + e.toString());
            Toast.makeText(this, "Dosya kaydedilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            try {
                new File(this.activePath).delete();
            } catch (Exception e) {
                Log.w(TAG, "Could not delete temp file: " + e.toString());
            }
            this.activePath = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_DIRECTORY && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    // Persistable URI permission al
                    try {
                        getContentResolver().takePersistableUriPermission(
                            uri, 
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );
                        saveDirectory(uri);
                        
                        // Eğer bekleyen bir dosya varsa, şimdi kaydet
                        if (this.activePath != null) {
                            String filename = new File(this.activePath).getName();
                            filename = addZipExtension(filename);
                            saveToDirectory(filename);
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Cannot take persistable URI permission: " + e.toString());
                        Toast.makeText(this, "Dizin erişim izni alınamadı", Toast.LENGTH_LONG).show();
                    }
                }
            } else if (requestCode == CREATE_FILE) {
                // Eski kod - artık kullanılmayacak ama geriye dönük uyumluluk için bırakılabilir
                if (this.activePath != null && data != null) {
                    copyFileToUri(data.getData());
                }
            }
        }
    }

    private native String processImageJNI(long mat, String path, int modeInt);
    private native void shutdownJNI();
}
