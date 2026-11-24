package com.example.chifaaapp.admin.directory;

import static com.example.chifaaapp.local.ApiRepository.*;
import static com.example.chifaaapp.directory.DirectoryLabels.*;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.example.chifaaapp.R;
import com.example.chifaaapp.local.LocalActivity;
import com.google.gson.*;

public class AddOrganisationActivity extends LocalActivity {
    @Override protected int title() { return R.string.e3_add; }

    @Override protected void setup(Bundle state) {
        if (id() == null) form(new JsonObject());
        else {
            String id = id();
            model.run(() -> execute(API.organisation(id)));
        }
    }

    private void form(JsonObject existing) {
        single(card -> {
            var name = input(card,R.string.e3_name,InputType.TYPE_CLASS_TEXT,value(existing,"name"));
            var category = options(card,R.string.e3_category,getResources().getStringArray(R.array.e3_categories),
                    index(CATEGORIES,value(existing,"serviceType")));
            var governorate = options(card,R.string.e3_governorate,getResources().getStringArray(R.array.e3_governorates),
                    index(GOVERNORATES,value(existing,"governorate")));
            var cost = options(card,R.string.e3_cost,getResources().getStringArray(R.array.e3_costs),
                    existing.has("cost") ? index(COSTS,value(existing,"cost")) : COSTS.length-1);
            var phone = input(card,R.string.e3_phone,InputType.TYPE_CLASS_PHONE,value(existing,"contactPhone"));
            var address = input(card,R.string.e3_address,InputType.TYPE_CLASS_TEXT,value(existing,"address"));
            text(card,getString(R.string.e3_languages),true);
            var languageLabels = getResources().getStringArray(R.array.e3_language_labels);
            var checks = new CheckBox[LANGUAGES.length];
            for (int i=0;i<LANGUAGES.length;i++) {
                var check=new CheckBox(this);
                checks[i]=check;
                check.setText(languageLabels[i]);
                check.setMinHeight(dim(R.dimen.e3_touch));
                check.setChecked(existing.has("languages") && existing.getAsJsonArray("languages").contains(new JsonPrimitive(LANGUAGES[i])));
                card.addView(check);
            }
            button(card,R.string.e3_save,() -> {
                if (name.getText().toString().trim().isEmpty()) {
                    name.setError(getString(R.string.e3_input_required));
                    return;
                }
                var request = new JsonObject();
                request.addProperty("name",name.getText().toString().trim());
                request.addProperty("serviceType",CATEGORIES[category.getSelectedItemPosition()]);
                request.addProperty("governorate",GOVERNORATES[governorate.getSelectedItemPosition()]);
                request.addProperty("cost",COSTS[cost.getSelectedItemPosition()]);
                request.addProperty("contactPhone",phone.getText().toString().trim());
                request.addProperty("address",address.getText().toString().trim());
                var languages = new JsonArray();
                for (int i=0;i<checks.length;i++) if (checks[i].isChecked()) languages.add(LANGUAGES[i]);
                request.add("languages",languages);
                String id = id();
                new AlertDialog.Builder(this).setTitle(R.string.e3_confirm).setNegativeButton(R.string.e3_cancel,null)
                    .setPositiveButton(R.string.e3_save,(dialog,which) -> model.run(() -> {
                        execute(id == null ? API.createOrganisation(request) : API.updateOrganisation(id,request));
                        return true;
                    })).show();
            });
        });
    }

    @Override protected void onResult(Object result) {
        if (Boolean.TRUE.equals(result)) {
            Toast.makeText(this,R.string.e3_saved,Toast.LENGTH_SHORT).show();
            finish();
        } else form((JsonObject)result);
    }
}
