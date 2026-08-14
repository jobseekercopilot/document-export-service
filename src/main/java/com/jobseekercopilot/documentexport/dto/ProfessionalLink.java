package com.jobseekercopilot.documentexport.dto;

import com.jobseekercopilot.documentexport.validation.HttpsUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A user-declared labelled professional HTTPS link")
public class ProfessionalLink {

    public static final int MAX_LABEL_LENGTH = 40;
    public static final int MAX_URL_LENGTH = 512;

    @NotBlank
    @Size(min = 1, max = MAX_LABEL_LENGTH)
    @Pattern(regexp = "^[^\\p{Cc}]+$")
    @Schema(example = "GitHub", maxLength = MAX_LABEL_LENGTH)
    private String label;

    @NotBlank
    @Size(min = 9, max = MAX_URL_LENGTH)
    @HttpsUrl
    @Schema(
            example = "https://github.com/example-developer",
            pattern = "^https://",
            maxLength = MAX_URL_LENGTH)
    private String url;

    @Override
    public String toString() {
        return "ProfessionalLink(redacted)";
    }
}
