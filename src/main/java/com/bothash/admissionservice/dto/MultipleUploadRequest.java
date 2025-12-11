package com.bothash.admissionservice.dto;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultipleUploadRequest {
    private List<UploadRequest> files;
}
