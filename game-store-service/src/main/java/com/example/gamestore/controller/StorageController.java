package com.example.gamestore.controller;

import com.example.gamestore.config.StorageProperties;
import com.example.gamestore.storage.S3StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/storage")
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
public class StorageController {
    private final S3StorageService storage; private final StorageProperties properties;
    public StorageController(S3StorageService storage, StorageProperties properties){this.storage=storage;this.properties=properties;}
    @GetMapping("/health") public Map<String,Object> health(){ return Map.of("enabled",true,"ready",storage.isReady(),"provider","S3-compatible","bucket",storage.bucket()); }
    @PostMapping(value="/images", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadImage(@RequestHeader(value="X-Storage-Admin-Token", required=false) String token, @RequestPart("file") MultipartFile file) throws Exception {
        if (properties.getAdminToken()==null || properties.getAdminToken().isBlank() || !properties.getAdminToken().equals(token)) return ResponseEntity.status(403).body(Map.of("error","Forbidden"));
        if (file.isEmpty() || file.getSize()>5*1024*1024) return ResponseEntity.badRequest().body(Map.of("error","Image must be between 1 byte and 5 MB"));
        String type=file.getContentType()==null?"":file.getContentType().toLowerCase(Locale.ROOT);
        String ext=switch(type){case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; case "image/gif" -> ".gif"; default -> null;};
        if(ext==null) return ResponseEntity.badRequest().body(Map.of("error","Only JPEG, PNG, WEBP and GIF images are allowed"));
        String key=storage.upload(file.getBytes(),type,ext);
        return ResponseEntity.ok(Map.of("key",key,"bucket",storage.bucket(),"downloadUrl",storage.downloadUrl(key)));
    }
}
