package com.troubadour.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import com.troubadour.utils.Logger;
import com.troubadour.utils.StringsUtils;

/**
 * Main activity of the application.
 */
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Logger.i("MainActivity created");
        // Example usage of StringsUtils
        String hello = StringsUtils.loc("Hello World"); // We'll need to define this key in strings.json
        TextView tv = findViewById(R.id.text_view);
        tv.setText(hello);
    }
}