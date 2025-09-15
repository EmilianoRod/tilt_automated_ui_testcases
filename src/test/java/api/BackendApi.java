package api;

import api.v1.*;
import api.v2.*;

public final class BackendApi {
    private final ApiClient client;

    public BackendApi(ApiConfig cfg) { this.client = new ApiClient(cfg); }
    public static BackendApi create(ApiConfig cfg) { return new BackendApi(cfg); }

    // ── V1 facades ──────────────────────────────────────────────────────────────
    public UserAssessmentsApiV1 userAssessmentsApiV1() {
        return client.create(UserAssessmentsApiV1.class);
    }
    public UsersApiV1 usersApiV1() {
        return client.create(UsersApiV1.class);
    }
    public UserAssessmentsAnswersApiV1 userAssessmentsAnswersApiV1() {
        return client.create(UserAssessmentsAnswersApiV1.class);
    }

    // ── V2 facades ──────────────────────────────────────────────────────────────
    public AssessmentsApiV2 assessmentsApiV2() {
        return client.create(AssessmentsApiV2.class);
    }
    public CouponCodesApiV2 couponCodesApiV2() {
        return client.create(CouponCodesApiV2.class);
    }
    public GoogleAuthsApiV2 googleAuthsApiV2() {
        return client.create(GoogleAuthsApiV2.class);
    }
    public ImpersonationsApiV2 impersonationsApiV2() {
        return client.create(ImpersonationsApiV2.class);
    }
    public IndividualsApiV2 individualsApiV2() {
        return client.create(IndividualsApiV2.class);
    }
    public MaintenanceWindowsApiV2 maintenanceWindowsApiV2() {
        return client.create(MaintenanceWindowsApiV2.class);
    }
    public OrganizationsApiV2 organizationsApiV2() {
        return client.create(OrganizationsApiV2.class);
    }
    public PasswordsApiV2 passwordsApiV2() {
        return client.create(PasswordsApiV2.class);
    }
    public PdfsApiV2 pdfsApiV2() {
        return client.create(PdfsApiV2.class);
    }
    public ShopCheckoutsApiV2 shopCheckoutsApiV2() {
        return client.create(ShopCheckoutsApiV2.class);
    }
    public ShopOrdersApiV2 shopOrdersApiV2() {
        return client.create(ShopOrdersApiV2.class);
    }
    public ShopPreviewsApiV2 shopPreviewsApiV2() {
        return client.create(ShopPreviewsApiV2.class);
    }
    public TeamsApiV2 teamsApiV2() {
        return client.create(TeamsApiV2.class);
    }
    public TeamsSubscriptionsNeededApiV2 teamsSubscriptionsNeededApiV2() {
        return client.create(TeamsSubscriptionsNeededApiV2.class);
    }
    public TeamsUserAssessmentsApiV2 teamsUserAssessmentsApiV2() {
        return client.create(TeamsUserAssessmentsApiV2.class);
    }
    public TeamsUsersApiV2 teamsUsersApiV2() {
        return client.create(TeamsUsersApiV2.class);
    }
    public UserAssessmentsApiV2 userAssessmentsApiV2() {
        return client.create(UserAssessmentsApiV2.class);
    }
    public UserAssessmentsAnswersApiV2 userAssessmentsAnswersApiV2() {
        return client.create(UserAssessmentsAnswersApiV2.class);
    }
    public UsersApiV2 usersApiV2() {
        return client.create(UsersApiV2.class);
    }
    public UsersMeApiV2 usersMeApiV2() {
        return client.create(UsersMeApiV2.class);
    }
    public UsersSessionsApiV2 usersSessionsApiV2() {
        return client.create(UsersSessionsApiV2.class);
    }
}
