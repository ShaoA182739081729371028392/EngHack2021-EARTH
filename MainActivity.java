package org.pytorch.earth;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.Thread;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.renderscript.ScriptGroup;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Canvas;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.InvalidMarkException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

//import org.pytorch.LiteModuleLoader;
import com.facebook.soloader.nativeloader.NativeLoader;
import com.facebook.soloader.nativeloader.SystemDelegate;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.pytorch.IValue;
import org.pytorch.MemoryFormat;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.util.Arrays;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
  static {
    if (!NativeLoader.isInitialized()) {
      NativeLoader.init(new SystemDelegate());
    }
    NativeLoader.loadLibrary("pytorch_jni");
    NativeLoader.loadLibrary("torchvision_ops");
  }


  private static final int CAMERA_REQUEST = 1888;
  private static final int INITIAL_REQUEST=1337;

  private static final int CONTACTS_REQUEST=INITIAL_REQUEST+2;
  private static final int LOCATION_REQUEST=INITIAL_REQUEST+3;
  private ImageView imageView;
  public static int GALLERY_REQUEST = 100;
  private static int RESULT = 0;

  private static final int MY_CAMERA_PERMISSION_CODE = 100;
  public static final int REQUEST_WRITE_STORAGE = 112;
  public static String mCurrentPhotoPath = null;
  private static Module pytorch_model = null;
  private static String[] IDX_CLASSES = TACO_Classes.TACO_CLASSES;
  // INSTANTIATE FIREBASE
  private static FirebaseFirestore db = FirebaseFirestore.getInstance();
  private static String city = null;
  // LOOKUP FN
  private static PieChart pieChart;
  private static PieData pieData;
  private static List<PieEntry> pieEntryList = new ArrayList<>();

  private static void lookup_data(String key){
    DocumentReference docRef = db.collection("locations").document(key);
    docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
      @Override
      public void onComplete(@NonNull Task<DocumentSnapshot> task) {
        if (task.isSuccessful()) {
          // Document found in the offline cache
          DocumentSnapshot document = task.getResult();
          Map<String, Object> ex = document.getData();
          System.out.println(ex);
          if (ex != null && ex.containsKey(key)) {
            int result = ((Long)ex.get(key)).intValue();
            RESULT = result;
            System.out.println(RESULT);
          }
          else{
            RESULT = -1; // Indicate that there is no value.
          }
        }
      }
    });
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  };

  // ADD FN
  private static void add_data(String key, int value){
    // Data is loaded at /posts/key -> Map<Key, Values>
    // and /user-posts/key -> Map<Key, Value>
    Map<String, Integer> ex = new HashMap<String, Integer>();
    ex.put(key, value);
    db.collection("locations").document(key).set(ex);
  }

  private File getAlbumDir() {
    File storageDir = null;

    if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {

      storageDir = new File(Environment.getExternalStorageDirectory()
              + "/dcim/"
              + "MyRecipes");

      if (!storageDir.mkdirs()) {
        if (!storageDir.exists()) {
          Log.d("CameraSample", "failed to create directory");
          return null;
        }
      }

    } else {
      Log.v(getString(R.string.app_name), "External storage is not mounted READ/WRITE.");
    }

    return storageDir;
  }

  private File createImageFile() throws IOException {
    // Create an image file name
    String imageFileName = "PHOTO_TMP";
    File storageDir = getAlbumDir();
    File image = File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",         /* suffix */
            storageDir      /* directory */
    );
    mCurrentPhotoPath = image.getAbsolutePath();
    return image;
  }
  public void take_photo(View view){
    // Grab an External File for storage
    File photoFile = null;
    try{
      photoFile = createImageFile();
    }
    catch(IOException ex){

    }

    Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
    Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
    cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

    if (photoFile != null) {
      Uri photoURI = FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", photoFile);

      cameraIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoURI);
      startActivityForResult(cameraIntent, CAMERA_REQUEST);
    }
  }
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
  {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == MY_CAMERA_PERMISSION_CODE)
    {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
      {
      }
      else
      {
        Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
      }
    }
  }

  private Bitmap setPic() {

    /* There isn't enough memory to open up more than a couple camera photos */
    /* So pre-scale the target bitmap into which the file is decoded */

    /* Get the size of the ImageView */
    int targetW = 512;
    int targetH = 512;

    /* Get the size of the image */
    System.out.println(mCurrentPhotoPath);
    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
    bmOptions.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
    int photoW = bmOptions.outWidth;
    int photoH = bmOptions.outHeight;

    /* Figure out which way needs to be reduced less */
    int scaleFactor = 2;
    if ((targetW > 0) && (targetH > 0)) {
      scaleFactor = Math.max(photoW / targetW, photoH / targetH);
    }

    /* Set bitmap options to scale the image decode target */
    bmOptions.inJustDecodeBounds = false;
    bmOptions.inSampleSize = scaleFactor;
    bmOptions.inPurgeable = true;

    Matrix matrix = new Matrix();
    matrix.postRotate(getRotation());

    /* Decode the JPEG file into a Bitmap */
    Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
    System.out.println("ALL OK.");
    /* Associate the Bitmap to the ImageView */
    /* Resize the Bitmap to (512, 512) */

    Bitmap new_bitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, false);
    // Recycle the Old Bitmap
    bitmap.recycle();

    return new_bitmap;
  }

  private float getRotation() {
    try {
      InputStream is = new FileInputStream(mCurrentPhotoPath);
      ExifInterface ei = new ExifInterface(is);
      int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

      switch (orientation) {
        case ExifInterface.ORIENTATION_ROTATE_90:
          return 90f;
        case ExifInterface.ORIENTATION_ROTATE_180:
          return 180f;
        case ExifInterface.ORIENTATION_ROTATE_270:
          return 270f;
        default:
          return 90f;
      }
    } catch (Exception e) {
      Log.e("Add Recipe", "getRotation", e);
      return 0f;
    }
  }

  private Bitmap handleBigCameraPhoto() {
    if (mCurrentPhotoPath != null) {
      Bitmap bitmap = setPic();
      return bitmap;
    }
    return null;
  }
  public void load_from_gallery(View view){


    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);

    photoPickerIntent.setType("image/*");
    startActivityForResult(photoPickerIntent, GALLERY_REQUEST);

  }
  private void PyTorchProcessing(Bitmap bitmap) throws InterruptedException {
    // Input_Image: Tensor(1, 3, 512, 512)
    // Run the Image thru the neural net:
    // Output Shape: (N, 6)
    final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);//, MemoryFormat.CHANNELS_LAST);

    Tensor outputs = pytorch_model.forward(IValue.from(inputTensor)).toTensor();
    // (x1, y1, x2, y2, obj, cls)
    // Threshold 0.25 for bounding boxes
    // Then, lookup the Classes.
    long N = outputs.shape()[0];
    float[] array_outputs = outputs.getDataAsFloatArray();
    float THRESH = (float)0.25;
    float x1 = (float)0.0;
    float x2 = (float)0.0;
    float y1 = (float)0.0;
    float y2 = (float)0.0;
    float obj = (float)0.0;
    float cls = (float)0.0;
    int idx = 0;
    // Compute Class Counts
    Map<String, Integer> class_counts = new HashMap<>();

    String class_name = "";
    Canvas cnvs=new Canvas(bitmap);

    Paint paint=new Paint();
    paint.setColor(Color.RED);
    paint.setStrokeWidth(2);
    cnvs.drawBitmap(bitmap, 0, 0, null);
    for (int i = 0; i < N; i++){
      int idx_begin = i * 6;
      int idx_end = (i + 1) * 6;
      float[] values = Arrays.copyOfRange(array_outputs, idx_begin, idx_end);
      x1 = values[0];
      y1 = values[1];
      x2 = values[2];
      y2 = values[3];
      obj = values[4];
      cls = values[5];
      idx = (int)cls;
      if (obj >= THRESH){
        // Valid Bounding Box, draw the 4 lines around the bounding box
        cnvs.drawLine(x1, y1, x1, y2, paint);
        cnvs.drawLine(x1, y1, x2, y1, paint);
        cnvs.drawLine(x2, y1, x2, y2, paint);
        cnvs.drawLine(x1, y2, x2, y2, paint);
        // Draw the Text Above the Bounding Box
        class_name = IDX_CLASSES[idx];
        if (class_counts.containsKey(class_name)){
          class_counts.put(class_name, class_counts.get(class_name) + 1);
        }
        else{
          class_counts.put(class_name, 1);
        }
        cnvs.drawText(class_name, x1, y1, paint);
      }

    }

    ImageView img = (ImageView)findViewById(R.id.image_input);
    img.setImageBitmap(bitmap);
    // Update the class counts inside of the
    String[] all_keys = class_counts.keySet().toArray(new String[0]);

    String key = null;

    String output_string = "";
    int cur_count = 0;
    String output_counts = class_counts.toString();
    for (int i = 0; i < all_keys.length; i++){
      key = all_keys[i];
      cur_count = cur_count + class_counts.get(key);
      output_string = output_string + " " + key;
    }
    lookup_data(city);
    //while (RESULT == 0) {

    //};
    System.out.println(RESULT);
    // Grab the Result
    if (RESULT == -1){
      // New Data

    }
    else{
      // Old Data, add them
      cur_count = cur_count + RESULT;
    }
    add_data(city, cur_count);
    // DISPLAY THE BOUNDING BOX RESULTS
    output_string = "BBOXES FOUND:" + output_string;
    // UPDATE THE TEXT VIEW
    TextView tv = (TextView)findViewById(R.id.BoundingBoxFound);
    tv.setText(output_string);

    // DISPLAY String Counts Result
    output_counts = "COUNTS: " + output_counts;
    // UPDATE THE TEXT VIEW
    tv = (TextView)findViewById(R.id.BoundingBoxCounts);
    tv.setText(output_counts);
    display_pie_chart();

  }
  public void load_all_data(){
    // Loads all of the data off of FireBase
    // By All: I mean the top 10 garbage producing cities.
    Task<com.google.firebase.firestore.QuerySnapshot> collection = db.collection("locations").get();
    collection.addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
              @Override
              public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                  pieChart = findViewById(R.id.pieChart);
                  List<PieEntry> pieEntryList = new ArrayList<>();
                  for (QueryDocumentSnapshot document : task.getResult()) {
                    Map<String, Object> ex = document.getData();
                    // <Map: <String, Object>>

                    String[] keys = ex.keySet().toArray(new String[0]);
                    String key = null;
                    int value = 0;
                    for (int i = 0; i < keys.length; i++){
                      key = keys[i]; // String
                      value =  ((Long)ex.get(key)).intValue();
                    }
                    // Only One Key per, anyways.
                    pieEntryList.add(new PieEntry(value, key));
                    System.out.println("------------------------------");
                    System.out.println(value);
                    System.out.println(key);
                    System.out.println("-----------------------------");
                  }
                  PieDataSet pieDataSet = new PieDataSet(pieEntryList, "city");
                  pieDataSet.setColors(ColorTemplate.JOYFUL_COLORS);
                  pieData = new PieData(pieDataSet);
                  pieChart.getLegend().setTextColor(Color.BLACK);
                  pieChart.setEntryLabelColor(Color.BLACK);
                  pieChart.setData(pieData);
                }
              }
            });
  }

  public void display_pie_chart(){
    load_all_data();

  }
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Bitmap bitmap = null;
    if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
      bitmap = handleBigCameraPhoto();
    }
    else if (requestCode == GALLERY_REQUEST && resultCode == Activity.RESULT_OK) {

      try {
        Uri imageUri = data.getData();
        InputStream imageStream = getContentResolver().openInputStream(imageUri);
        bitmap = BitmapFactory.decodeStream(imageStream);

        // Unlike from the Camera, the Uploaded Images are full sized(~2500 x 4000)
        // FIX ROTATION
        int targetW = 512;
        int targetH = 512;
        float rotation = 90f;

        /* Set bitmap options to scale the image decode target */

        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);

        /* Decode the JPEG file into a Bitmap */
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
        System.out.println("ALL OK.");
        /* Associate the Bitmap to the ImageView */
        /* Resize the Bitmap to (512, 512) */

        Bitmap new_bitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, false);
        // Recycle the Old Bitmap
        bitmap.recycle();
        // Resize the image

        bitmap = new_bitmap;
      } catch (FileNotFoundException e) {
        e.printStackTrace();

      }

    }
    System.out.println(bitmap);
    if (bitmap != null) {
      try {
        PyTorchProcessing(bitmap);
      }catch(InterruptedException ex){

      }
    }


  }

  public void AboutEarthPushed(View view){
    // scroll the View down to the Correct Part of the Page
    ScrollView sv = (ScrollView)findViewById(R.id.scroll);
    // Grab the View to Scroll to
    int position_y = 1600;
    sv.smoothScrollTo(0, position_y);

  }
  public void TryItOutPushed(View view){
    ScrollView sv = (ScrollView)findViewById(R.id.scroll);
    int position_y = 1900;
    sv.smoothScrollTo(0, position_y);
  }
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Module module = null;
    try {
      module = Module.load(assetFilePath(this, "model.ptl"));
    }
    catch(IOException ex){

    }
    pytorch_model = module;
    System.out.println("Hello, World.");
    int TAG_CODE_PERMISSION_LOCATION = 1;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requestPermissions(new String[]  {android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
    }
    int locationPermissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);

    if (locationPermissionCheck != PackageManager.PERMISSION_GRANTED) {
      Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    } else {
      Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
    }

    LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
    Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    double longitude = location.getLongitude();
    double latitude = location.getLatitude();
    // GET CITY NAME
    Geocoder gcd = new Geocoder(this, Locale.getDefault());
    List<Address> addresses = null;

    try {
      addresses = gcd.getFromLocation(latitude, longitude, 1);
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (addresses.size() > 0) {
      city = addresses.get(0).getLocality();
    }
    city = "Waterloo";
    lookup_data(city);
    setContentView(R.layout.activity_main);
    pieChart = findViewById(R.id.pieChart);
    pieChart.getLegend().setTextColor(Color.BLACK);
    pieChart.setEntryLabelColor(Color.BLACK);
    display_pie_chart();
    /*
    TODO:
    - Set up City Data inside of a table
      - Java Can't display heat maps

     */


  }

  /**
   * Copies specified asset to the file in /files app directory and returns this file absolute path.
   *
   * @return absolute file path
   */
  public static String assetFilePath(Context context, String assetName) throws IOException {
    System.out.println(assetName);
    File file = new File(context.getFilesDir(), assetName);
    if (file.exists() && file.length() > 0) {
      return file.getAbsolutePath();
    }

    try (InputStream is = context.getAssets().open(assetName)) {
      try (OutputStream os = new FileOutputStream(file)) {
        byte[] buffer = new byte[4 * 1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
          os.write(buffer, 0, read);
        }
        os.flush();
      }
      return file.getAbsolutePath();
    }
  }
}
