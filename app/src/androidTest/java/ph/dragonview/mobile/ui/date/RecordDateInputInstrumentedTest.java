package ph.dragonview.mobile.ui.date;

import static org.junit.Assert.assertEquals;

import android.text.InputType;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Checks the actual Android text watcher used by recording forms. */
@RunWith(AndroidJUnit4.class)
public final class RecordDateInputInstrumentedTest {
    @Test public void typingAndBackspacingKeepTheDateMaskEditable() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            EditText field = new EditText(InstrumentationRegistry.getInstrumentation()
                    .getTargetContext());
            field.setInputType(InputType.TYPE_CLASS_NUMBER);
            RecordDateInput.attach(field);
            field.append("0");
            field.append("9");
            assertEquals("09/", field.getText().toString());
            field.getText().delete(2, 3);
            assertEquals("0", field.getText().toString());
            field.setText("");
            for (char digit : "09292026".toCharArray()) field.append(String.valueOf(digit));
            assertEquals("09/29/2026", field.getText().toString());
            field.getText().replace(1, 2, "8");
            assertEquals("08/29/2026", field.getText().toString());
        });
    }
}
