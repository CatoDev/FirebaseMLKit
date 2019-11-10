package com.devfest.facedetection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BarCodeActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 1000;
    File foto;
    private String mDirAbsoluto = null;

    FirebaseVisionBarcodeDetectorOptions options;
    Bitmap bitmap;

    private static final Integer SCALE_FACTOR_IMAGE_VIEW = 4;
    private static final int PERMISSION_REQUESTS = 1;
    private static final int REQUEST_CODE_CAMARA = 2;
    private static final String EXTENSION_JPEG = ".jpg";
    private static final String TAG = "BarCodeActivity";
    private static final String ALBUM = "Album";


    private ImageView imageView;
    private ImageButton btn_photo;
    private ImageButton btn_library;
    private TextView tvBarcode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);
        imageView = findViewById(R.id.imageView);
        btn_photo = findViewById(R.id.btn_tomar_foto);
        btn_library = findViewById(R.id.btn_library);
        tvBarcode = findViewById(R.id.tv_barcode);
        if (!allPermissionsGranted()){
            getRuntimePermissions();
        }else{
            btn_photo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    getFoto("nombre_foto");
                }
            });
            btn_library.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openGallery();
                }
            });
        }

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_CAMARA:
                if (resultCode == RESULT_OK) {
                    mostrarFoto();
                    detectarCodigoBarras();
                    break;
                }
            case PICK_IMAGE:
                if (resultCode == RESULT_OK) {
                    Uri imageUri = data.getData();

                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    imageView.setImageBitmap(bitmap);
                    detectarCodigoBarras();

                }
        }
    }

    private void mostrarFoto() {
        bitmap = escalarBitmap(mDirAbsoluto, SCALE_FACTOR_IMAGE_VIEW);
        imageView.setImageBitmap(bitmap);
    }

    private void detectarCodigoBarras() {

    }

    private void dibujarRect(Point[] corners) {
        Bitmap bmp = Bitmap.createBitmap(imageView.getWidth(), imageView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        imageView.draw(c);
        Paint p = new Paint();
        p.setColor(Color.BLUE);
        c.drawCircle(corners[0].x,corners[0].y,15f, p);
        imageView.setImageBitmap(bmp);
    }




    //--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
    //--------------------------------------------------------Funciones para foto-----------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------------------------------------------------------------------------


    private void getFoto(String nombreFoto) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            // Crea el Nombre de la Fotografía
            String fechaHora = new SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.getDefault()).format(new Date());
            String nombre = ALBUM + "_" + fechaHora;
            // Crea el Archivo de la Fotografía
            foto = nombrarArchivo(BarCodeActivity.this, ALBUM, nombreFoto,
                    EXTENSION_JPEG);


            // Guarda el Directorio Absoluto en una Variable Global
            mDirAbsoluto = foto.getAbsolutePath();
            intent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(BarCodeActivity.this, BuildConfig.APPLICATION_ID, foto));

        } catch (IOException e) {
            e.printStackTrace();
            foto = null;
            mDirAbsoluto = null;
        }

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_CAMARA);
    }

    private File nombrarArchivo(Context context, String album, String nombre,
                                String extension) throws IOException {
        return new File(obtenerDirectorioPublico(context, album), nombre
                + extension);
    }

    private File obtenerDirectorioPublico(Context context, String album) {
        File file = null;

        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            file = new File(
                    Environment
                            .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    album);

                if (!file.mkdirs()) {
                    if (!file.exists()) {
                        Toast.makeText(context,
                                "Error al crear el directorio.",
                                Toast.LENGTH_SHORT).show();
                        return null;
                    }
                }

        } else {
            Toast.makeText(context, "Tarjeta SD no disponible.",
                    Toast.LENGTH_SHORT).show();
            file = new File(context.getFilesDir(), album);
        }
        return file;
    }

    private Bitmap rotarBitmap(String Url, Bitmap bitmap) {
        try {
            ExifInterface exifInterface = new ExifInterface(Url);
            int orientacion = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, 1);
            Matrix matrix = new Matrix();

            if (orientacion == 6) {
                matrix.postRotate(90);
            } else if (orientacion == 3) {
                matrix.postRotate(180);
            } else if (orientacion == 8) {
                matrix.postRotate(270);
            }

            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true); // rotating bitmap
        } catch (Exception e) {
            // TODO:
            Log.e("df", "rotarBitmap: ");
        }
        Log.e("df", "rotarBitmap: ");
        return bitmap;
    }

    public Bitmap escalarBitmap(String uri, Integer factor) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = factor;
        bmOptions.inPurgeable = true;
        return rotarBitmap(uri, BitmapFactory.decodeFile(uri, bmOptions));
    }

    private void openGallery() {
        Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(gallery, PICK_IMAGE);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
    //--------------------------------------------------------Permisos-----------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------------------------------------------------------------------------

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }


    private static boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: " + permission);
            return true;
        }
        Log.i(TAG, "Permission NOT granted: " + permission);
        return false;
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }

}
