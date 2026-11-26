package tn.chifaa.datarequests;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/data")
class DataController {
    private final DataService data;
    private final ObjectMapper json;
    DataController(DataService data,ObjectMapper json) { this.data=data; this.json=json; }
    @PostMapping("/deletion") DataService.Request delete(@AuthenticationPrincipal UUID pid) { return data.requestDeletion(pid); }
    @GetMapping("/export") ResponseEntity<?> export(@AuthenticationPrincipal UUID pid,@RequestParam(defaultValue="json") String format)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        if (!Set.of("json","csv").contains(format)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var result=data.export(pid);
        if ("json".equals(format)) return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
        var csv=new StringBuilder("section,json\r\n");
        for (var e:result.entrySet())
            csv.append(e.getKey()).append(",\"").append(json.writeValueAsString(e.getValue()).replace("\"","\"\"")).append("\"\r\n");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(csv.toString());
    }
}
