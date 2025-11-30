package com.example.musique;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    // Durée d'affichage du splash screen en millisecondes (3 secondes)
    private static final int SPLASH_DURATION = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Masquer la barre d'action et de statut pour un plein écran
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Optionnel : Masquer aussi la barre de statut
        // getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        //                      WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_splash);

        // Utiliser un Handler pour créer un délai
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Lancer l'activité principale après le délai
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // Fermer le splash screen
            }
        }, SPLASH_DURATION);
    }
}