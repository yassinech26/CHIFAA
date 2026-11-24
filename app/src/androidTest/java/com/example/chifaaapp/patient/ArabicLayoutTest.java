package com.example.chifaaapp.patient;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.chifaaapp.R;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArabicLayoutTest {
    @Test public void allPatientScreensResolveArabicTitlesAndRtlLayout() {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(()->{
            Context context=instrumentation.getTargetContext();
            Configuration config=new Configuration(context.getResources().getConfiguration());
            config.setLocale(Locale.forLanguageTag("ar"));
            Context arabic=new ContextThemeWrapper(context.createConfigurationContext(config),R.style.PatientTheme);
            assertEquals(View.LAYOUT_DIRECTION_RTL,arabic.getResources().getConfiguration().getLayoutDirection());
            int[] layouts={R.layout.patient_welcome,
                R.layout.patient_age,
                R.layout.patient_service,
                R.layout.patient_health_consent,
                R.layout.patient_survey_consent,
                R.layout.patient_register,
                R.layout.patient_login,
                R.layout.patient_otp,
                R.layout.patient_profile,
                R.layout.patient_edit_profile,
                R.layout.patient_language_settings,
                R.layout.patient_health_home,
                R.layout.patient_health_profile,
                R.layout.patient_edit_health,
                R.layout.patient_fertility,
                R.layout.patient_create_checkin,
                R.layout.patient_history,
                R.layout.patient_details,
                R.layout.patient_chart,
                R.layout.patient_my_data,
                R.layout.patient_manage_consents,
                R.layout.patient_export,
                R.layout.patient_delete_account};
            FrameLayout host=new FrameLayout(arabic);
            host.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            for (int layout:layouts) {
                View page=LayoutInflater.from(arabic).inflate(layout,host,false);
                host.addView(page);
                int width=View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY);
                host.measure(width,View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));
                host.layout(0,0,720,1280);
                var column=(android.view.ViewGroup)((android.view.ViewGroup)page).getChildAt(0);
                TextView title=(TextView)column.getChildAt(0);
                assertTrue(title.getText().toString().matches(".*[\\u0600-\\u06FF].*"));
                assertEquals(View.LAYOUT_DIRECTION_RTL,title.getLayoutDirection());
                assertTrue(title.getWidth()>0);
                host.removeAllViews();
            }
            assertEquals(24,arabic.getResources().getStringArray(R.array.governorate_labels).length);
        });
    }

    @Test public void easternArabicOtpDigitsProduceAsciiRequest() {
        try (var screen=ActivityScenario.launch(OtpVerificationActivity.class)) {
            screen.onActivity(activity->{
                EditText code=activity.findViewById(R.id.otp_code);
                code.setText("١٢٣٤٥٦");
                assertEquals("123456",activity.input(R.id.otp_code));
            });
        }
    }
}
