package com.example.speeddetectorsensorapp;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class MainActivity extends AppCompatActivity {

    private SensorManager sensorManager;
    private TextView speedTextView;
    private Button kmhButton, knotsButton, msButton;
    private ToggleButton toggleThemeButton;

    private String currentUnit = "ms"; // Default unit
    private static final String PREFS_NAME = "SpeedDetectorPrefs";
    private float currentSpeed = 0;

    private KalmanFilter kalmanFilter;
    private float[] gravity = new float[3];
    private float[] linearAcceleration = new float[3];
    private long lastUpdateTime;
    private float lastSpeed = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applyTheme();
        setContentView(R.layout.activity_main);

        speedTextView = findViewById(R.id.speedTextView);
        kmhButton = findViewById(R.id.kmhButton);
        knotsButton = findViewById(R.id.knotsButton);
        msButton = findViewById(R.id.msButton);
        toggleThemeButton = findViewById(R.id.toggleThemeButton);
        toggleThemeButton.setChecked(isDarkMode());

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        kalmanFilter = new KalmanFilter();

        Sensor accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(sensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);

        kmhButton.setOnClickListener(view -> {
            currentUnit = "kmh";
            updateSpeedDisplay();
        });

        knotsButton.setOnClickListener(view -> {
            currentUnit = "knots";
            updateSpeedDisplay();
        });

        msButton.setOnClickListener(view -> {
            currentUnit = "ms";
            updateSpeedDisplay();
        });

        toggleThemeButton.setOnClickListener(view -> {
            boolean isChecked = ((ToggleButton) view).isChecked();
            setTheme(isChecked);
        });

        lastUpdateTime = System.currentTimeMillis();
    }

    private void applyTheme() {
        boolean isDarkMode = isDarkMode();
        AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private boolean isDarkMode() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return preferences.getBoolean("isDarkMode", false);
    }

    private void setTheme(boolean isDarkMode) {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("isDarkMode", isDarkMode);
        editor.apply();

        AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        recreate();
    }

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                final float alpha = 0.8f;

                // Isolate the force of gravity with the low-pass filter.
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

                // Remove the gravity contribution with the high-pass filter.
                linearAcceleration[0] = event.values[0] - gravity[0];
                linearAcceleration[1] = event.values[1] - gravity[1];
                linearAcceleration[2] = event.values[2] - gravity[2];

                float acceleration = (float) Math.sqrt(
                        linearAcceleration[0] * linearAcceleration[0] +
                        linearAcceleration[1] * linearAcceleration[1] +
                        linearAcceleration[2] * linearAcceleration[2]
                );

                long currentTime = System.currentTimeMillis();
                float dt = (currentTime - lastUpdateTime) / 1000f; // Convert to seconds

                // Apply Kalman filter to smooth acceleration
                float filteredAcceleration = kalmanFilter.update(acceleration);

                // Integrate acceleration to get speed
                currentSpeed = lastSpeed + filteredAcceleration * dt;

                // Apply a simple low-pass filter to reduce noise
                currentSpeed = 0.9f * currentSpeed + 0.1f * lastSpeed;

                // Ensure speed is non-negative
                currentSpeed = Math.max(0, currentSpeed);

                lastSpeed = currentSpeed;
                lastUpdateTime = currentTime;

                updateSpeedDisplay();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Not used in this example
        }
    };

    private float convertSpeed(float speedMps, String unit) {
        switch (unit) {
            case "kmh":
                return speedMps * 3.6f;
            case "knots":
                return speedMps * 1.94384f;
            case "ms":
            default:
                return speedMps;
        }
    }

    private String getUnitSuffix(String unit) {
        switch (unit) {
            case "kmh":
                return " km/h";
            case "knots":
                return " knots";
            case "ms":
                return " m/s";
            default:
                return "";
        }
    }

    private void updateSpeedDisplay() {
        float displaySpeed = convertSpeed(currentSpeed, currentUnit);
        runOnUiThread(() -> speedTextView.setText(String.format("Speed: %.2f%s", displaySpeed, getUnitSuffix(currentUnit))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(sensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorEventListener);
    }
}
