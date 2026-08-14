package com.jobseekercopilot.documentexport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional owner-authorised professional contact details to render in the document header")
public class ProfessionalContact {

    public static final int MAX_PHONE_LENGTH = 40;
    public static final int MAX_LINKS = 8;
    public static final String PHONE_PATTERN =
            "^\\s*$|^(?=(?:\\D*\\d){7,15}\\D*$)[+0-9() .-]+$";

    @Size(max = MAX_PHONE_LENGTH)
    @Pattern(regexp = PHONE_PATTERN)
    @Schema(
            description = "Optional user-declared professional telephone number",
            example = "+44 20 7946 0958",
            maxLength = MAX_PHONE_LENGTH,
            pattern = PHONE_PATTERN)
    private String phone;

    @Size(max = MAX_LINKS)
    private List<@NotNull @Valid ProfessionalLink> links = new ArrayList<>();

    @Override
    public String toString() {
        return "ProfessionalContact(redacted)";
    }
}
