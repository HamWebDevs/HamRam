package dev.hamweb.stayopen.ramfixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Isolated test target, never bundled into StayOpen. No services or permissions. */
public final class MemoryFixtureActivity extends Activity {
    private static byte[][] memory;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        memory = new byte[24][];
        for (int i = 0; i < memory.length; i++) {
            memory[i] = new byte[1024 * 1024];
            for (int j = 0; j < memory[i].length; j += 4096) memory[i][j] = (byte) (i + 1);
        }
        TextView text = new TextView(this);
        text.setText("RAM clearing test fixture · 24 MiB allocation");
        text.setTextSize(24);
        text.setPadding(24, 64, 24, 24);
        setContentView(text);
    }
}
