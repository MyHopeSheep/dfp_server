package com.dataframe.prase.controller;

import com.dataframe.prase.domain.vo.ExcelFileDownload;
import com.dataframe.prase.service.CsvUploadParseService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/dfp")
public class CsvFileParseController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CsvUploadParseService csvParseWebService;


    public CsvFileParseController(CsvUploadParseService csvParseWebService) {
        this.csvParseWebService = csvParseWebService;
    }


    @PostMapping(value = "/csvParse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> parse(
            @RequestParam("file") MultipartFile file,
            @RequestParam("configKey") String configKey) throws IOException {
        ExcelFileDownload download = csvParseWebService.parse(file, configKey);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }
}
