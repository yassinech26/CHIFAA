package com.example.chifaaapp.admin.safety;

import static com.example.chifaaapp.local.ApiRepository.*;
import static com.example.chifaaapp.directory.DirectoryLabels.*;
import android.os.Bundle;
import android.text.InputType;
import androidx.appcompat.app.AlertDialog;
import com.example.chifaaapp.R;
import com.example.chifaaapp.local.LocalActivity;
import com.google.gson.*;
import java.util.*;

public class SafetyEventDetailsActivity extends LocalActivity {
    @Override protected int title() { return R.string.e3_queue; }

    @Override protected void setup(Bundle state) {
        String id=id();
        button(ui.footer,R.string.e3_refresh,() -> load(id));
        load(id);
    }

    private void load(String id) { model.run(() -> details(id)); }

    private JsonObject details(String id) throws java.io.IOException {
        var event=execute(API.event(id));
        event.add("reviewers",execute(API.reviewers()));
        var organisations=new JsonArray();
        int page=0;
        JsonObject response;
        do {
            response=execute(API.directory(page++));
            organisations.addAll(response.getAsJsonArray("items"));
        } while (response.get("hasNext").getAsBoolean());
        event.add("organisations",organisations);
        return event;
    }

    @Override protected void onResult(Object result) {
        var event=(JsonObject)result;
        notice("");
        single(card -> {
            text(card,value(event,"id"),true);
            text(card,label(this,R.array.e3_severity_labels,SEVERITIES,value(event,"severity")),true);
            text(card,getString(R.string.e3_user_pseudonym,value(event,"userPid")),false);
            text(card,getString(R.string.e3_event_date,displayTimestamp(value(event,"flaggedAt"))),false);
            if (!value(event,"resolutionSeconds").isEmpty()) text(card,getString(R.string.e3_duration,value(event,"resolutionSeconds")),false);
            var states=options(card,R.string.e3_status,getResources().getStringArray(R.array.e3_status_labels),
                    index(STATUSES,value(event,"status")));
            var ids=new ArrayList<String>();
            var names=new ArrayList<String>();
            choices(event.getAsJsonArray("reviewers"),"username",ids,names);
            var reviewers=options(card,R.string.e3_reviewer,names.toArray(new String[0]),ids.indexOf(value(event,"reviewerId")));
            var orgIds=new ArrayList<String>();
            var orgNames=new ArrayList<String>();
            choices(event.getAsJsonArray("organisations"),"name",orgIds,orgNames);
            var organisations=options(card,R.string.e3_organisation,orgNames.toArray(new String[0]),orgIds.indexOf(value(event,"orgId")));
            var action=input(card,R.string.e3_action,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,value(event,"actionTaken"));
            var outcome=input(card,R.string.e3_outcome,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,value(event,"referralOutcome"));
            button(card,R.string.e3_save,() -> {
                var body=new JsonObject();
                body.addProperty("status",STATUSES[states.getSelectedItemPosition()]);
                if (reviewers.getSelectedItemPosition()>0) body.addProperty("reviewerId",ids.get(reviewers.getSelectedItemPosition()));
                if (organisations.getSelectedItemPosition()>0) body.addProperty("orgId",orgIds.get(organisations.getSelectedItemPosition()));
                body.addProperty("actionTaken",action.getText().toString().trim());
                body.addProperty("referralOutcome",outcome.getText().toString().trim());
                String id=id();
                new AlertDialog.Builder(this).setTitle(R.string.e3_confirm).setNegativeButton(R.string.e3_cancel,null)
                    .setPositiveButton(R.string.e3_save,(dialog,which)->model.run(()-> {
                        execute(API.updateEvent(id,body));
                        return details(id);
                    })).show();
            });
        });
    }

    private void choices(JsonArray items,String label,List<String> ids,List<String> names) {
        ids.add("");
        names.add(getString(R.string.e3_no_value));
        for (var item:items) {
            ids.add(value(item.getAsJsonObject(),"id"));
            names.add(value(item.getAsJsonObject(),label));
        }
    }
}
