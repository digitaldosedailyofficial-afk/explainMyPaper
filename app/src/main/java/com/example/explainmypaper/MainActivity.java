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
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE = 1;
    private ImageView imageView;
    private TextView resultText;
    private Bitmap selectedImageBitmap;
    private TextToSpeech textToSpeech;
    private Button scanButton, playButton, stopButton;
    private GenerativeModelFutures model;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Replace with your actual API key
        String apiKey = "YOUR_API_KEY";
        model = GenerativeModelFutures.from(new GenerativeModel("gemini-pro", apiKey));

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
            if (!text.isEmpty() && textToSpeech != null) {
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

        String prompt = "तुम्हाला एक कागदपत्र मिळाले आहे. त्यातील सर्वात महत्त्वाची माहिती, जसे की बिल क्रमांक, ग्राहकाचे नाव, पत्ता आणि एकूण रक्कम मराठीमध्ये थोडक्यात सांगा. जर माहिती उपलब्ध नसेल तर 'उपलब्ध नाही' असे लिहा. \n\n" + recognizedText;
        Content content = new Content.Builder().addText(prompt).build();

        ListenableFuture<GenerateContentResponse> future = model.generateContent(content);
        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String response = result.getText();
                runOnUiThread(() -> {
                    resultText.setText(response);
                    if (response != null && !response.isEmpty()) {
                        textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                });
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e("GenerativeAI", "Error: " + t.getMessage());
                runOnUiThread(() -> resultText.setText("AI प्रतिसाद मिळवण्यात अयशस्वी: " + t.getMessage()));
            }
        }, executorService);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        executorService.shutdown();
        super.onDestroy();
    }
}