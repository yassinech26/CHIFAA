package com.example.chifaaapp.admin.directory;

import static com.example.chifaaapp.local.ApiRepository.*;
import android.os.Bundle;
import android.widget.*;
import com.example.chifaaapp.R;
import com.example.chifaaapp.local.LocalActivity;
import com.google.gson.*;
import java.util.*;

public class AdminDirectoryActivity extends LocalActivity {
    private int page;
    private boolean stale;
    private final List<JsonObject> items = new ArrayList<>();
    private com.google.android.material.button.MaterialButton more;

    @Override protected int title() { return R.string.e3_manage; }
    @Override protected void setup(Bundle state) {
        button(ui.header,R.string.e3_add,() -> open(AddOrganisationActivity.class));
        var toggle = new CheckBox(this);
        toggle.setText(R.string.e3_due);
        toggle.setMinHeight(dim(R.dimen.e3_touch));
        ui.header.addView(toggle);
        toggle.setOnCheckedChangeListener((view,value) -> { stale=value; reload(); });
        more = button(ui.footer,R.string.e3_older,() -> load(page));
        button(ui.footer,R.string.e3_refresh,this::reload);
    }

    @Override protected void onStart() { super.onStart(); reload(); }

    private void reload() { load(0); }
    private void load(int current) {
        boolean filter = stale;
        model.run(() -> {
            var result = execute(API.adminDirectory(current,filter));
            result.addProperty("staleFilter",filter);
            return result;
        });
    }

    @Override protected void onResult(Object result) {
        var response = (JsonObject)result;
        if (response.get("staleFilter").getAsBoolean() != stale) { reload(); return; }
        page=response.get("page").getAsInt()+1;
        if (page==1) items.clear();
        for (var item : response.getAsJsonArray("items")) items.add(item.getAsJsonObject());
        more.setVisibility(response.get("hasNext").getAsBoolean() ? android.view.View.VISIBLE : android.view.View.GONE);
        var rows = new ArrayList<java.util.function.Consumer<LinearLayout>>();
        for (var item : items) rows.add(card -> {
            String id=value(item,"id");
            text(card,value(item,"name"),true);
            text(card,id,false);
            if (item.get("verificationDue").getAsBoolean()) text(card,getString(R.string.e3_due),false);
            button(card,R.string.e3_edit,() -> open(EditOrganisationActivity.class,id));
            button(card,R.string.e3_verify,() -> open(OrganisationVerificationActivity.class,id));
        });
        show(rows);
        notice(items.isEmpty() ? getString(R.string.e3_empty) : "");
    }
}
