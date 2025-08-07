package com.example.explainmypaper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import java.io.IOException;
import java.util.Locale;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.launch;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE = 1;
    private ImageView imageView;
    private TextView resultText;
    private Bitmap selectedImageBitmap;
    private TextToSpeech textToSpeech;
    private Button scanButton, playButton, stopButton;
    private GenerativeModel generativeModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Replace with your actual API key
        String apiKey = "AIzaSyDgTKZgFJD0Fvdhu41zS11TMf6LRsoDcUY"; 
        generativeModel = new GenerativeModel("gemini-pro", apiKey);

        imageView = findViewById(R.id.imageView);
        resultText = findViewById(R.id.resultText);
        scanButton = findViewById(R.id.scanButton);
        playButton = findViewById(R.id.playButton);
        stopButton = findViewById(R.id.stopButton);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("mar", "IND"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "Marathi language not supported.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "TTS initialization failed.", Toast.LENGTH_SHORT).show();
            }
        });

        scanButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_IMAGE);
        });

        playButton.setOnClickListener(v -> {
            String text = resultText.getText().toString();
            if (!text.isEmpty()) {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        stopButton.setOnClickListener(v -> {
            if (textToSpeech != null) {
                textToSpeech.stop();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                selectedImageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                imageView.setImageBitmap(selectedImageBitmap);
                runTextRecognition(selectedImageBitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void runTextRecognition(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        com.google.mlkit.vision.text.TextRecognizer recognizer = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());

        recognizer.process(image)
                .addOnSuccessListener(this::processTextWithAI)
                .addOnFailureListener(e -> resultText.setText("स्कॅन अयशस्वी: " + e.getMessage()));
    }

    private void processTextWithAI(Text visionText) {
        String recognizedText = visionText.getText();
        if (recognizedText.isEmpty()) {
            resultText.setText("काहीही ओळखले गेले नाही.");
            return;
        }

        CoroutineScope coroutineScope = new CoroutineScope(Dispatchers.getMain());
        coroutineScope.launch(() -> {
            try {
                String prompt = "Read the following document text and extract important information such as Bill Number, Customer Name, Address, and Total Amount. Please reply in Marathi. If any information is missing, say 'उपलब्ध नाही'.\n\n" + recognizedText;
                String response = generativeModel.generateContent(prompt).getText();

                // Run on the UI thread
                runOnUiThread(() -> {
                    resultText.setText(response);
                    textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
                });

            } catch (Exception e) {
                Log.e("GenerativeAI", "Error: " + e.getMessage());
                runOnUiThread(() -> resultText.setText("AI प्रतिसाद मिळवण्यात अयशस्वी: " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}