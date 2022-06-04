package com.example.wasteclassifier;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
//import java.io.FileNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends Activity {

    private static final int CAMERA_REQUEST = 1888;
    private static final int MY_CAMERA_PERMISSION_CODE = 100;

    Drive service;

    java.io.File photoFile = null;

    //private static final String CREDENTIALS_FILE_PATH = "/credential.json";
    private static final String SERVICE_ACCOUNT_USER = "service-account@waste-classifier-348613.iam.gserviceaccount.com";
    private static final String APPLICATION_NAME = "Waste Classifier";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Log.i("Drive", "Service connecting started.");
        // Get service account secret
        //InputStream inputStream = MainActivity.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        //if (inputStream == null)
        //    try {
        //        throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        //    } catch (FileNotFoundException e) {
        //        e.printStackTrace();
        //    }

        // Convert file in InputStream
        InputStream keyStream = MainActivity.class.getResourceAsStream("/key.p12");

        // Http transport creation
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        // Instance of the JSON factory
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        // Instance of the scopes required
        List<String> scopes = new ArrayList<>();
        scopes.add(DriveScopes.DRIVE);

        // Build Google credential
        GoogleCredential credential = null;
        try {
            credential = new GoogleCredential.Builder()
                    .setTransport(httpTransport)
                    .setJsonFactory(jsonFactory)
                    .setServiceAccountId("service-account@waste-classifier-348613.iam.gserviceaccount.com")
                    .setServiceAccountScopes(scopes)
                    .setServiceAccountPrivateKeyFromP12File(keyStream)
                    .setServiceAccountUser(SERVICE_ACCOUNT_USER)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }

        // Build Drive service
        service = new Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        Log.d("Drive","Service connected!");


        Thread clearDriveCache = new Thread() {
            @Override
            public void run() {
                // Update file on google drive
                try {
                    FileList filesList;
                    File res;

                    filesList = getFilesList(false);
                    for(int i = 0; i < filesList.getFiles().size(); i++){
                        String fileId = filesList.getFiles().get(i).getId();
                        service.files().delete(fileId).execute();
                        Log.d("Drive","Cleared file with id: " + fileId);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        };
        clearDriveCache.start();

        final Button cameraButton = (Button) findViewById(R.id.buttonCamera);

        cameraButton.setOnClickListener(v -> {
            Log.d("Action","Pressed camera button.");
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.CAMERA}, MY_CAMERA_PERMISSION_CODE);
            } else {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    // Create the File where the photo should go
                    photoFile = createImageFile();
                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        Uri photoURI = FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()),
                                BuildConfig.APPLICATION_ID + ".provider", photoFile);

                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                        startActivityForResult(takePictureIntent, CAMERA_REQUEST);
                    }
            }
        });
    }

    public java.io.File createImageFile(){
        java.io.File fileDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fileDir = new java.io.File (MainActivity.this.getExternalFilesDir(null) + java.io.File.separator + "photo");
        } else {
            fileDir = new java.io.File(Environment.getExternalStorageDirectory().getAbsolutePath() + java.io.File.separator + "photo");
        }

        java.io.File tempDir;
        tempDir =new java.io.File(fileDir.getAbsolutePath() + "/.temp/");

        if(!tempDir.exists())
        {
            if (!tempDir.mkdirs()) {
                Log.i("File", "Temp folder created.");
            }
            else{
                Log.e("File", "Temp folder not created!");
            }
        }

        java.io.File photo = null;
        try {
            photo = java.io.File.createTempFile("picture", ".png", tempDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return photo;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK && photoFile != null)
        {
            Uri photoURI = FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()),
                    BuildConfig.APPLICATION_ID + ".provider", photoFile);
            Bitmap photo = null;
            try {
                photo = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoURI);
            } catch (IOException e) {
                e.printStackTrace();
            }



            int rotateImage = getCameraPhotoOrientation(this, photoURI,
                    photoFile.getAbsolutePath());

            final ImageView[] imageView = {findViewById(R.id.imageView)};


            Matrix matrix = new Matrix();
            matrix.postRotate(rotateImage);
            Bitmap res = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(),
                    matrix, true);

            imageView[0].setImageBitmap(res);

            final ProgressBar progressBar = findViewById(R.id.progressBar);
            progressBar.setVisibility(View.VISIBLE);

            final TextView label = findViewById(R.id.textView);
            label.setText("Ожидайте...");

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    final Bitmap[] bitmap = {null};

                    Thread createFile = new Thread() {
                        @Override
                        public void run() {
                            // Update file on google drive
                            try {
                                UpdateFile();
                                // Share files (For debugging)
                                //sharePermission();

                                FileList filesList;
                                File res;

                                filesList = getFilesList(false);
                                while(filesList.getFiles().size() == 0) {

                                    Log.i("File", "Waiting for result...");
                                    filesList = getFilesList(false);
                                    sleep(100);
                                }

                                String fileId = filesList.getFiles().get(0).getId();
                                OutputStream outputStream = new ByteArrayOutputStream();
                                Log.i("File", "Downloading result...");
                                service.files().get(fileId)
                                        .executeMediaAndDownloadTo(outputStream);

                                byte[] bitmapdata = ((ByteArrayOutputStream) outputStream).toByteArray();

                                bitmap[0] = BitmapFactory.decodeByteArray(bitmapdata, 0, bitmapdata.length);
                                Log.i("File", "Downloaded. Clearing old on drive.");
                                service.files().delete(fileId).execute();

                            } catch (IOException | InterruptedException e) {
                                e.printStackTrace();
                            }

                        }
                    };
                    createFile.start();

                    try {
                        createFile.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    progressBar.setVisibility(View.INVISIBLE);


                    imageView[0].setRotation(0);
                    imageView[0] = findViewById(R.id.imageView);
                    imageView[0].setImageBitmap(bitmap[0]);

                    label.setText("Сделайте фото!");
                }
            }, 2000);
        }
    }

    private void UpdateFile() throws IOException {
        Log.i("Drive", "File update started.");
        File fileMetadata = new File().setName("photo.png");
        FileContent photo = new FileContent("image/png", photoFile);

        FileList filesList = getFilesList(true);

        if (filesList.getFiles().size() != 0) {
            Log.i("Drive", "Deleting old file...");
            String first = filesList.getFiles().get(0).getId();
            service.files().delete(first).execute();
        }
        Log.i("Drive", "Creating new file...");
        service.files().create(fileMetadata, photo)
                .setFields("id")
                .execute();
        Log.i("Drive", "File updated!");
    }


    @NonNull
    private FileList getFilesList(Boolean fetchForUpload) throws IOException {
        Log.i("Drive", "Fetching files on drive...");
        FileList result;
        if(fetchForUpload) {
            result = service.files().list()
                    .setQ("name = 'photo.png'")
                    .setPageSize(10)
                    .setFields("nextPageToken, files(id, name, mimeType)")
                    .execute();
            Log.i("Drive", "Base files on drive:" + result.toString());
        }
        else{
            result = service.files().list()
                    .setQ("name = 'predict.jpg'")
                    .setPageSize(10)
                    .setFields("nextPageToken, files(id, name, mimeType)")
                    .execute();
            Log.i("Drive", "Result files on drive:" + result.toString());
        }
        return result;

    }

    private void sharePermission() throws IOException {
        Log.i("Drive", "Permissions update started.");
        FileList filesList = getFilesList(true);
        String fileId = filesList.getFiles().get(0).getId();

        Permission permission = new Permission();
        permission.setEmailAddress("pavelfedotov2000@gmail.com");
        permission.setRole("writer");
        permission.setType("user");

        Log.i("Drive", "Updating permissions...");
        Permission answer = service.permissions().create(fileId, permission).setSendNotificationEmail(false).execute();
        Log.i("Drive", "Shared permission:" + answer.toString());
        Log.i("Drive", "Permissions update complete!");
    }

    public int getCameraPhotoOrientation(Context context, Uri imageUri,
                                         String imagePath) {
        int rotate = 0;
        try {
            context.getContentResolver().notifyChange(imageUri, null);
            java.io.File imageFile = new java.io.File(imagePath);
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }

            Log.i("RotateImage", "Exif orientation: " + orientation);
            Log.i("RotateImage", "Rotate value: " + rotate);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rotate;
    }
}