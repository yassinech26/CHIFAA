package com.example.chifaaapp.local;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import com.example.chifaaapp.*;
import com.example.chifaaapp.databinding.ActivityStudent3Binding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.*;
import retrofit2.HttpException;

public abstract class LocalActivity extends AppCompatActivity {
    protected ActivityStudent3Binding ui;
    protected ScreenViewModel model;
    private final Cards cards = new Cards();

    @Override protected void onCreate(Bundle state) {
        setTheme(R.style.Theme_ChifaaE3);
        super.onCreate(state);
        EdgeToEdge.enable(this);
        ui = ActivityStudent3Binding.inflate(getLayoutInflater());
        setContentView(ui.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(ui.getRoot(), (view, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            return insets;
        });
        ui.list.setLayoutManager(new LinearLayoutManager(this));
        ui.list.setAdapter(cards);
        ui.toolbar.setTitle(title());
        if (!(this instanceof MainActivity)) {
            ui.toolbar.setNavigationIcon(R.drawable.e3_back);
            ui.toolbar.setNavigationContentDescription(R.string.e3_back);
            ui.toolbar.setNavigationOnClickListener(v -> finish());
        }
        model = new ViewModelProvider(this).get(viewModelType());
        setup(state);
        model.busy().observe(this, busy -> ui.progress.setVisibility(busy ? View.VISIBLE : View.GONE));
        model.results().observe(this, result -> {
            if (!result.consume()) return;
            if (result.error == null) onResult(result.value);
            else onFailure(result.error);
        });
    }

    protected abstract @StringRes int title();
    protected Class<? extends ScreenViewModel> viewModelType() { return ScreenViewModel.class; }
    protected abstract void setup(Bundle state);
    protected void onResult(Object result) {}
    protected void onFailure(Exception error) {
        if (error instanceof HttpException && ((HttpException)error).code() == 401) {
            RetrofitClient.clear();
            notice(getString(R.string.e3_unauthorized));
        } else if (error instanceof HttpException && ((HttpException)error).code() == 403) {
            notice(getString(R.string.e3_forbidden));
        } else notice(getString(error instanceof java.io.IOException ? R.string.e3_network : R.string.e3_error));
    }

    protected void notice(CharSequence value) {
        ui.status.setText(value);
        ui.status.setVisibility(value.length() == 0 ? View.GONE : View.VISIBLE);
    }

    protected int dim(int id) { return getResources().getDimensionPixelSize(id); }
    protected int color(int id) { return ContextCompat.getColor(this,id); }

    protected Locale screenLocale() { return getResources().getConfiguration().getLocales().get(0); }

    protected String displayTimestamp(String isoTimestamp) {
        try {
            return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, screenLocale())
                    .format(Date.from(java.time.Instant.parse(isoTimestamp)));
        } catch (java.time.format.DateTimeParseException invalid) {
            return isoTimestamp;
        }
    }

    protected MaterialButton button(LinearLayout container, int label, Runnable action) {
        var button = new MaterialButton(this);
        button.setText(label);
        button.setMinHeight(dim(R.dimen.e3_touch));
        button.setCornerRadius(dim(R.dimen.e3_button_radius));
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        container.addView(button);
        return button;
    }

    protected EditText input(LinearLayout container, int label, int inputType, String value) {
        var box = new com.google.android.material.textfield.TextInputLayout(this,null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        box.setHint(getString(label));
        var input = new com.google.android.material.textfield.TextInputEditText(box.getContext());
        input.setInputType(inputType);
        input.setMinHeight(dim(R.dimen.e3_touch));
        input.setText(value);
        box.addView(input);
        container.addView(box);
        return input;
    }

    protected Spinner options(LinearLayout container, int label, String[] values, int selected) {
        text(container,getString(label),false);
        var spinner = new Spinner(this);
        spinner.setMinimumHeight(dim(R.dimen.e3_touch));
        spinner.setContentDescription(getString(label));
        var adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0,selected));
        container.addView(spinner);
        return spinner;
    }

    protected TextView text(LinearLayout container, CharSequence value, boolean title) {
        var view = new TextView(this);
        view.setText(value);
        view.setTextColor(color(R.color.text_primary));
        view.setTextSize(title ? 18 : 16);
        if (title) ViewCompat.setAccessibilityHeading(view,true);
        container.addView(view);
        return view;
    }


    protected void open(Class<?> screen) { open(screen,null); }
    protected void open(Class<?> screen, String id) {
        var intent = new Intent(this,screen);
        if (id != null) intent.putExtra("id",id);
        startActivity(intent);
    }

    protected String id() { return getIntent().getStringExtra("id"); }
    protected void show(List<java.util.function.Consumer<LinearLayout>> rows) { cards.replace(rows); }
    protected void single(java.util.function.Consumer<LinearLayout> row) { show(Collections.singletonList(row)); }

    private final class Cards extends RecyclerView.Adapter<Cards.Holder> {
        private final List<java.util.function.Consumer<LinearLayout>> rows = new ArrayList<>();
        void replace(List<java.util.function.Consumer<LinearLayout>> values) {
            rows.clear();
            rows.addAll(values);
            notifyDataSetChanged();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final LinearLayout body;
            Holder(MaterialCardView card, LinearLayout body) { super(card); this.body = body; }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            var card = new MaterialCardView(parent.getContext());
            var params = new RecyclerView.LayoutParams(-1,-2);
            params.bottomMargin = dim(R.dimen.e3_gap);
            card.setLayoutParams(params);
            card.setRadius(dim(R.dimen.e3_card_radius));
            card.setCardElevation(1);
            card.setCardBackgroundColor(color(R.color.surface));
            var body = new LinearLayout(parent.getContext());
            body.setOrientation(LinearLayout.VERTICAL);
            int inset = dim(R.dimen.e3_gap) * 2;
            body.setPadding(inset,inset,inset,inset);
            card.addView(body,new android.widget.FrameLayout.LayoutParams(-1,-2));
            return new Holder(card,body);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            holder.body.removeAllViews();
            rows.get(position).accept(holder.body);
        }

        @Override public int getItemCount() { return rows.size(); }
    }
}
