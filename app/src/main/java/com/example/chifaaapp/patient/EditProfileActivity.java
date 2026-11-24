package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class EditProfileActivity extends PatientActivity {
    @Override int layout() { return R.layout.patient_edit_profile; }
    @Override void refresh() {
        show(R.id.save,false);
        request(api.profile(),p->{ profileFields(p); show(R.id.save,true); click(R.id.save,()->{
            var edit=profileInput(); if (edit!=null) request(api.edit(edit),ignored->finish());
        }); });
    }
}
