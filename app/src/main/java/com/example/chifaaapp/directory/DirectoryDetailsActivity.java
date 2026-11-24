package com.example.chifaaapp.directory;

import static com.example.chifaaapp.local.ApiRepository.*;
import static com.example.chifaaapp.directory.DirectoryLabels.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.example.chifaaapp.R;
import com.example.chifaaapp.local.LocalActivity;
import com.google.gson.*;
import java.io.IOException;
import java.time.*;
import java.util.*;

public class DirectoryDetailsActivity extends LocalActivity {
    @Override protected int title() { return R.string.e3_detail; }

    @Override protected void setup(Bundle state) {
        button(ui.footer,R.string.e3_refresh,this::load);
        load();
    }

    private void load() {
        var context = getApplicationContext();
        String id = id();
        model.run(() -> {
            try { return execute(API.organisation(id)); }
            catch (IOException | retrofit2.HttpException failure) {
                if (failure instanceof retrofit2.HttpException && ((retrofit2.HttpException)failure).code() < 500) throw failure;
                var cached = DirectoryRepository.cache(context).organisations().find(id);
                if (cached == null) throw failure;
                var item = JsonParser.parseString(cached.json).getAsJsonObject();
                item.addProperty("cachedAt",cached.synchronizedAt);
                return item;
            }
        });
    }

    @Override protected void onResult(Object result) {
        JsonObject item = (JsonObject)result;
        notice(item.has("cachedAt") ? getString(R.string.e3_offline,
            java.text.DateFormat.getDateTimeInstance().format(new Date(item.get("cachedAt").getAsLong()))) : "");
        single(card -> {
            text(card,value(item,"name"),true);
            text(card,label(this,R.array.e3_categories,CATEGORIES,value(item,"serviceType")),false);
            text(card,label(this,R.array.e3_governorates,GOVERNORATES,value(item,"governorate")),false);
            text(card,label(this,R.array.e3_costs,COSTS,value(item,"cost")),false);
            text(card,value(item,"address"),false);
            String phone = value(item,"contactPhone");
            boolean fictional = item.get("fictional").getAsBoolean();
            if (fictional) text(card,getString(R.string.e3_test_data),false);
            if (!phone.isEmpty()) text(card,phone,false);
            var languages = new ArrayList<String>();
            for (var language : item.getAsJsonArray("languages")) {
                languages.add(label(this,R.array.e3_language_labels,LANGUAGES,language.getAsString()));
            }
            text(card,String.join(", ",languages),false);
            String verified = value(item,"verifiedDate");
            if (!verified.isEmpty()) text(card,getString(R.string.e3_verified,verified),false);
            if (verified.isEmpty() || LocalDate.parse(verified).isBefore(LocalDate.now(ZoneOffset.UTC).minusMonths(12))) {
                text(card,getString(R.string.e3_due),false);
            }
            button(card,R.string.e3_dial,() -> {
                try { startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phone,null))); }
                catch (android.content.ActivityNotFoundException failure) { notice(getString(R.string.e3_error)); }
            }).setEnabled(!fictional && !phone.isEmpty());
        });
    }
}
