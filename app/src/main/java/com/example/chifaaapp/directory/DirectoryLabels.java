package com.example.chifaaapp.directory;

import android.content.Context;
import java.util.*;
import com.example.chifaaapp.R;

public final class DirectoryLabels {
    public static final String[] GOVERNORATES = {"TN-11","TN-12","TN-13","TN-14","TN-21","TN-22","TN-23","TN-31",
        "TN-32","TN-33","TN-34","TN-41","TN-42","TN-43","TN-51","TN-52","TN-53","TN-61","TN-71","TN-72","TN-73","TN-81","TN-82","TN-83"};
    public static final String[] CATEGORIES = {"ONCOLOGY","PSYCHOLOGY","LEGAL","FERTILITY","FINANCIAL","PEER_SUPPORT",
        "PROSTHESES_WIGS","TRANSPORT","CNAM"};
    public static final String[] COSTS = {"FREE","COVERED_BY_CNAM","PAID","UNKNOWN"};
    public static final String[] LANGUAGES = {"French","Darija","MSA"};
    public static final String[] SEVERITIES = {"Low","Medium","High","Critical"};
    public static final String[] STATUSES = {"Open","In progress","Resolved","Closed"};

    private DirectoryLabels() {}

    public static int index(String[] keys, String key) { return Arrays.asList(keys).indexOf(key); }
    public static String label(Context ctx, int names, String[] keys, String key) {
        int index = index(keys,key);
        return index < 0 ? key : ctx.getResources().getStringArray(names)[index];
    }

    public static String[] withAll(Context ctx, int names) {
        var labels = ctx.getResources().getStringArray(names);
        var result = new String[labels.length + 1];
        result[0] = ctx.getString(R.string.e3_all);
        System.arraycopy(labels,0,result,1,labels.length);
        return result;
    }

    public static String filter(String[] values, int index) { return index <= 0 ? "" : values[index-1]; }
}
