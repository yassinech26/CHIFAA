package com.example.chifaaapp.directory;

import static com.example.chifaaapp.local.ApiRepository.value;
import static com.example.chifaaapp.directory.DirectoryLabels.*;
import android.net.*;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;
import com.example.chifaaapp.R;
import com.example.chifaaapp.local.LocalActivity;
import java.text.Normalizer;
import java.util.*;

public class DirectoryActivity extends LocalActivity {
    private static final int[] CATEGORY_ICONS = {R.drawable.e3_category_oncology,R.drawable.e3_category_psychology,
        R.drawable.e3_category_legal,R.drawable.e3_category_fertility,R.drawable.e3_category_financial,
        R.drawable.e3_category_peers,R.drawable.e3_category_prostheses,R.drawable.e3_category_transport,
        R.drawable.e3_category_cnam};
    private EditText search;
    private Spinner governorate, category;
    private DirectoryRepository.Result directory;
    private ConnectivityManager connectivity;
    private final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network active) { runOnUiThread(() -> { if (directory == null || directory.offline) refresh(); }); }
    };

    @Override protected int title() { return R.string.e3_directory; }
    @Override protected Class<? extends com.example.chifaaapp.local.ScreenViewModel> viewModelType() { return DirectoryViewModel.class; }
    @Override protected void setup(Bundle state) {
        search = input(ui.header,R.string.e3_search,android.text.InputType.TYPE_CLASS_TEXT,"");
        governorate = options(ui.header,R.string.e3_governorate,withAll(this,R.array.e3_governorates),0);
        category = options(ui.header,R.string.e3_category,withAll(this,R.array.e3_categories),0);
        button(ui.footer,R.string.e3_reset,() -> { search.setText(""); governorate.setSelection(0); category.setSelection(0); });
        button(ui.footer,R.string.e3_refresh,this::refresh);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { render(); }
            public void afterTextChanged(Editable s) {}
        });
        var listener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> view, View item, int position, long id) { render(); }
            public void onNothingSelected(AdapterView<?> view) {}
        };
        governorate.setOnItemSelectedListener(listener);
        category.setOnItemSelectedListener(listener);
        connectivity = getSystemService(ConnectivityManager.class);
    }

    @Override protected void onStart() { super.onStart(); connectivity.registerDefaultNetworkCallback(network); refresh(); }
    @Override protected void onStop() { connectivity.unregisterNetworkCallback(network); super.onStop(); }
    private void refresh() { ((DirectoryViewModel) model).refresh(getApplicationContext()); }
    @Override protected void onResult(Object result) { directory = (DirectoryRepository.Result)result; render(); }

    private void render() {
        if (directory == null) return;
        String query = normalize(search.getText().toString());
        String region = filter(GOVERNORATES,governorate.getSelectedItemPosition());
        String type = filter(CATEGORIES,category.getSelectedItemPosition());
        var rows = new ArrayList<java.util.function.Consumer<LinearLayout>>();
        for (var item : directory.items) {
            if (!normalize(value(item,"name")).contains(query)) continue;
            if (!region.isEmpty() && !region.equals(value(item,"governorate"))) continue;
            if (!type.isEmpty() && !type.equals(value(item,"serviceType"))) continue;
            rows.add(card -> {
                text(card,value(item,"name"),true);
                text(card,label(this,R.array.e3_governorates,GOVERNORATES,value(item,"governorate")),false);
                var typeLabel = text(card,label(this,R.array.e3_categories,CATEGORIES,value(item,"serviceType")),false);
                int categoryIndex = index(CATEGORIES,value(item,"serviceType"));
                if (categoryIndex >= 0) typeLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(CATEGORY_ICONS[categoryIndex],0,0,0);
                typeLabel.setCompoundDrawablePadding(dim(R.dimen.e3_gap));
                var cost = text(card,label(this,R.array.e3_costs,COSTS,value(item,"cost")),false);
                cost.setTextColor(color(R.color.primary));
                button(card,R.string.e3_detail,() -> open(DirectoryDetailsActivity.class,value(item,"id")));
            });
        }
        String date = displayTimestamp(java.time.Instant.ofEpochMilli(directory.synchronizedAt).toString());
        notice(getString(directory.offline ? R.string.e3_offline : R.string.e3_synced,date) + "\n" +
                (rows.isEmpty() ? getString(R.string.e3_empty) : getString(R.string.e3_results,rows.size())));
        show(rows);
    }

    static String normalize(String text) {
        return Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","");
    }
}
