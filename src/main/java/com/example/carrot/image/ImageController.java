package com.example.carrot.image;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RequiredArgsConstructor
@RestController
public class ImageController {

    private final S3UploaderService s3UploaderService;

    @PostMapping("/api/images")
    public String upload(@RequestParam("images") String images) throws IOException {
        s3UploaderService.upload(images, "static");
        return "test";
    }

    @DeleteMapping("/api/images")
    public String deleteFile(@RequestParam("filename") String filename) {
        System.out.println(filename);
        s3UploaderService.deleteImage(filename);
        return "delete";
    }

}
