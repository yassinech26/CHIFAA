package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class MyDataActivity extends PatientActivity {
    @Override int layout() { return R.layout.patient_my_data; }
    @Override void bind() {
        click(R.id.manage_consents,()->go(ManageConsentsActivity.class));
        click(R.id.export,()->go(ExportDataActivity.class));
        click(R.id.delete_account,()->go(DeleteAccountRequestActivity.class));
    }
}
