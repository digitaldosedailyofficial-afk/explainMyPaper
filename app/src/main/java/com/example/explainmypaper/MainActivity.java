
package com.example.explainmypaper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private TextView resultTextView;
    private ImageView imageView;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultTextView = findViewById(R.id.resultTextView);
        imageView = findViewById(R.id.imageView);
        Button captureButton = findViewById(R.id.captureButton);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("mr")); // Marathi
            }
        });

        captureButton.setOnClickListener(v -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            imageView.setImageBitmap(imageBitmap);
            processImage(imageBitmap);
        }
    }

   private void processImage(Bitmap bitmap) {
    InputImage image = InputImage.fromBitmap(bitmap, 0);

    // Initialize TextRecognizerOptions with the language tag for Marathi (mr)
    TextRecognizerOptions options = new TextRecognizerOptions.Builder().setLanguageTag("mr").build();
    
    TextRecognition.getClient(options)
            .process(image)
            .addOnSuccessListener(visionText -> {
                String resultText = visionText.getText();
                resultTextView.setText(resultText);
                tts.speak("हे कागदपत्र असे म्हणते: " + resultText, TextToSpeech.QUEUE_FLUSH, null, null);
            })
            .addOnFailureListener(e -> resultTextView.setText("ओळखण्यात अडचण आली."));
}

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
