package tn.chifaa;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tn.chifaa.auth.OtpDelivery;
import tn.chifaa.datarequests.DataService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="CHIFAA_TEST_DATABASE",matches="true")
class PatientJourneyTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired DataService data;
    @MockitoBean OtpDelivery sms;
    private final AtomicReference<String> code=new AtomicReference<>();

    @BeforeEach void prepare() {
        doAnswer(invocation->{ code.set(invocation.getArgument(1)); return null; }).when(sms).send(any(),anyString());
    }
    private String phone() {
        return "+1999"+String.format(Locale.ROOT,"%010d",new java.security.SecureRandom().nextInt(Integer.MAX_VALUE));
    }
    private ObjectNode registration(String phone,boolean health) {
        ObjectNode r=json.createObjectNode();
        r.put("phone",phone).put("adult",true).put("service",true).put("health",health).put("survey",false).put("version","v1.0");
        r.putObject("profile").put("ageBand","18–29").put("governorate","TN-11").put("preferredLanguage","French");
        return r;
    }
    private JsonNode call(MockHttpServletRequestBuilder req,String token,Object body,int status) throws Exception {
        if (token!=null) req.header("Authorization","Bearer "+token);
        if (body!=null) req.contentType("application/json").content(json.writeValueAsBytes(body));
        var response=http.perform(req).andExpect(status().is(status)).andReturn().getResponse();
        return response.getContentAsByteArray().length==0?json.nullNode():json.readTree(response.getContentAsByteArray());
    }
    private String register(String phone,boolean health) throws Exception {
        var ticket=call(post("/api/auth/register"),null,registration(phone,health),200);
        var verify=Map.of("challengeId",ticket.get("challengeId").asText(),"code",code.get());
        var token=call(post("/api/auth/verify"),null,verify,200).get("token").asText();
        call(post("/api/auth/verify"),null,verify,401);
        return token;
    }
    @Test void ageAndRequiredConsentAreEnforcedBeforeOtp() throws Exception {
        var r=registration(phone(),true);
        call(post("/api/auth/register"),null,r.put("adult",false),400);
        call(post("/api/auth/register"),null,r.put("adult",true).put("service",false),400);
        verifyNoInteractions(sms);
    }
    @Test void otpAttemptsAndResendsAreLimited() throws Exception {
        var r=registration(phone(),false);
        String id=call(post("/api/auth/register"),null,r,200).get("challengeId").asText();
        call(post("/api/auth/register"),null,r,429);
        String wrong="000000".equals(code.get())?"000001":"000000";
        for (int i=0;i<5;i++) call(post("/api/auth/verify"),null,Map.of("challengeId",id,"code",wrong),401);
        call(post("/api/auth/verify"),null,Map.of("challengeId",id,"code",code.get()),401);
    }
    @Test void ownDataConsentAndDeletionJourney() throws Exception {
        String phone=phone(), a=register(phone,true), b=register(phone(),false);
        JsonNode exported=call(get("/api/data/export"),a,null,200);
        assertEquals(phone,exported.get("phone").asText());
        var login=call(post("/api/auth/login"),null,Map.of("phone",phone),200);
        var secondSession=call(post("/api/auth/verify"),null,Map.of("challengeId",login.get("challengeId").asText(),"code",code.get()),200);
        call(get("/api/users/me"),secondSession.get("token").asText(),null,200);
        UUID pid=UUID.fromString(exported.get("userPid").asText());
        var identity=db.queryForMap("select phone_number_enc,phone_hash from identities where user_pid=?",pid);
        assertFalse(new String((byte[])identity.get("phone_number_enc"),java.nio.charset.StandardCharsets.UTF_8).contains(phone));
        assertEquals(64,identity.get("phone_hash").toString().length());
        assertEquals(2,exported.get("consents").size());
        call(get("/api/health/profile"),b,null,403);
        Map<String,Object> profile=Map.of("diagnosisYear",2020,"treatments",List.of("Surgery","Radiotherapy"),"fertilityInfoInterest",true);
        call(put("/api/health/profile"),a,profile,200);
        assertEquals(2,call(get("/api/health/profile"),a,null,200).get("treatments").size());
        // Synthetic fixture in the dedicated test DB; no catalog values are seeded in the product.
        db.update("insert into side_effect_catalog values ('AT-073-test-A') on conflict do nothing");
        var entry=call(post("/api/health/checkins"),a,Map.of("date",LocalDate.now().toString(),"effects",List.of("AT-073-test-A")),200);
        assertEquals("AT-073-test-A",entry.path("effects").get(0).asText());
        String detail="/api/health/checkins/"+entry.get("id").asText();
        call(get(detail),a,null,200);
        call(put("/api/consents"),b,Map.of("purpose","Health data","version","v1.0","given",true),200);
        call(get(detail),b,null,404);
        assertTrue(call(get("/api/data/export"),b,null,200).get("checkins").isEmpty());
        call(put("/api/consents"),a,Map.of("purpose","Health data","version","v1.0","given",false),200);
        call(get(detail),a,null,403);
        assertFalse(call(get("/api/data/export"),a,null,200).has("healthProfile"));
        String request=call(post("/api/data/deletion"),a,null,200).get("requestId").asText();
        assertEquals(request,call(post("/api/data/deletion"),a,null,200).get("requestId").asText());
        var proofs=db.queryForList("select consent_id from consents where user_pid=?",String.class,pid);
        data.executeDeletion(request);
        for (String table:List.of("users","identities","health_profiles","health_profile_treatments","health_checkins"))
            assertEquals(0,db.queryForObject("select count(*) from "+table+" where user_pid=?",Integer.class,pid));
        for (String proof:proofs) assertNull(db.queryForObject("select user_pid from consents where consent_id=?",UUID.class,proof));
        assertNull(db.queryForObject("select user_pid from data_requests where request_id=?",UUID.class,request));
        call(get("/api/users/me"),a,null,401);
        call(post("/api/auth/logout"),b,null,200);
        call(get("/api/users/me"),b,null,401);
    }
}
