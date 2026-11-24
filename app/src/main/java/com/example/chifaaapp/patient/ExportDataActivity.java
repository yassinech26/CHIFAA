package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class ExportDataActivity extends PatientActivity {
    @Override int layout() { return R.layout.patient_export; }
    private final androidx.activity.result.ActivityResultLauncher<String> jsonFile=registerForActivityResult(
        new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),uri->save(uri,"json"));
    private final androidx.activity.result.ActivityResultLauncher<String> csvFile=registerForActivityResult(
        new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),uri->save(uri,"csv"));
    @Override void bind() {
        click(R.id.json,()->jsonFile.launch("chifaa.json"));
        click(R.id.csv,()->csvFile.launch("chifaa.csv"));
    }
    private void save(android.net.Uri uri,String format) {
        if (uri==null || !repository.signedIn()) return;
        request(api.export(format),body->{
            if (body==null) { notice(R.string.network_error); return; }
            new Thread(()->{
                try (body; var out=getContentResolver().openOutputStream(uri,"wt")) {
                    if (out==null) throw new java.io.IOException();
                    byte[] buffer=new byte[8192]; var stream=body.byteStream(); int n;
                    while ((n=stream.read(buffer))!=-1) out.write(buffer,0,n);
                } catch (java.io.IOException e) { runOnUiThread(()->notice(R.string.unavailable)); return; }
                runOnUiThread(()->notice(R.string.saved));
            },"patient-export").start();
        });
    }
}
