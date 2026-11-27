package com.example.chifaaapp.patient;

import java.util.List;

public final class Models {
    private Models() {}
    public static class Profile {
        public String ageBand, governorate, preferredLanguage;
        public Profile(String ageBand,String governorate,String preferredLanguage) {
            this.ageBand=ageBand; this.governorate=governorate; this.preferredLanguage=preferredLanguage;
        }
    }
    public static class Register {
        public String phone,version="v1.0";
        public boolean adult,service,health,survey;
        public Profile profile;
    }
    public static class Login { public String phone; Login(String phone) { this.phone=phone; } }
    public static class Verify {
        public String challengeId,code;
        Verify(String id,String code) { this.challengeId=id; this.code=code; }
    }
    public static class Challenge { public String challengeId,expiresAt; }
    public static class Session { public String token,expiresAt,userPid; }
    public static class Evidence { public String id,purpose,version,status,givenAt,withdrawnAt,method; }
    public static class Change {
        public String purpose,version="v1.0";
        public boolean given;
        public Change(String purpose,boolean given) { this.purpose=purpose; this.given=given; }
    }
    public static class Health {
        public String cancerType,carePhase,diagnosisPeriodBand,facilityType;
        public Integer diagnosisYear;
        public Boolean fertilityInfoInterest;
        public List<String> treatments;
    }
    public static class Checkin { public String id,date; public List<String> effects; }
    public static class Catalog { public List<String> carePhases,sideEffects; }
    public static class Item { public String title,contentText; }
    public static class Request { public String requestId; }
}
