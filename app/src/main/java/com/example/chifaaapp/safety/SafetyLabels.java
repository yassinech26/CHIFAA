package com.example.chifaaapp.safety;

import android.content.Context;
import android.icu.text.MeasureFormat;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import com.example.chifaaapp.R;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class SafetyLabels {
    static final List<String> STATES = Arrays.asList("Open","In progress","Resolved","Closed");
    static final List<String> ACTIONS = Arrays.asList("Resource shared","Human contacted","Referral","None needed");
    static final List<String> OUTCOMES = Arrays.asList("Contacted","Attended","Declined","Unknown","N/A");
    private static final List<String> SEVERITY = Arrays.asList("Low","Medium","High","Critical");
    final Context context;
    SafetyLabels(Context context) { this.context = context; }

    String label(String value, List<String> codes, int resource) {
        int index = codes.indexOf(value);
        return index < 0 ? optional(value) : context.getResources().getStringArray(resource)[index];
    }

    String optional(Object value) { return value == null ? context.getString(R.string.b3_none) : value.toString(); }

    String meta(SafetyRepository.Event event) {
        return context.getString(R.string.b3_meta, label(event.severity, SEVERITY, R.array.b3_severity),
                label(event.status, STATES, R.array.b3_states));
    }

    String date(String value) {
        if (value == null) return context.getString(R.string.b3_pending);
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        parser.setTimeZone(TimeZone.getTimeZone("UTC")); // Le contrat B3 renvoie des Instant en UTC.
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        try { return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale).format(parser.parse(value)); }
        catch (ParseException invalid) { return value; }
    }

    String delay(Long seconds) {
        if (seconds == null) return context.getString(R.string.b3_pending);
        List<Measure> parts = new ArrayList<>();
        if (seconds >= 3600) parts.add(new Measure(seconds / 3600, MeasureUnit.HOUR));
        if (seconds >= 60) parts.add(new Measure(seconds / 60 % 60, MeasureUnit.MINUTE));
        parts.add(new Measure(seconds % 60, MeasureUnit.SECOND));
        return MeasureFormat.getInstance(context.getResources().getConfiguration().getLocales().get(0),
                MeasureFormat.FormatWidth.SHORT).formatMeasures(parts.toArray(new Measure[0]));
    }

    void options(Spinner spinner, List<String> labels, int selection) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selection);
    }

    void withEmpty(Spinner spinner, int empty, int labels, int selection) {
        List<String> items = new ArrayList<>();
        items.add(context.getString(empty));
        items.addAll(Arrays.asList(context.getResources().getStringArray(labels)));
        options(spinner, items, selection);
    }
}
