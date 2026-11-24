package com.example.chifaaapp.admin.directory;

import static com.example.chifaaapp.local.ApiRepository.*;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.example.chifaaapp.R;
import com.example.chifaaapp.local.LocalActivity;
import com.google.gson.JsonObject;

public class OrganisationVerificationActivity extends LocalActivity {
    @Override protected int title() { return R.string.e3_verify; }

    @Override protected void setup(Bundle state) {
        single(card -> {
            text(card,id(),true);
            var method=input(card,R.string.e3_method,InputType.TYPE_CLASS_TEXT,"");
            button(card,R.string.e3_save,() -> {
                String text=method.getText().toString().trim();
                if (text.isEmpty()) { method.setError(getString(R.string.e3_input_required)); return; }
                var body=new JsonObject();
                body.addProperty("method",text);
                String id=id();
                new AlertDialog.Builder(this).setTitle(R.string.e3_confirm).setNegativeButton(R.string.e3_cancel,null)
                    .setPositiveButton(R.string.e3_save,(dialog,which) -> model.run(() -> execute(API.verify(id,body)))).show();
            });
        });
    }

    @Override protected void onResult(Object result) {
        Toast.makeText(this,R.string.e3_saved,Toast.LENGTH_SHORT).show();
        finish();
    }
}
