package com.example.androiddiffusion;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.androiddiffusion.ml.NativeOptimizations;

/**
 * Test activity to verify that IntelliSense is working correctly.
 * This activity is only used for development purposes.
 */
public class IntelliSenseTestActivity extends AppCompatActivity {
    private static final String TAG = "IntelliSenseTest";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intellisense_test);
        
        TextView resultTextView = findViewById(R.id.test_result);
        
        try {
            NativeOptimizations nativeOpt = new NativeOptimizations();
            boolean testResult = nativeOpt.testIntelliSense();
            
            String resultMessage = testResult 
                ? "IntelliSense Test: SUCCESS" 
                : "IntelliSense Test: FAILED";
            
            Log.i(TAG, resultMessage);
            resultTextView.setText(resultMessage);
            
            // Additional information
            boolean deviceSupported = nativeOpt.isDeviceSupported();
            int optimalThreads = nativeOpt.getOptimalNumThreads();
            
            String additionalInfo = String.format(
                "Device supported: %s\nOptimal threads: %d",
                deviceSupported ? "Yes" : "No",
                optimalThreads
            );
            
            TextView infoTextView = findViewById(R.id.additional_info);
            infoTextView.setText(additionalInfo);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in IntelliSense test", e);
            resultTextView.setText("IntelliSense Test: ERROR - " + e.getMessage());
        }
    }
} 