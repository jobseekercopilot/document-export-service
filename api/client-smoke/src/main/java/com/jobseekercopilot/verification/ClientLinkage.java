package com.jobseekercopilot.verification;

import com.jobseekercopilot.generated.documentexportservice.api.DocumentExportsApi;
import com.jobseekercopilot.generated.documentexportservice.client.ApiClient;

final class ClientLinkage {

    private ClientLinkage() {
    }

    static DocumentExportsApi createClient() {
        return new DocumentExportsApi(new ApiClient());
    }
}
