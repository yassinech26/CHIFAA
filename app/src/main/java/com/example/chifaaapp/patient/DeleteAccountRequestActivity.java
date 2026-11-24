package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class DeleteAccountRequestActivity extends PatientActivity {
    @Override int layout() { return R.layout.patient_delete_account; }
    @Override void bind() {
        click(R.id.confirm_delete,()->request(api.deletion(),receipt->{
            value(R.id.result,getString(R.string.request_received,receipt.requestId)); show(R.id.confirm_delete,false);
        }));
    }
}
