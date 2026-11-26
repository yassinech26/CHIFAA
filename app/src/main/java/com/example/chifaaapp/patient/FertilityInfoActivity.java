package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class FertilityInfoActivity extends HealthPage {
    @Override int layout() { return R.layout.patient_fertility; }
    @Override void refresh() {
        String language="ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage())?"MSA":"French";
        request(api.items(language),items->{
            List<String> entries=new ArrayList<>();
            for (var item:items) entries.add(item.title+"\n"+item.contentText);
            value(R.id.result,items.isEmpty()?getString(R.string.empty):android.text.TextUtils.join("\n\n",entries));
            contentVisible(true);
        });
    }
}
