package com.example.chifaaapp.patient;

abstract class PublicPage extends PatientActivity {
    @Override boolean protectedPage() { return false; }
}
